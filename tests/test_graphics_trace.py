"""Bounded diagnostics must preserve real wrapper delegation and never print shader text."""
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('trace_builder', ROOT/'scripts/build-lwjgl-api.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class GraphicsTraceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        # Authored delegate fixtures exercise the production source adapter, not Wurm code.
        methods = [
            ('void', 'glShaderSource', 'int shader, CharSequence string', 'shader, string'),
            ('void', 'glCompileShader', 'int shader', 'shader'),
            ('void', 'glAttachShader', 'int program, int shader', 'program, shader'),
            ('void', 'glLinkProgram', 'int program', 'program'),
            ('int', 'glGetShaderi', 'int shader, int pname', 'shader, pname'),
            ('int', 'glGetProgrami', 'int program, int pname', 'program, pname'),
            ('String', 'glGetActiveUniform', 'int program, int index, int maxLength, int[] size, int[] type', 'program, index, maxLength, size, type'),
            ('String', 'glGetActiveAttrib', 'int program, int index, int maxLength, int[] size, int[] type', 'program, index, maxLength, size, type'),
        ]
        for kind in ('CharSequence', 'java.nio.ByteBuffer'):
            methods += [('int', name, 'int program, '+kind+' name', 'program, name')
                        for name in ('glGetUniformLocation', 'glGetAttribLocation')]
            methods += [('void', 'glBindAttribLocation', 'int program, int index, '+kind+' name', 'program, index, name')]
        cls.original = 'class GL20 {\n' + '\n'.join(
            f' static {result} {name}({params}) {{ '+('return ' if result != 'void' else '')+
            f'GL20C.{name}({args}); }}' for result, name, params, args in methods) + '\n}'
        stubs = []
        for result, name, params, args in methods:
            body = 'calls++;'
            if name == 'glCompileShader': body += ' if(shader==99) throw failure; if(shader==101) Runtime.getRuntime().halt(134);'
            if name == 'glShaderSource': body += ' if(shader!=7 || !string.toString().equals("private shader fixture")) throw new AssertionError();'
            if name == 'glGetActiveUniform': body += ' size[0]=3; type[0]=35675;'
            if result == 'int': body += ' return 73;'
            if result == 'String': body += ' return "fixture";'
            stubs.append(f' static {result} {name}({params}) {{ {body} }}')
        source = builder.patch_graphics_trace(cls.original) + '''
class GL20C {
 static int calls;
 static final RuntimeException failure=new IllegalArgumentException("fixture");
''' + '\n'.join(stubs) + '\n}' + '''
public class TraceFixture {
 public static void main(String[] args) {
  if(args.length>0 && args[0].equals("abort")) { GL20.glCompileShader(101); return; }
  if(args.length>0) {
   for(int i=0;i<5000;i++) wurm.graphics.GraphicsTrace.end(wurm.graphics.GraphicsTrace.begin("bounded",0,0));
   return;
  }
  GL20.glShaderSource(7,"private shader fixture");
  GL20.glCompileShader(7);
  GL20.glAttachShader(8,7); GL20.glLinkProgram(8);
  if(GL20.glGetShaderi(7,1)!=73 || GL20.glGetProgrami(8,1)!=73) throw new AssertionError();
  int[] size={0},type={0};
  if(!GL20.glGetActiveUniform(8,0,128,size,type).equals("fixture") || size[0]!=3 || type[0]!=35675) throw new AssertionError();
  if(!GL20.glGetActiveAttrib(8,0,128,size,type).equals("fixture")) throw new AssertionError();
  java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(4);
  if(GL20.glGetUniformLocation(8,"name")!=73 || GL20.glGetUniformLocation(8,bytes)!=73 ||
     GL20.glGetAttribLocation(8,"name")!=73 || GL20.glGetAttribLocation(8,bytes)!=73) throw new AssertionError();
  GL20.glBindAttribLocation(8,0,"name"); GL20.glBindAttribLocation(8,0,bytes);
  try { GL20.glCompileShader(99); throw new AssertionError(); }
  catch(RuntimeException error) { if(error!=GL20C.failure) throw new AssertionError("exception changed"); }
  if(GL20C.calls!=15) throw new AssertionError("delegate called extra or skipped");
  System.out.println("DELEGATES_PASS");
 }
}
'''
        (cls.home/'TraceFixture.java').write_text(source)
        compiled = subprocess.run(['java', 'com.sun.tools.javac.Main', '-source', '8', '-target', '8',
            '-d', str(cls.home), str(cls.home/'TraceFixture.java'),
            str(ROOT/'graphics-compat/src/wurm/graphics/GraphicsTrace.java')], capture_output=True, text=True)
        if compiled.returncode: raise AssertionError(compiled.stdout+compiled.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_fixture(self, enabled, *args):
        result = subprocess.run(['java', '-Dwurm.graphics.trace='+str(enabled).lower(), '-cp', str(self.home),
            'TraceFixture', *args], capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        return result.stdout

    def test_disabled_preserves_delegation_and_exception_without_trace(self):
        self.assertEqual(self.run_fixture(False), 'DELEGATES_PASS\n')

    def test_enabled_pairs_calls_and_hashes_without_shader_text(self):
        text = self.run_fixture(True)
        self.assertIn('DELEGATES_PASS', text)
        self.assertEqual(text.count('BEGIN seq='), 15)
        self.assertEqual(text.count('END seq='), 14)
        self.assertIn('THREW seq=15 type=java.lang.IllegalArgumentException', text)
        self.assertIn(hashlib.sha256(b'private shader fixture').hexdigest(), text)
        self.assertNotIn('private shader fixture', text)

    def test_limit_stops_trace_and_reports_once(self):
        text = self.run_fixture(True, 'limit')
        self.assertEqual(text.count('BEGIN seq='), 4096)
        self.assertEqual(text.count('END seq='), 4096)
        self.assertEqual(text.count('TRACE_LIMIT'), 1)

    def test_source_drift_rejected(self):
        with self.assertRaises(ValueError):
            builder.patch_graphics_trace(self.original.replace('GL20C.glLinkProgram(program);', 'GL20C.glLinkProgram(0);'))

    def test_abrupt_exit_retains_flushed_unfinished_native_call(self):
        result = subprocess.run(['java', '-Dwurm.graphics.trace=true', '-cp', str(self.home),
            'TraceFixture', 'abort'], capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 134)
        self.assertIn('BEGIN seq=1 op=compile object=101', result.stdout)
        self.assertNotIn('END seq=', result.stdout)
        self.assertNotIn('THREW seq=', result.stdout)


if __name__ == '__main__':
    unittest.main()
