"""Authored class-file and wrapper fixtures; no proprietary game or native graphics input."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    sys.modules[name] = value
    spec.loader.exec_module(value)
    return value


api = module('lwjgl_audit', ROOT/'scripts/audit-lwjgl-api.py')
builder = module('lwjgl_builder', ROOT/'scripts/build-lwjgl-api.py')


@unittest.skipUnless(shutil.which('java'), 'Java 17 required')
class LwjglApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        cls.full = cls.compile_jar({
            'org/lwjgl/Parent.java': 'package org.lwjgl; public class Parent { public static int value; public Parent() {} public Parent(int n) {} public static void inherited(int n) {} }',
            'org/lwjgl/Surface.java': 'package org.lwjgl; public class Surface extends Parent { static { if(true) throw new Error("MUST_NOT_RUN"); } public Surface(int n) {} public static void draw(int n) {} public static void draw(String n) {} }',
            'com/wurmonline/client/Use.java': '''package com.wurmonline.client; public class Use {
             public void use() { new org.lwjgl.Surface(3); org.lwjgl.Surface.draw(1); org.lwjgl.Surface.draw("text"); org.lwjgl.Surface.inherited(2); org.lwjgl.Surface.value=5; }
            }'''
        })
        cls.platform = cls.home/'jdk.jar'
        subprocess.run(['java', str(ROOT/'scripts/ExportAuditPlatform.java'), str(cls.platform)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    @classmethod
    def compile_jar(cls, sources):
        folder = Path(tempfile.mkdtemp(dir=cls.home))
        for name, text in sources.items():
            path = folder/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(folder), *map(str, (folder/n for n in sources))], check=True)
        jar = folder/'fixture.jar'
        with zipfile.ZipFile(jar, 'w') as z:
            for p in folder.rglob('*.class'):
                z.write(p, p.relative_to(folder).as_posix())
        return jar

    def test_resolves_overloads_inherited_members_without_class_initialization(self):
        result = api.audit(self.full, [self.full], platforms=[self.platform])
        self.assertTrue(result['candidate_complete'], result)
        self.assertEqual(result['required_members'], 5)

    def test_missing_adapter_member_is_not_hidden_by_original_bundled_lwjgl(self):
        partial = self.compile_jar({
            'org/lwjgl/Parent.java': 'package org.lwjgl; public class Parent { public static int value; public Parent() {} public Parent(int n) {} public static void inherited(int n) {} }',
            'org/lwjgl/Surface.java': 'package org.lwjgl; public class Surface extends Parent { public Surface(int n) {} public static void draw(String n) {} }'})
        result = api.audit(self.full, [partial], platforms=[self.platform])
        self.assertFalse(result['candidate_complete'])
        self.assertEqual([(x['name'], x['descriptor']) for x in result['missing_members']], [('draw', '(I)V')])

    def test_missing_classes_are_not_satisfied_by_game_classpath(self):
        candidate = self.compile_jar({'org/lwjgl/Different.java': 'package org.lwjgl; public class Different {}'})
        result = api.audit(self.full, [candidate])
        self.assertIn('org/lwjgl/Surface', result['missing_classes'])
        self.assertFalse(result['candidate_complete'])

    def test_missing_ancestor_stays_unresolved_and_constructors_are_not_inherited(self):
        parent = api.ClassInfo('org/lwjgl/Parent', (), {('method', '<init>', '(I)V'): 1}, set(), set())
        child = api.ClassInfo('org/lwjgl/Child', ('org/lwjgl/Parent',), {}, set(), set())
        ref = api.Reference('method', child.name, '<init>', '(I)V')
        self.assertEqual(api.lookup(ref, {parent.name: parent, child.name: child}), 'missing')
        child.parents = ('unknown/Parent',)
        self.assertEqual(api.lookup(api.Reference('method', child.name, 'method', '()V'), {child.name: child}), 'unknown')

    def test_duplicate_providers_and_empty_scope_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate adapter'):
            api.audit(self.full, [self.full, self.full])
        with self.assertRaisesRegex(ValueError, 'No caller'):
            api.audit(self.full, [self.full], caller_prefix='missing/')

    def test_corrupt_and_oversized_class_inputs_are_rejected(self):
        for value in [b'bad!', b'\xca\xfe\xba\xbe', bytes(api.LIMIT + 1)]:
            with self.assertRaises(ValueError):
                api.parse_class(value)

    def test_legacy_shader_wrappers_preserve_output_slots_and_delegate_arguments(self):
        methods = ROOT/'graphics-compat/methods'
        wrappers = {}
        for cls, method in [('GL20', 'glGetActiveAttrib'), ('ARBVertexShader', 'glGetActiveAttribARB'), ('ARBShaderObjects', 'glGetActiveUniformARB')]:
            fixture = f'''package org.lwjgl.opengl;
public class {cls} {{
 public static String {method}(int program, int index, int length, java.nio.IntBuffer size, java.nio.IntBuffer type) {{
  if(program!=7 || index!=8 || length!=99) throw new AssertionError("arguments");
  size.put(size.position(), 3); type.put(type.position(), 0x1406); return "fixture-name";
 }}
}}'''
            wrappers[f'org/lwjgl/opengl/{cls}.java'] = builder.append_methods(fixture, (methods/f'{cls}.java.inc').read_text())
        wrappers['org/lwjgl/system/MemoryStack.java'] = '''package org.lwjgl.system;
public class MemoryStack implements AutoCloseable {
 public static MemoryStack stackPush() { return new MemoryStack(); }
 public java.nio.IntBuffer mallocInt(int count) { return java.nio.ByteBuffer.allocateDirect(count*4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer(); }
 public void close() {}
}'''
        wrappers['org/lwjgl/opengl/ARBVertexProgram.java'] = '''package org.lwjgl.opengl;
public class ARBVertexProgram { public static int glGetProgramiARB(int target, int parameter) { return target ^ parameter; } }'''
        for p in (ROOT/'graphics-compat/src').rglob('*.java'):
            wrappers[p.relative_to(ROOT/'graphics-compat/src').as_posix()] = p.read_text()
        wrappers['Check.java'] = '''import org.lwjgl.opengl.*; import java.nio.*;
public class Check { public static void main(String[] args) {
 IntBuffer data=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asIntBuffer(); data.position(2); data.limit(4);
 if(!ARBShaderObjects.glGetActiveUniformARB(7,8,99,data).equals("fixture-name")) throw new AssertionError("name");
 if(data.position()!=2 || data.limit()!=4 || data.get(2)!=3 || data.get(3)!=0x1406 || data.get(0)!=0) throw new AssertionError("slots/position");
 if(!GL20.glGetActiveAttrib(7,8,99).equals("fixture-name") || !ARBVertexShader.glGetActiveAttribARB(7,8,99).equals("fixture-name")) throw new AssertionError("attribute");
 if(ARBProgram.glGetProgramiARB(7,8)!=(7^8) || SGISGenerateMipmap.GL_GENERATE_MIPMAP_SGIS!=0x8191 || SGISGenerateMipmap.GL_GENERATE_MIPMAP_HINT_SGIS!=0x8192) throw new AssertionError("legacy aliases");
 for(IntBuffer bad:new IntBuffer[]{IntBuffer.allocate(2),data.asReadOnlyBuffer(),data.duplicate().position(3)}) {
  try { ARBShaderObjects.glGetActiveUniformARB(7,8,99,bad); throw new AssertionError("accepted invalid buffer"); }
  catch(IllegalArgumentException expected) {}
 }
 System.out.println("WRAPPER_CONTRACT_OK");
} }'''
        jar = self.compile_jar(wrappers)
        result = subprocess.run(['java', '-cp', str(jar), 'Check'], capture_output=True, text=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn('WRAPPER_CONTRACT_OK', result.stdout)

    def test_pinned_core_queries_write_two_slots_without_moving_position(self):
        methods = ''
        for name in ('glGetActiveUniform', 'glGetActiveAttrib'):
            methods += f'''    public static String {name}(int program, int index, int maxLength,
                                            IntBuffer sizeType) {{
        IntBuffer type = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        String s = {name}(program, index, maxLength, sizeType, type);
        sizeType.put(type.get(0));
        return s;
    }}
    public static String {name}(int p, int i, int length, IntBuffer size, IntBuffer type) {{
        if(p!=7 || i!=8 || length!=99) throw new AssertionError("arguments");
        size.put(size.position(),3); type.put(type.position(),0x1406); return "native-output";
    }}
'''
        source = 'package org.lwjgl.opengl; import java.nio.*; public class GL20 {\n'+methods+'}'
        patched = builder.patch_legacy_queries(source)
        with self.assertRaises(ValueError): builder.patch_legacy_queries(patched)
        check = '''import java.nio.*; import org.lwjgl.opengl.GL20;
public class CoreCheck { public static void main(String[] args) {
 for(int position:new int[]{0,2}) for(boolean uniform:new boolean[]{false,true}) {
  IntBuffer b=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asIntBuffer();
  b.position(position);b.limit(position+2);
  String name=uniform?GL20.glGetActiveUniform(7,8,99,b):GL20.glGetActiveAttrib(7,8,99,b);
  if(!name.equals("native-output") || b.position()!=position || b.limit()!=position+2 || b.get(position)!=3 || b.get(position+1)!=0x1406) throw new AssertionError("size/type/position");
 }
 for(IntBuffer b:new IntBuffer[]{IntBuffer.allocate(2),ByteBuffer.allocateDirect(4).asIntBuffer(),ByteBuffer.allocateDirect(8).asIntBuffer().asReadOnlyBuffer()}) {
  try{GL20.glGetActiveUniform(7,8,99,b);throw new AssertionError("bad output accepted");}catch(IllegalArgumentException expected){}
  try{GL20.glGetActiveAttrib(7,8,99,b);throw new AssertionError("bad output accepted");}catch(IllegalArgumentException expected){}
 }
 System.out.println("CORE_QUERY_CONTRACT_PASS");
} }'''
        jar = self.compile_jar({'org/lwjgl/opengl/GL20.java':patched,'CoreCheck.java':check})
        result = subprocess.run(['java','-cp',str(jar),'CoreCheck'],capture_output=True,text=True,timeout=10)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('CORE_QUERY_CONTRACT_PASS',result.stdout)


if __name__ == '__main__':
    unittest.main()
