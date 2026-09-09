#!/usr/bin/env python3
"""Optional real Mesa test. Supply WURM_HOST_GRAPHICS with source-built host JNI/GL4ES.

Uses a private Mesa installation (usr/lib and GLVND JSON under that directory).
No Wurm assets, Android install, or synthetic native library is used.
"""
import os
from pathlib import Path
import subprocess
import time
import struct

root=Path(__file__).resolve().parents[1]
host=Path(os.environ['WURM_HOST_GRAPHICS']).resolve()
assets=root/'app/build/generated/clientGraphics/assets'
env=dict(os.environ, LD_LIBRARY_PATH=str(host/'usr/lib/x86_64-linux-gnu'),
    __EGL_VENDOR_LIBRARY_FILENAMES=str(host/'usr/share/glvnd/egl_vendor.d/50_mesa.json'),
    EGL_PLATFORM='surfaceless', LIBGL_ALWAYS_SOFTWARE='1', LIBGL_ES='2', LIBGL_GL='21',
    LIBGL_GLES='libGLESv2.so.2', LIBGL_EGL='libEGL.so.1', LIBGL_NOPSA='1')
args=['java', f'-Djava.library.path={host}', f'-Dorg.lwjgl.librarypath={host}',
    '-Dorg.lwjgl.opengl.explicitInit=true', '-Dorg.lwjgl.system.bundledLibrary.nameMapper=wurm.graphics.LibraryNames',
    '-Dorg.lwjgl.system.allocator=system', '-XX:-CreateCoredumpOnCrash', f'-XX:ErrorFile={host}/window-hs_err_pid%p.log', f'-Dwurm.graphics.library={host}/libgl4es.so',
    f'-Dwurm.graphics.frame={host}/window.bin', '-cp',
    ':'.join(str(assets/n) for n in ['wurm-window.jar','graphics-probe.jar','pojav-wurm-api.jar']),
    'wurm.graphics.WindowProbe']
log=host/'window-test.log'
with log.open('w') as out:
    child=subprocess.Popen(args,env=env,stdin=subprocess.PIPE,stdout=out,stderr=subprocess.STDOUT,text=True)
    try:
        deadline=time.monotonic()+15
        while '[window] WINDOW_FRAME sequence=1 ' not in log.read_text():
            if child.poll() is not None or time.monotonic()>deadline: raise AssertionError(log.read_text()[-8000:])
            time.sleep(.05)
        data=(host/'window.bin').read_bytes()
        magic,version,width,height,sequence=struct.unpack('>5i',data[:20])
        assert (magic,version,width,height)==(0x57554746,1,640,360) and sequence>0
        pixels=struct.unpack('>230400I',data[20:])
        # Actual fixed-function GL4ES drawing, not an Android placeholder.
        assert pixels[180*640+320] & 0xffffff == 0xff661a
        assert pixels[10*640+10] & 0xffffff == 0x0d2659
        child.stdin.write('KEY 17 1\nBUTTON 0 1\nBUTTON 7 1\nMOVE 24 10\nWHEEL 120\n'); child.stdin.flush()
        time.sleep(.4)
        child.stdin.write('RESET\n');child.stdin.flush();time.sleep(.4)
        child.stdin.write('STOP\n');child.stdin.flush();child.wait(timeout=15)
        assert child.returncode==0, log.read_text()[-8000:]
    finally:
        if child.poll() is None: child.kill();child.wait()
text=log.read_text()
for marker in ['WINDOW_READY','WINDOW_FRAME','LWJGL_KEY code=17 down=true','LWJGL_KEY code=17 down=false',
    'LWJGL_MOUSE button=0 down=true', 'LWJGL_MOUSE button=0 down=false', 'LWJGL_MOUSE button=7 down=true',
    'wheel=120', 'LWJGL_WHEEL_POLL delta=120', 'LWJGL_MOUSE_MOVE', 'WINDOW_CLOSED', 'WINDOW_PROBE_PASS', 'WINDOW_DESTROY_SKIPPED', 'OFFSCREEN_FBO_PASS', 'SHADER_QUERY_PASS']:
    assert marker in text, 'Missing '+marker+'; see '+str(log)
assert not any(x in text for x in ['WINDOW_PROBE_FAIL', 'CLIENT_GL_ERROR', 'FRAME_READBACK_ERROR'])
print('PASS real Display creation, frame readback, keyboard/mouse/wheel queues, RESET and teardown; see '+str(log))
