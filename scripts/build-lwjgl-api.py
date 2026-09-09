#!/usr/bin/env python3
"""Build a pinned Pojav Java API candidate for audit, not an Android native renderer."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PIN = '39272d4d0ca119379024e3ca7207699fd3fce237'
JSR305_SHA256 = '766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7'
MODULES = ('core', 'lwjglx', 'opengl', 'opengles', 'openal', 'glfw', 'egl')


def patch_display(source):
    original = '''    public static void destroy() {
        Window.releaseCallbacks();
        glfwDestroyWindow(Window.handle);

        displayCreated = false;
    }'''
    replacement = '''    public static void destroy() {
        if (Window.handle == MemoryUtil.NULL) {
            displayCreated = false;
            isCreated = false;
            System.out.println("[window] WINDOW_DESTROY_SKIPPED no window; original startup error can be reported");
            return;
        }
        try {
            Window.releaseCallbacks();
        } finally {
            try { glfwDestroyWindow(Window.handle); }
            finally { Window.handle = MemoryUtil.NULL; displayCreated = false; isCreated = false; }
        }
    }'''
    if source.count(original) != 1:
        raise ValueError('Pinned Display.destroy implementation changed')
    return source.replace(original, replacement)


def append_methods(source, methods):
    # Only used against the verified pinned checkout or authored test fixtures.
    position = source.rfind('}')
    if position < 0:
        raise ValueError('Missing class closing brace')
    return source[:position] + '\n' + methods + '\n' + source[position:]


def patch_legacy_queries(source):
    # The pinned LWJGLX convenience methods overwrite size with type, leave the
    # second slot zero, and advance position. LWJGL2 callers expect two outputs.
    for method in ('glGetActiveUniform', 'glGetActiveAttrib'):
        marker = '    public static String ' + method + '(int program, int index, int maxLength,'
        if source.count(marker) != 1:
            raise ValueError('Pinned legacy query changed: ' + method)
        start = source.index(marker)
        end = source.index('\n    }', start) + len('\n    }')
        original = source[start:end]
        if 'sizeType.put(type.get(0));' not in original or 'IntBuffer sizeType)' not in original:
            raise ValueError('Pinned legacy query body changed: ' + method)
        replacement = '''    public static String METHOD(int program, int index, int maxLength, IntBuffer sizeType) {
        if (!sizeType.isDirect() || sizeType.isReadOnly() || sizeType.remaining() < 2)
            throw new IllegalArgumentException("sizeType requires two writable direct integers");
        IntBuffer type = sizeType.duplicate();
        type.position(type.position() + 1);
        return METHOD(program, index, maxLength, sizeType.duplicate(), type);
    }'''.replace('METHOD', method)
        source = source[:start] + replacement + source[end:]
    return source


def patch_capability_checks(source):
    # This fork's ANGLE workaround reports success even when a version is absent
    # or a function lookup failed. Wurm then selects its GL3.3 deferred renderer
    # on GL4ES 2.1. Keep every lookup/cache write; restore truthful return values.
    marker = 'return true; // otherwise the lookup chain will be broken (ANGLE renderer)'
    if source.count(marker) != 3:
        raise ValueError('Pinned ANGLE capability workaround changed')
    for signature, result in (
        ('public static boolean checkFunctions(FunctionProvider provider, PointerBuffer caps, int[] indices, String... functions)', 'available'),
        ('public static boolean checkFunctions(FunctionProvider provider, long[] caps, int[] indices, String... functions)', 'available'),
        ('public static boolean reportMissing(String api, String extension)', 'false'),
    ):
        start = source.index(signature)
        end = source.index('\n    }', start)
        body = source[start:end]
        if body.count(marker) != 1:
            raise ValueError('Pinned capability check changed: ' + signature)
        source = source[:start] + body.replace(marker, 'return ' + result + ';') + source[end:]
    return source


def patch_graphics_trace(source):
    # Only wrap the pinned public GL20 delegates used by the current Wurm core
    # path. Native method names, arguments, results and exceptions remain intact.
    calls = [
        ('source', None, 'GL20C.glShaderSource(shader, string)', 'shader', '0', 1),
        ('compile', None, 'GL20C.glCompileShader(shader)', 'shader', '0', 1),
        ('attach', None, 'GL20C.glAttachShader(program, shader)', 'program', 'shader', 1),
        ('link', None, 'GL20C.glLinkProgram(program)', 'program', '0', 1),
        ('shader-status', 'int', 'GL20C.glGetShaderi(shader, pname)', 'shader', 'pname', 1),
        ('program-status', 'int', 'GL20C.glGetProgrami(program, pname)', 'program', 'pname', 1),
        ('uniform', 'String', 'GL20C.glGetActiveUniform(program, index, maxLength, size, type)', 'program', 'index', 1),
        ('attribute', 'String', 'GL20C.glGetActiveAttrib(program, index, maxLength, size, type)', 'program', 'index', 1),
        ('uniform-location', 'int', 'GL20C.glGetUniformLocation(program, name)', 'program', '0', 2),
        ('attribute-location', 'int', 'GL20C.glGetAttribLocation(program, name)', 'program', '0', 2),
        ('bind-attribute', None, 'GL20C.glBindAttribLocation(program, index, name)', 'program', 'index', 2),
    ]
    for operation, result, call, obj, detail, count in calls:
        original = ('return ' if result else '') + call + ';'
        if source.count(original) != count:
            raise ValueError('Pinned GL20 trace delegate changed: ' + operation)
        replacement = ('wurm.graphics.GraphicsTrace.source(shader, string);\n        ' if operation == 'source' else '')
        replacement += f'long trace = wurm.graphics.GraphicsTrace.begin("{operation}", {obj}, {detail});\n        try {{\n            '
        replacement += (result + ' value = ' if result else '') + call + ';\n            wurm.graphics.GraphicsTrace.end(trace);'
        if result: replacement += '\n            return value;'
        replacement += '''
        } catch (RuntimeException | Error error) {
            wurm.graphics.GraphicsTrace.failed(trace, error);
            throw error;
        }'''
        source = source.replace(original, replacement)
    return source


def build(source, annotations, output, patches=True):
    source, annotations, output = source.resolve(), annotations.resolve(), output.resolve()
    if subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip() != PIN:
        raise ValueError('Unexpected Pojav source revision')
    subprocess.run(['git', 'diff', '--exit-code', PIN, '--', 'modules/lwjgl', 'LICENSE.md'], cwd=source, check=True)
    if hashlib.sha256(annotations.read_bytes()).hexdigest() != JSR305_SHA256:
        raise ValueError('Unexpected JSR305 annotation dependency')
    # Do not consume untracked source files or change the upstream checkout.
    tracked = subprocess.check_output(['git', 'ls-files', 'modules/lwjgl'], cwd=source, text=True).splitlines()
    return compile_verified_source(source, annotations, output, tracked, patches)


def compile_verified_source(source, annotations, output, tracked, patches=True):
    """Build sources whose identity was checked by the caller (Git or pinned archive)."""
    if hashlib.sha256(annotations.read_bytes()).hexdigest() != JSR305_SHA256:
        raise ValueError('Unexpected JSR305 annotation dependency')
    java = [source/p for p in tracked if any(p.startswith(f'modules/lwjgl/{m}/src/{s}/java/') for m in MODULES for s in ('main', 'generated'))
            and p.endswith('.java') and Path(p).name not in ('module-info.java', 'package-info.java', 'GLFWVulkan.java')]
    # A fresh directory prevents old class files masking missing providers in the audit.
    output.mkdir(parents=True, exist_ok=False)
    classes = output/'classes'
    if patches:
        additions = ROOT/'graphics-compat/methods'
        for fragment in sorted(additions.glob('*.java.inc')):
            name = fragment.name.removesuffix('.inc')
            original = source/'modules/lwjgl/opengl/src/generated/java/org/lwjgl/opengl'/name
            if original not in java:
                raise ValueError('Patch target not in pinned source list')
            target = output/'patched'/name
            target.parent.mkdir(exist_ok=True)
            content = original.read_text()
            if name == 'GL20.java': content = patch_graphics_trace(patch_legacy_queries(content))
            target.write_text(append_methods(content, fragment.read_text()))
            java[java.index(original)] = target
        java.extend(sorted((ROOT/'graphics-compat/src').rglob('*.java')))
        # LWJGLX exposes eight mouse buttons but its upstream poll buffer has only
        # three. Preserve wheel deltas for both Mouse.next and Mouse.getDWheel.
        original = source/'modules/lwjgl/lwjglx/src/main/java/org/lwjgl/input/GLFWInputImplementation.java'
        target = output/'patched/GLFWInputImplementation.java'
        content = original.read_text().replace('new byte[3]', 'new byte[8]')
        content = content.replace('public int mouseLastX = 0;', 'private int wheel;\n    public int mouseLastX = 0;')
        content = content.replace('buttons.rewind();', 'coord_buffer.put(2, wheel); wheel = 0;\n        buttons.rewind();')
        content = content.replace('event_buffer.putInt(dz).putLong(nanos);', 'wheel += dz;\n        event_buffer.putInt(dz).putLong(nanos);')
        target.write_text(content)
        java[java.index(original)] = target
        original = source/'modules/lwjgl/lwjglx/src/main/java/org/lwjgl/input/Mouse.java'
        target = output/'patched/Mouse.java'
        content = original.read_text().replace('}catch (Throwable e) {', '''}catch (ClassNotFoundException absent) {
            System.out.println("[window] OPTIONAL_CACIO_MOUSE absent; GLFW input remains active");
        }catch (Throwable e) {''')
        target.write_text(content); java[java.index(original)] = target
        original = source/'modules/lwjgl/lwjglx/src/main/java/org/lwjgl/opengl/Display.java'
        target = output/'patched/Display.java'
        target.write_text(patch_display(original.read_text()))
        java[java.index(original)] = target
        original = source/'modules/lwjgl/core/src/main/java/org/lwjgl/system/Checks.java'
        target = output/'patched/Checks.java'
        target.write_text(patch_capability_checks(original.read_text()))
        java[java.index(original)] = target
    # Compile the legacy MemoryUtil last, as module-by-module upstream builds do:
    # exposing it earlier makes generated wildcard imports ambiguous with system.MemoryUtil.
    legacy = source/'modules/lwjgl/lwjglx/src/main/java/org/lwjgl/MemoryUtil.java'
    java.remove(legacy)
    for name, sources, cp in [('base', java, str(annotations)), ('legacy', [legacy], str(annotations)+':'+str(classes))]:
        args = ['-source', '8', '-target', '8', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes), *map(str, sources)]
        args_file = output/(name+'.args')
        args_file.write_text('\n'.join('"'+s.replace('\\', '\\\\').replace('"', '\\"')+'"' for s in args))
        result = subprocess.run(['java', '-Xmx1024m', 'com.sun.tools.javac.Main', '@'+str(args_file)], capture_output=True, text=True)
        (output/(name+'-compile.log')).write_text(result.stdout+result.stderr)
        if result.returncode:
            raise RuntimeError(f'{name} compilation failed; see {output/(name+"-compile.log")}')
    subprocess.run(['java', str(ROOT/'scripts/ExportAuditPlatform.java'), str(output/'jdk-platform-api.jar')], check=True)
    artifact = output/'pojav-wurm-api.jar'
    with zipfile.ZipFile(artifact, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
        for path in sorted(classes.rglob('*.class')):
            entry = zipfile.ZipInfo(path.relative_to(classes).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            jar.writestr(entry, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        notice = zipfile.ZipInfo('META-INF/LICENSE.lwjgl.txt', date_time=(1980, 1, 1, 0, 0, 0))
        jar.writestr(notice, (source/'LICENSE.md').read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
    metadata = dict(source=f'https://github.com/PojavLauncherTeam/lwjgl3/tree/{PIN}', annotations_sha256=JSR305_SHA256,
                    modules=MODULES, excluded='GLFWVulkan.java (not an OpenGL/LWJGL2 surface provider)',
                    wurm_compatibility=patches, native_libraries=False, java='Java 17 compiler, source/target 8; not a Java 8 runtime qualification',
                    jar_sha256=hashlib.sha256(artifact.read_bytes()).hexdigest())
    (output/'build.json').write_text(json.dumps(metadata, indent=2)+'\n')
    print(json.dumps(metadata))
    return artifact


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--annotations', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True, help='New scratch output directory; must not exist')
    parser.add_argument('--without-wurm-compat', action='store_true')
    args = parser.parse_args()
    build(args.source, args.annotations, args.output, not args.without_wurm_compat)


if __name__ == '__main__':
    main()
