"""Authored resource fixtures; the real user shader bytes are never in public tests."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ClientShaderResourcesTest(unittest.TestCase):
    def test_exact_rules_preserve_other_bytes_and_reject_unknown_resources(self):
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder)
            source = work/'ShaderResourceFixture.java'
            source.write_text('''package client;
import java.nio.charset.StandardCharsets;
public class ShaderResourceFixture {
 public static void main(String[] args) throws Exception {
  String vertex="#version 330\\r\\nlayout (location = 0) in vec3 Position;\\r\\n// fixture attribute and matrix math remain untouched\\r\\n";
  String fragment="#version 330\\r\\nlayout (location = 0) out vec4 diffuseOut;\\r\\n"+"vec4 test=texture(s,uv);\\r\\n".repeat(7)+"// preserve user content\\r\\n";
  for(boolean v:new boolean[]{true,false}) {
   byte[] original=(v?vertex:fragment).getBytes(StandardCharsets.UTF_8);
   byte[] changed=ClientShaderResources.convert(original,v,false);
   if(!java.util.Arrays.equals(original,ClientShaderResources.convert(changed,v,true))) throw new AssertionError("round trip changed bytes");
   String text=new String(changed,StandardCharsets.UTF_8);
   if(!text.startsWith("#version 120\\r\\n") || text.contains("layout (") || text.contains("texture(")) throw new AssertionError("old syntax retained");
   if(v && !text.contains("attribute vec3 Position;")) throw new AssertionError("attribute");
   if(!v && !text.contains("#define diffuseOut gl_FragColor")) throw new AssertionError("output");
   String name=ClientShaderResources.ORIGINALS.keySet().stream().filter(n->n.endsWith(v?"vertex.shader":"fragment.shader")).findFirst().orElseThrow();
   try{ClientShaderResources.prepare(name,original);throw new AssertionError("uninspected source accepted");}catch(java.io.IOException expected){}
   try{ClientShaderResources.verify(name,changed);throw new AssertionError("tampered output accepted");}catch(java.io.IOException expected){}
  }
  for(String invalid:new String[]{fragment.replace("#version 330","#version 450"),fragment+"texture(s,uv);"}) {
   try{ClientShaderResources.convert(invalid.getBytes(StandardCharsets.UTF_8),false,false);throw new AssertionError("wrong rule count accepted");}catch(java.io.IOException expected){}
  }
  System.out.println("SHADER_RESOURCE_CONTRACT_PASS exactRules=true hashes=enforced reverse=byte-exact");
 }
}''')
            subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(work),
                *map(str,(ROOT/'runtime-probe/src/client').glob('*.java')),str(source)],check=True)
            result = subprocess.run(['java','-cp',str(work),'client.ShaderResourceFixture'],capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('SHADER_RESOURCE_CONTRACT_PASS',result.stdout)
