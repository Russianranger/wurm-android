"""Execute pinned legacy AL lifecycle against a deterministic device boundary."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("lwjgl_builder", ROOT/"scripts/build-lwjgl-api.py")
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

BOUNDARY = r'''
class MemoryUtil { static final long NULL=0; }
class BufferUtils { static IntBuffer createIntBuffer(int n) { return IntBuffer.allocate(n); } }
class LWJGLException extends Exception { LWJGLException(String s) { super(s); } }
class ALCdevice { long device; ALCdevice(long d) { device=d; } }
class ALCapabilities {}
class ALCCapabilities {}
class ALC {
    static ALCCapabilities createCapabilities(long d) {
        if (ALC10.fail==4) throw new IllegalStateException("capabilities");
        if(d!=101) throw new AssertionError("not device");
        return new ALCCapabilities();
    }
}
class ALC10 {
    static final int ALC_FREQUENCY=4103, ALC_REFRESH=4104, ALC_SYNC=4105, ALC_TRUE=1, ALC_FALSE=0;
    static int fail, opens, creates, destroys, closes;
    static long current;
    static long alcOpenDevice(String s) { ++opens; return fail==1 ? 0 : 101; }
    static long alcOpenDevice(ByteBuffer b) { return alcOpenDevice((String)null); }
    static long alcCreateContext(long d, IntBuffer b) {
        ++creates;
        if(d!=101) throw new AssertionError("context passed as device");
        return fail==2 ? 0 : 202;
    }
    static boolean alcMakeContextCurrent(long c) {
        if(c!=0 && c!=202) throw new AssertionError("bad context");
        if(c!=0 && fail==3) return false;
        current=c; return true;
    }
    static void alcDestroyContext(long c) {
        if(c!=202 || current!=0) throw new AssertionError("bad context cleanup"); ++destroys;
    }
    static boolean alcCloseDevice(long d) {
        if(d!=101 || current!=0) throw new AssertionError("bad device cleanup"); ++closes; return true;
    }
    static void reset(int f) { fail=f; opens=creates=destroys=closes=0; current=0; }
}
class Probe {
    static void check(boolean b) { if(!b) throw new AssertionError("lifecycle"); }
    public static void main(String[] args) throws Exception {
        for(int i=0; i<3; ++i) {
            ALC10.reset(0);
            if(i==1) AL.create(null,48000,60,false); else AL.create();
            check(AL.isCreated() && AL.alContext==202 && AL.getDevice().device==101);
            AL.create();
            check(ALC10.opens==1 && ALC10.creates==1);
            AL.destroy(); AL.destroy();
            check(!AL.isCreated() && AL.alContext==0 && AL.getDevice()==null);
            check(ALC10.closes==1 && ALC10.destroys==1);
        }
        for(int fail=1; fail<=4; ++fail) {
            ALC10.reset(fail);
            try { AL.create(); throw new AssertionError("failure accepted"); }
            catch(LWJGLException | IllegalStateException expected) {}
            check(!AL.isCreated() && AL.alContext==0 && AL.getDevice()==null);
            check(ALC10.closes==(fail==1 ? 0 : 1));
            check(ALC10.destroys==(fail>=3 ? 1 : 0));
            ALC10.reset(0); AL.create(); AL.destroy();
        }
        ALC10.reset(0); AL.create(null,44100,60,false,false);
        check(ALC10.opens==0 && !AL.isCreated());
        System.out.println("LEGACY_OPENAL_PASS create/destroy/recreate and all partial failures");
    }
}
'''


@unittest.skipUnless(os.environ.get("WURM_LWJGL_ARCHIVE"), "pinned public LWJGL archive required")
class LegacyOpenALTest(unittest.TestCase):
    def test_context_is_not_used_as_a_device_and_cleanup_allows_retry(self):
        archive = Path(os.environ["WURM_LWJGL_ARCHIVE"])
        self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),
                         "598e676c20c89847538a202c42d6765a36bb83da7adc4ca01cffdefdc534e476")
        with tarfile.open(archive) as tar:
            source = tar.extractfile("lwjgl3-"+builder.PIN+"/modules/lwjgl/openal/src/main/java/org/lwjgl/openal/AL.java").read().decode()
        for fixed in (False, True):
            code = builder.patch_legacy_audio(source) if fixed else source
            # Run the actual legacy fields/methods, destroy body and new helper;
            # generated AL3 capabilities/native boundaries are supplied above.
            legacy = code[code.index("// -- Begin LWJGL2 part --"):code.index("// -- End LWJGL2 part")]
            start = code.index("    public static void destroy()")
            end = code.index("\n    }", start)+6
            methods = code[start:end]
            if fixed:
                start = code.index("    private static void createLegacyContext(")
                methods += code[start:code.index("\n    }", start)+6]
            fixture = "import java.nio.*;\nclass AL {\n"+legacy+methods+'''
                static void setCurrentProcess(ALCapabilities c) {}
                static ALCapabilities createCapabilities(ALCCapabilities c) { return new ALCapabilities(); }
            }\n'''+BOUNDARY
            with tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp)/"Probe.java"; path.write_text(fixture)
                result = subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "8", "-d", tmp, str(path)], capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                run = subprocess.run(["java", "-cp", tmp, "Probe"], capture_output=True, text=True)
                if fixed: self.assertEqual(run.returncode, 0, run.stdout+run.stderr)
                else:
                    self.assertNotEqual(run.returncode, 0)
                    self.assertIn("context passed as device", run.stderr)
        with self.assertRaises(ValueError):
            builder.patch_legacy_audio(builder.patch_legacy_audio(source))


if __name__ == "__main__": unittest.main()
