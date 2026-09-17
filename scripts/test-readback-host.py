#!/usr/bin/env python3
"""Exact-pixel GLES3 readback gate through the packaged GL4ES source/patches.
Requires host cc, Mesa EGL/GLES development packages and the pinned GL4ES archive.
No proprietary client, Android device or device-performance claim.
"""
from concurrent.futures import ThreadPoolExecutor
import hashlib,importlib.util,json,os,re,subprocess,tarfile,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
pin=json.loads((ROOT/'graphics-compat/native-sources.json').read_text())['gl4es']
archive=Path(os.environ.get('WURM_GL4ES_ARCHIVE',ROOT/'app/build/graphicsDownloads/gl4es.tar.gz'))
assert hashlib.sha256(archive.read_bytes()).hexdigest()==pin['sha256']
with tempfile.TemporaryDirectory(prefix='wurm-readback-') as td:
 home=Path(td)
 with tarfile.open(archive) as t:t.extractall(home,filter='data')
 source=home/pin['root']
 spec=importlib.util.spec_from_file_location('patch',ROOT/'scripts/patch-gl4es.py');patch=importlib.util.module_from_spec(spec);spec.loader.exec_module(patch)
 native=ROOT/'graphics-compat/native'
 patch.apply(source);patch.apply_draw(source,native/'wurm_draw_trace.h');patch.apply_array_addresses(source);patch.apply_program_cleanup(source);patch.apply_depth_precision(source);patch.apply_error_origin(source,native);patch.apply_mipmap_realization(source,native)
 files=re.findall(r'\bsrc/[A-Za-z0-9_/]+\.c\b',(source/'Android.mk').read_text())
 def build(f):
  output=home/(f.replace('/','_')+'.o')
  r=subprocess.run(['cc','-c','-std=gnu99','-O2','-fPIC','-fvisibility=hidden','-DNOX11','-DEGL_NO_X11','-DNO_GBM','-DNO_INIT_CONSTRUCTOR','-DDEFAULT_ES=2','-I'+str(source/'include'),str(source/f),'-o',str(output)],capture_output=True,text=True)
  if r.returncode:raise RuntimeError(r.stderr)
  return str(output)
 with ThreadPoolExecutor(max_workers=4) as executor:objects=list(executor.map(build,files))
 library=home/'libgl4es.so'
 subprocess.run(['cc','-shared',*objects,'-ldl','-lm','-o',str(library)],check=True)
 host=Path(os.environ.get('WURM_HOST_GRAPHICS','/usr'))
 runner=home/'readback'
 subprocess.run(['cc','-std=c11','-O2','-Wall','-Wextra','-Werror','-DEGL_NO_X11','-I'+str(host/'include'),'-I'+str(source/'include'),'-I'+str(native),str(ROOT/'tests/native/pipelined_readback_mesa.c'),'-l:libEGL.so.1','-ldl','-o',str(runner)],check=True)
 env=dict(os.environ,EGL_PLATFORM='surfaceless',LIBGL_ALWAYS_SOFTWARE='1',LIBGL_ES='2',LIBGL_GL='21',LIBGL_GLES=str(host/'lib/x86_64-linux-gnu/libGLESv2.so.2'),LIBGL_EGL='libEGL.so.1',LIBGL_NOPSA='1')
 r=subprocess.run([str(runner),str(library)],env=env,capture_output=True,text=True,timeout=60)
 print(r.stdout)
 if r.returncode:raise RuntimeError(r.stderr)
 assert 'READBACK_REAL_GL4ES_MESA_PASS' in r.stdout
