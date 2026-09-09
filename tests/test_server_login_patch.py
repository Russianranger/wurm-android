"""Java 17 encoder ABI, credential-format preservation and optional private login bytecode checks."""
from pathlib import Path
import base64
import hashlib
import os
import struct
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SERVER = os.environ.get('WURM_TEST_SERVER_JAR')
ENTRY = 'com/wurmonline/server/LoginHandler.class'


def encrypt_only_class(data):
    """Private test only: retain the supplied encrypt method and pool, remove game dependencies.

    No proprietary bytes are stored in this repository. The method's Code,
    exception table and stack maps remain byte-identical to the supplied input.
    Production overlays always contain the complete original class.
    """
    pos, utf, index = 10, {}, 1
    count = struct.unpack_from('>H', data, 8)[0]
    while index < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, pos)[0]; pos += 2
            utf[index] = data[pos:pos+size]; pos += size
        else:
            pos += {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
            if tag in (5,6): index += 1
        index += 1
    header = data[:pos+6]  # constant pool, flags, this class, superclass
    pos += 6
    interfaces = struct.unpack_from('>H', data, pos)[0]; pos += 2+interfaces*2

    def member():
        nonlocal pos
        start = pos
        name = struct.unpack_from('>H', data, pos+2)[0]
        attrs = struct.unpack_from('>H', data, pos+6)[0]; pos += 8
        for _ in range(attrs):
            size = struct.unpack_from('>I', data, pos+2)[0]; pos += 6+size
        return utf[name], data[start:pos]

    fields = struct.unpack_from('>H', data, pos)[0]; pos += 2
    for _ in range(fields): member()
    methods = struct.unpack_from('>H', data, pos)[0]; pos += 2
    selected = [body for name, body in (member() for _ in range(methods)) if name == b'encrypt']
    assert len(selected) == 1
    return header + struct.pack('>HHH', 0, 0, 1) + selected[0] + b'\0\0'


class ServerLoginPatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='wurm-login-patch-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        sources = {
            'server/LoginFixture.java': '''package server;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
public class LoginFixture {
 static class Loader extends ClassLoader { Class<?> define(byte[] b) { return defineClass(null,b,0,b.length); } }
 public static void main(String[] args) throws Exception {
  if(args[0].equals("prepare")){ServerLoginPatch.prepare(Path.of(args[1]),Path.of(args[2]));return;}
  if(args[0].equals("verify")){ServerLoginPatch.verifySelected();return;}
  if(args[0].equals("encode")) {
   for(String value:args[1].split(",",-1)) {
    String result=new LegacyBase64Encoder().encode(Base64.getDecoder().decode(value));
    System.out.println("RESULT="+Base64.getEncoder().encodeToString(result.getBytes(StandardCharsets.US_ASCII)));
   }
   return;
  }
  if(args[0].equals("contract")) {
   byte[] modern=Files.readAllBytes(Path.of(args[1]));
   byte[] legacy=ServerLoginPatch.redirect(modern,ServerSqlitePatch.sha(modern),true);
   byte[] fixed=ServerLoginPatch.redirect(legacy,ServerSqlitePatch.sha(legacy),false);
   if(!Arrays.equals(modern,fixed))throw new AssertionError("changed unrelated bytes");
   Class<?> old=new Loader().define(legacy),updated=new Loader().define(fixed);
   try{old.getMethod("encrypt",String.class).invoke(null,"abc");throw new AssertionError("legacy ABI unexpectedly resolved");}
   catch(java.lang.reflect.InvocationTargetException e){if(!(e.getCause() instanceof NoClassDefFoundError))throw e;}
   if(!updated.getMethod("encrypt",String.class).invoke(null,"abc").equals("qZk+NkcGgWq6PiVxeFDCbJzQ2J0="))throw new AssertionError("digest");
   for(String method:new String[]{"number","text"})
    if(!old.getMethod(method).invoke(null).equals(updated.getMethod(method).invoke(null)))throw new AssertionError("unrelated data");
   for(String expected:new String[]{"bad",ServerSqlitePatch.sha(modern)}) {
    try{ServerLoginPatch.redirect(modern,expected,false);throw new AssertionError("unguarded input");}catch(java.io.IOException ok){}
   }
   System.out.println("LOGIN_ENCODER_ABI_PASS");return;
  }
  Class<?> type=new Loader().define(Files.readAllBytes(Path.of(args[1])));
  if(args[0].equals("legacy")) {
   try{type.getMethod("encrypt",String.class).invoke(null,"abc");throw new AssertionError("missing ABI not reproduced");}
   catch(java.lang.reflect.InvocationTargetException e){if(!(e.getCause() instanceof NoClassDefFoundError))throw e;}
   System.out.println("PRIVATE_LEGACY_ABI_REPRODUCED");return;
  }
  for(String input:new String[]{"","abc","Thor-fixture-123","\\u00e9\\u4e16\\ud83d\\ude00"}) {
   String result=(String)type.getMethod("encrypt",String.class).invoke(null,input);
   String expected=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(input.getBytes(StandardCharsets.UTF_8)));
   if(!result.equals(expected)||result.length()!=28||result.contains("\\n"))throw new AssertionError("credential format changed");
  }
  System.out.println("PRIVATE_LOGIN_ENCRYPT_PASS vectors=4");
 }
}''',
            'Fixture.java': '''public class Fixture {
 public static String encrypt(String value) throws Exception {
  return new server.LegacyBase64Encoder().encode(java.security.MessageDigest.getInstance("SHA").digest(value.getBytes("UTF-8")));
 }
 public static long number(){return 12345678912345L;}
 public static String text(){return "preserved \\u0000 \\ud83d\\ude00";}
}''',
            'com/wurmonline/shared/exceptions/WurmServerException.java': '''package com.wurmonline.shared.exceptions;
public class WurmServerException extends Exception {public WurmServerException(String s,Throwable t){super(s,t);}}'''
        }
        paths = []
        for name, text in sources.items():
            p = cls.root/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(text); paths.append(p)
        result = subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.root),
            *map(str,paths),*[str(ROOT/'runtime-probe/src/server'/name) for name in
                ('ServerSqlitePatch.java','ServerLoginPatch.java','LegacyBase64Encoder.java')]],capture_output=True,text=True)
        if result.returncode: raise AssertionError(result.stdout+result.stderr)

    @classmethod
    def run_java(cls,*args,extra_cp=(),properties=()):
        return subprocess.run(['java',*properties,'-cp',os.pathsep.join([*map(str,extra_cp),str(cls.root)]),
            'server.LoginFixture',*map(str,args)],capture_output=True,text=True,timeout=20)

    def test_encoder_padding_full_and_partial_line_boundaries(self):
        values = [bytes((i*29)%256 for i in range(n)) for n in [0,1,2,3,20,54,55,56,57,58,113,114,115,171]]
        result = self.run_java('encode',','.join(base64.b64encode(v).decode() for v in values))
        self.assertEqual(result.returncode,0,result.stderr)
        actual = [base64.b64decode(line[7:]) for line in result.stdout.splitlines() if line.startswith('RESULT=')]
        expected = [b''.join(base64.b64encode(v[i:i+57])+(os.linesep.encode() if len(v[i:i+57])==57 else b'')
                            for i in range(0,len(v),57)) for v in values]
        self.assertEqual(actual,expected)
        self.assertEqual(result.stdout.count('BASE64_ENCODE_ACTIVE'),1)

    def test_legacy_constructor_method_linkage_and_unchanged_other_bytes(self):
        result = self.run_java('contract',self.root/'Fixture.class')
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('LOGIN_ENCODER_ABI_PASS',result.stdout)
        self.assertNotIn('qZk+NkcGgWq6PiVxeFDCbJzQ2J0=',result.stdout)

    def test_reject_uninspected_or_missing_class_without_creating_overlay(self):
        for label,entry in [('unknown',ENTRY),('missing','other.class')]:
            source,target=self.root/(label+'.jar'),self.root/(label+'-overlay.jar')
            with zipfile.ZipFile(source,'w') as jar: jar.write(self.root/'Fixture.class',entry)
            result=self.run_java('prepare',source,target)
            self.assertNotEqual(result.returncode,0)
            self.assertIn('SERVER_LOGIN_PATCH_UNSUPPORTED' if label=='unknown' else 'SERVER_LOGIN_CLASS_MISSING',result.stderr)
            self.assertFalse(target.exists()); self.assertFalse(Path(str(target)+'.pending').exists())
        result=self.run_java('verify',properties=[f'-Dwurm.server.loginOverlay={target}'])
        self.assertNotEqual(result.returncode,0)

    @unittest.skipUnless(SERVER,'Optional: legally owned pinned server.jar')
    def test_private_overlay_roundtrip_classpath_integrity_and_input_preservation(self):
        before=Path(SERVER).read_bytes()
        target=self.root/'private-overlay.jar'
        result=self.run_java('prepare',SERVER,target)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('BASE64_SELF_TEST_OK',result.stdout)
        self.assertEqual(Path(SERVER).read_bytes(),before)
        prop=[f'-Dwurm.server.loginOverlay={target}']
        with zipfile.ZipFile(target) as jar,zipfile.ZipFile(SERVER) as source:
            self.assertEqual(jar.namelist(),[ENTRY])
            patched=jar.read(ENTRY);original=source.read(ENTRY)
            # Compare every byte beyond the constant-pool owner replacement, including login checks.
            old=b'sun/misc/BASE64Encoder';new=b'server/LegacyBase64Encoder'
            self.assertEqual(patched.replace(struct.pack('>H',len(new))+new,struct.pack('>H',len(old))+old),original)
        result=self.run_java('verify',extra_cp=[target,SERVER],properties=prop)
        self.assertEqual(result.returncode,0,result.stderr);self.assertIn('LOGIN_PATCH_ACTIVE',result.stdout)
        result=self.run_java('verify',extra_cp=[SERVER,target],properties=prop)
        self.assertNotEqual(result.returncode,0);self.assertIn('SERVER_LOGIN_PATCH_NOT_SELECTED',result.stderr)
        self.assertNotEqual(self.run_java('prepare',SERVER,target).returncode,0)
        target.write_bytes(b'corrupt');self.assertNotEqual(self.run_java('verify',extra_cp=[target,SERVER],properties=prop).returncode,0)
        with zipfile.ZipFile(target,'w') as jar:jar.writestr(ENTRY,patched[:-1]+bytes([patched[-1]^1]))
        result=self.run_java('verify',extra_cp=[target,SERVER],properties=prop)
        self.assertNotEqual(result.returncode,0);self.assertIn('SERVER_LOGIN_PATCH_INTEGRITY_FAILED',result.stderr)
        with zipfile.ZipFile(target,'w') as jar:jar.writestr(ENTRY,patched);jar.writestr('extra','unexpected')
        self.assertNotEqual(self.run_java('verify',extra_cp=[target,SERVER],properties=prop).returncode,0)

    @unittest.skipUnless(SERVER,'Optional: legally owned pinned server.jar')
    def test_private_original_encrypt_bytecode_on_java17(self):
        target=self.root/'encrypt-overlay.jar'
        result=self.run_java('prepare',SERVER,target)
        self.assertEqual(result.returncode,0,result.stderr)
        for name,path,mode in [('legacy',SERVER,'legacy'),('fixed',target,'encrypt')]:
            with zipfile.ZipFile(path) as jar: private_class=encrypt_only_class(jar.read(ENTRY))
            dest=self.root/(name+'.class');dest.write_bytes(private_class)
            result=self.run_java(mode,dest)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('PRIVATE_LEGACY_ABI_REPRODUCED' if name=='legacy' else 'PRIVATE_LOGIN_ENCRYPT_PASS',result.stdout)


if __name__ == '__main__': unittest.main()
