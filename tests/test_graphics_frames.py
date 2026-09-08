"""Executable JVM frame producer and library-name boundary tests, no game/GPU input."""
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which('java'), 'Java 17 required')
class GraphicsFramesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        source = cls.home/'Check.java'
        source.write_text('''import java.nio.*; import java.nio.file.*; import wurm.graphics.*;
public class Check {
 public static void main(String[] args) throws Exception {
  Path output=Path.of(args[0]); int w=16,h=16;
  ByteBuffer data=ByteBuffer.allocateDirect(w*h*4+8); data.position(8);
  for(int y=0;y<h;y++) for(int x=0;x<w;x++) data.put((byte)(y<8?255:0)).put((byte)0).put((byte)(y<8?0:255)).put((byte)255);
  data.flip(); data.position(8);
  if(args[1].equals("valid")) {
   FrameFile.write(output,w,h,1,data); FrameFile.write(output,w,h,2,data);
   if(data.position()!=8 || data.limit()!=1032) throw new AssertionError("buffer consumed");
  } else if(args[1].equals("invalid")) {
   Files.writeString(output,"previous");
   for(int[] bounds:new int[][]{{0,16,1},{16,1025,1},{16,16,0},{16,17,1}}) {
    try { FrameFile.write(output,bounds[0],bounds[1],bounds[2],data); throw new AssertionError("accepted invalid"); }
    catch(IllegalArgumentException expected) {}
   }
   if(!Files.readString(output).equals("previous")) throw new AssertionError("old frame lost");
  } else {
   LibraryNames names=new LibraryNames();
   if(!names.apply("lwjgl").equals("wurm_lwjgl3") || !names.apply("lwjgl_opengl").equals("wurm_lwjgl3_opengl") || !names.apply("other").equals("other")) throw new AssertionError("name isolation");
  }
 }
}''')
        java = ROOT/'graphics-compat/probe/wurm/graphics'
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.home),str(source),
                        str(java/'FrameFile.java'),str(java/'LibraryNames.java')],check=True)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def run_case(self, name):
        output=self.home/(name+'.bin')
        subprocess.run(['java','-cp',str(self.home),'Check',str(output),name],check=True,timeout=15)
        self.assertFalse(output.with_suffix('.bin.pending').exists())
        return output

    def test_rgba_is_flipped_to_argb_and_atomically_replaced_without_consuming_input(self):
        data=self.run_case('valid').read_bytes()
        self.assertEqual(struct.unpack('>5i',data[:20]),(0x57554746,1,16,16,2))
        self.assertEqual(len(data),20+16*16*4)
        pixels=struct.unpack('>256I',data[20:])
        self.assertTrue(all(p==0xff0000ff for p in pixels[:128]))
        self.assertTrue(all(p==0xffff0000 for p in pixels[128:]))

    def test_invalid_dimensions_sequence_and_buffer_do_not_replace_previous_frame(self):
        self.assertEqual(self.run_case('invalid').read_text(),'previous')

    def test_supported_library_mapper_keeps_new_natives_out_of_legacy_lwjgl_names(self):
        self.run_case('names')


if __name__ == '__main__': unittest.main()
