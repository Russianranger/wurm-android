#!/usr/bin/env python3
"""Build public ARM64 LWJGL/GL4ES and the JVM graphics test. No game input."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(args, log, cwd=None, env=None):
    with log.open('w') as output:
        result = subprocess.run(list(map(str, args)), cwd=cwd, env=env, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        print(log.read_text()[-8000:])
        raise RuntimeError(f'Command failed ({result.returncode}); see {log}')


def fetch(cache, name, pin):
    path = cache/(name + ('.jar' if name == 'jsr305' else '.tar.gz'))
    if not path.is_file() or sha(path) != pin['sha256']:
        partial = path.with_suffix('.partial')
        subprocess.run(['curl', '-fsSL', '--retry', '3', '--max-time', '300', pin['url'], '-o', str(partial)], check=True)
        if sha(partial) != pin['sha256']:
            raise ValueError(f'Graphics dependency checksum mismatch: {name}')
        partial.replace(path)
    return path


def main():
    ndk = Path(os.environ['ANDROID_NDK_HOME']).resolve()
    if '26.1.10909125' not in (ndk/'source.properties').read_text():
        raise ValueError('Use Android NDK 26.1.10909125')
    cc = ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang'
    readelf = cc.parent/'llvm-readelf'
    sanitizer = ['-fsanitize=address', '-fno-omit-frame-pointer', '-g']
    pins = json.loads((ROOT/'graphics-compat/native-sources.json').read_text())
    cache = ROOT/'app/build/graphicsDownloads'; cache.mkdir(parents=True, exist_ok=True)
    archives = {name: fetch(cache, name, pin) for name, pin in pins.items()}
    output = ROOT/'app/build/generated/clientGraphics'
    if output.exists(): shutil.rmtree(output)
    assets = output/'assets'; assets.mkdir(parents=True)
    native = output/'jniLibs/arm64-v8a'; native.mkdir(parents=True)
    work = output/'work'; work.mkdir()
    asan = list((cc.parent.parent/'lib/clang').glob('*/lib/linux/libclang_rt.asan-aarch64-android.so'))
    if len(asan) != 1: raise ValueError('Expected one NDK ARM64 ASan runtime')
    shutil.copyfile(cc.parent.parent/'sysroot/usr/lib/aarch64-linux-android/libc++_shared.so', native/'libc++_shared.so')
    sources = {}
    for name in ('lwjgl', 'gl4es', 'libffi', 'pojav', 'openal', 'compiler-rt', 'llvm-cmake', 'llvm'):
        with tarfile.open(archives[name]) as archive:
            members = (m for m in archive if '/cmake/' in m.name or m.name.endswith('/LICENSE.TXT')) if name == 'llvm' else None
            archive.extractall(work, members=members, filter='data')
        sources[name] = work/pins[name]['root']
    spec = importlib.util.spec_from_file_location('asan_builder', ROOT/'scripts/build-asan-runtime.py')
    asan_builder = importlib.util.module_from_spec(spec); spec.loader.exec_module(asan_builder)
    checked_asan = asan_builder.build(ndk, sources['compiler-rt'], sources['llvm-cmake'], sources['llvm'], work, run)
    shutil.copyfile(checked_asan, native/asan[0].name)
    lwjgl, gl4es, ffi = (sources[n] for n in ('lwjgl', 'gl4es', 'libffi'))
    spec = importlib.util.spec_from_file_location('gl4es_patch', ROOT/'scripts/patch-gl4es.py')
    gl4es_patch = importlib.util.module_from_spec(spec); spec.loader.exec_module(gl4es_patch)
    gl4es_patch.apply(gl4es)
    gl4es_patch.apply_draw(gl4es, ROOT/'graphics-compat/native/wurm_draw_trace.h')
    gl4es_patch.apply_array_addresses(gl4es)
    gl4es_patch.apply_program_cleanup(gl4es)
    spec = importlib.util.spec_from_file_location('lwjgl_builder', ROOT/'scripts/build-lwjgl-api.py')
    api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)
    candidate = api.compile_verified_source(lwjgl, archives['jsr305'], work/'java-api',
        [p.relative_to(lwjgl).as_posix() for p in (lwjgl/'modules/lwjgl').rglob('*') if p.is_file()])
    shutil.copyfile(candidate, assets/'pojav-wurm-api.jar')
    ffi_build = work/'ffi-build'; ffi_build.mkdir()
    env = dict(os.environ, CC=str(cc), CXX=str(cc)+'++', AR=str(cc.parent/'llvm-ar'),
               RANLIB=str(cc.parent/'llvm-ranlib'), CFLAGS='-O2 -fPIC '+ ' '.join(sanitizer), LDFLAGS='-fsanitize=address')
    run(['bash', ffi/'configure', '--host=aarch64-linux-android', '--disable-shared', '--enable-static', '--disable-docs'], work/'ffi-configure.log', ffi_build, env)
    run(['make', '-j4'], work/'ffi-build.log', ffi_build, env)
    ffi_lib = next(ffi_build.rglob('libffi.a'))
    core = lwjgl/'modules/lwjgl/core/src/main/c'
    generated = lwjgl/'modules/lwjgl/core/src/generated/c'
    # These are the same core source groups used by upstream config/linux/build.xml.
    core_sources = sorted(list(core.glob('*.c')) + list(generated.glob('*.c')) +
                          list((generated/'linux').glob('*.c')) + list((core/'linux/liburing').glob('*.c')))
    includes = [core, core/'linux', core/'libffi', core/'libffi/aarch64', core/'linux/liburing', core/'linux/liburing/include']
    flags = ['-std=gnu11', '-O2', '-fPIC', '-pthread', '-DNDEBUG', '-DLWJGL_LINUX', '-DLWJGL_arm64',
             '-D_GNU_SOURCE', '-D_FILE_OFFSET_BITS=64', '-DCONFIG_HAVE_MEMFD_CREATE']

    def library(name, files, options, libraries):
        folder = work/name; folder.mkdir()
        def compile_one(pair):
            index, source = pair
            obj = folder/f'{index}.o'
            run([cc, *options, *sanitizer, '-c', source, '-o', obj], folder/f'{index}.log')
            return obj
        with ThreadPoolExecutor(max_workers=4) as pool:
            objects = list(pool.map(compile_one, enumerate(files)))
        target = native/f'lib{name}.so'
        run([cc, '-shared', *sanitizer, '-Wl,--build-id=sha1', '-Wl,--no-undefined', '-Wl,-z,max-page-size=16384', f'-Wl,-soname,lib{name}.so',
             *objects, *libraries, '-o', target], folder/'link.log')
        print(f'Built {target.name}', flush=True)

    library('wurm_lwjgl3', core_sources, flags + ['-I'+str(p) for p in includes], [ffi_lib, '-ldl', '-lm'])
    gl = lwjgl/'modules/lwjgl/opengl/src'
    gl_sources = sorted(p for p in (gl/'generated/c').glob('*.c') if p.name != 'org_lwjgl_opengl_WGL.c')
    library('wurm_lwjgl3_opengl', gl_sources, flags + ['-I'+str(p) for p in [core, core/'linux', gl/'main/c']], ['-ldl', '-lm'])
    # Compile the pinned source list with the checked shader correction and draw breadcrumb.
    gl4es_sources = [gl4es/p for p in re.findall(r'\bsrc/[A-Za-z0-9_/]+\.c\b', (gl4es/'Android.mk').read_text())]
    if len(gl4es_sources) < 60: raise ValueError('Unexpected GL4ES Android source list')
    library('gl4es', gl4es_sources,
        ['-std=gnu99', '-O2', '-fPIC', '-fvisibility=hidden', '-DANDROID', '-DNOX11', '-DNO_GBM',
         '-DNO_INIT_CONSTRUCTOR', '-DDEFAULT_ES=2', '-I'+str(gl4es/'include'), '-Wno-incompatible-function-pointer-types'],
        ['-ldl', '-lm', '-llog'])
    library('wurm_graphics', [ROOT/'graphics-compat/native/egl_probe.c'],
            ['-std=c11', '-O2', '-fPIC', '-Wall', '-Wextra', '-Werror'], ['-lEGL', '-lGLESv2', '-ldl'])
    audio_build = work/'audio-build'
    run(['cmake', '-S', ROOT/'graphics-compat/audio', '-B', audio_build,
         '-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),
         '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-33', '-DANDROID_STL=c++_shared',
         '-DCMAKE_C_FLAGS='+ ' '.join(sanitizer), '-DCMAKE_CXX_FLAGS='+ ' '.join(sanitizer),
         '-DCMAKE_SHARED_LINKER_FLAGS=-fsanitize=address -Wl,--build-id=sha1',
         '-DCMAKE_BUILD_TYPE=Release', '-DWURM_OPENAL_SOURCE='+str(sources['openal'])], work/'audio-configure.log')
    run(['cmake', '--build', audio_build, '--target', 'OpenAL', '-j4'], work/'audio-build.log')
    shutil.copyfile(audio_build/'openal/libwurm_openal.so', native/'libwurm_openal.so')
    print('Built libwurm_openal.so with required Android OpenSL ES backend', flush=True)
    helper = work/'graphics-classes'; helper.mkdir()
    run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-cp', candidate, '-d', helper,
         *sorted((ROOT/'graphics-compat/probe').rglob('*.java'))], work/'probe-compile.log')
    with zipfile.ZipFile(assets/'graphics-probe.jar', 'w', zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(helper.rglob('*.class')):
            entry = zipfile.ZipInfo(path.relative_to(helper).as_posix(), (1980, 1, 1, 0, 0, 0))
            jar.writestr(entry, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
    spec = importlib.util.spec_from_file_location('window_builder', ROOT/'scripts/build-window-api.py')
    window = importlib.util.module_from_spec(spec); spec.loader.exec_module(window)
    window_jar = window.build(sources['pojav'], candidate, assets/'graphics-probe.jar', work/'window-api', archives['jsr305'])
    shutil.copyfile(window_jar, assets/'wurm-window.jar')
    notices = [('LWJGL BSD notice', lwjgl/'LICENSE.md'), ('LWJGL dyncall notice', core/'org_lwjgl_system_SharedLibraryUtil.c'),
               ('LWJGL bundled liburing notice', lwjgl/'modules/lwjgl/core/liburing_license.txt'), ('GL4ES MIT notice (custom shader global-scope correction; bounded native draw breadcrumb)', gl4es/'LICENSE'),
               ('libffi MIT notice', ffi/'LICENSE'), ('Pojav Java GLFW LGPLv3 notice', sources['pojav']/'LICENSE'),
               ('OpenAL Soft LGPL notice', sources['openal']/'COPYING'),
               ('Android utility Apache-2.0 notice', sources['pojav']/'jre_lwjgl3glfw/src/main/java/android/util/ArrayMap.java'),
               ('GPLv3 incorporated by LGPLv3', ROOT/'graphics-compat/licenses/GPL-3.0.txt'),
               ('Apache-2.0 license', ROOT/'graphics-compat/licenses/Apache-2.0.txt'), ('JSR305 annotation notice (build only)', None)]
    # Include notices for source-built ASan and the packaged NDK C++ runtime.
    notices.append(('LLVM compiler-rt Apache-2.0 with LLVM exceptions; prctl PAC correction', sources['compiler-rt']/'LICENSE.TXT'))
    ndk_notices = sorted(ndk.glob("NOTICE*"))
    if not ndk_notices: raise ValueError("Android NDK runtime notices missing")
    notices.extend(("Android NDK runtime notice: "+p.name, p) for p in ndk_notices if p.is_file())
    notice_text = []
    for title, path in notices:
        if path is None: continue
        notice_text.append(title+'\n'+path.read_text())
    (assets/'graphics-NOTICES.txt').write_text('\n\n'.join(notice_text))
    system = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libEGL.so', 'libGLESv2.so', 'libOpenSLES.so'}
    for path in native.glob('*.so'):
        contents = path.read_bytes()
        if contents[:6] != b'\x7fELF\x02\x01' or int.from_bytes(contents[18:20], 'little') != 183:
            raise ValueError('Not ARM64 ELF: '+path.name)
        if path.name.startswith(('libwurm_', 'libgl4es')):
            assert b'__asan_init' in contents, 'Missing ASan instrumentation: '+path.name
        dynamic = subprocess.check_output([str(readelf), '-d', str(path)], text=True)
        for dependency in re.findall(r'\(NEEDED\).*?\[(.*?)\]', dynamic):
            if dependency not in system and not (native/dependency).is_file():
                raise ValueError(f'Missing native dependency {dependency}: {path.name}')
    manifest = dict(id='wurm-graphics-2', backend='LWJGL/Pojav Java GLFW + GL4ES, owned EGL window/readback diagnostic',
                    ndk='26.1.10909125', abi='arm64-v8a', sources=pins, gl4esPatches=['custom-fragment-global-scope', 'bounded-native-draw-breadcrumb', 'internal-client-pointer-addresses', 'vao-buffer-offset-addresses', 'program-cache-cleanup'],
                    nativeHeapDiagnostic='ASan / isolated startup and client graphics stages / LLVM 17.0.2 native ELF TLS with prctl PAC and trampoline BTI fixes',
                    asanPatches=[asan_builder.PATCH, asan_builder.BTI_PATCH],
                    asanRuntimeFlags=asan_builder.RUNTIME_FLAGS,
                    audioBackend='OpenAL Soft 1.23.1 / Android OpenSL ES',
                    lwjglPatches=['legacy-openal-context-lifecycle'],
                    nativeSha256={p.name: sha(p) for p in sorted(native.glob('*.so'))},
                    assetsSha256={p.name: sha(p) for p in sorted(assets.iterdir())})
    (assets/'client-graphics.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print('Graphics runtime built and dependency closure verified. Device rendering is not yet qualified.')


if __name__ == '__main__': main()
