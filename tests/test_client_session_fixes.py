"""Execute targeted capability/GC adapters; optionally qualify the owner's exact client."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
CLIENT=os.environ.get('WURM_TEST_CLIENT_JAR')
RESOURCE='com/wurmonline/client/resources/res/missingsound.ogg'

class SessionFixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory(); cls.addClassCleanup(cls.tmp.cleanup)
        cls.home=Path(cls.tmp.name); cls.classes=cls.home/'classes'
        sources={
          'org/lwjgl/opengl/GL.java': '''package org.lwjgl.opengl;
public class GL { public static class Caps { public boolean GL_NVX_gpu_memory_info, GL_ATI_meminfo; }
 public static final Caps caps=new Caps(); public static Caps getCapabilities(){return caps;} }''',
          'org/lwjgl/opengl/GL11.java': '''package org.lwjgl.opengl;
public class GL11 { public static int calls; public static int error=0x502;
 public static int glGetInteger(int p){calls++; if(p==99)throw new IllegalArgumentException("delegate");return p+1;}
 public static String glGetString(int p){return "unrelated";} }''',
          'fixture/Engine.java': '''package fixture; import org.lwjgl.opengl.GL11;
public class Engine { public static int query(int p){return GL11.glGetInteger(p);}
 public static String other(){return GL11.glGetString(0);} public static long wide(){return 123456789123456L;} }''',
          'client/SessionFixFixture.java': '''package client;
import java.nio.file.*; import java.util.*; import java.lang.management.*; import org.lwjgl.opengl.*;
public class SessionFixFixture {
 static volatile byte[] churn;
 static void check(boolean b){if(!b)throw new AssertionError();}
 static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x->Math.max(0,x.getCollectionCount())).sum();}
 public static void main(String[] args)throws Exception{
  if(args[0].equals("gc")){
   boolean skip=Boolean.getBoolean("wurm.client.skipPeriodicGc");
   ClientWorldGc.reportPolicy(); long before=gcCount(); ClientWorldGc.gc(); long after=gcCount();
   check(skip?after==before:after>before);
   System.gc();check(gcCount()>after); // Unrelated explicit cleanup is retained.
   before=gcCount();for(int i=0;i<128;i++)churn=new byte[1024*1024];check(gcCount()>before);
   System.out.println("SCOPED_GC_PASS skip="+skip);return;
  }
  byte[] original=Files.readAllBytes(Path.of(args[1]));
  byte[] changed=ClientGlCapabilities.patch(original,false);
  check(Arrays.equals(original,ClientGlCapabilities.patch(changed,true)));
  class Loader extends ClassLoader {Class<?> define(byte[] b){return defineClass(null,b,0,b.length);}}
  Class<?> cls=new Loader().define(changed); var query=cls.getMethod("query",int.class);
  check(query.invoke(null,0x9048).equals(0)); check(query.invoke(null,0x87fb).equals(0));check(GL11.calls==0);
  check(query.invoke(null,3379).equals(3380)); check(GL11.calls==1 && GL11.error==0x502);
  GL.caps.GL_NVX_gpu_memory_info=true;GL.caps.GL_ATI_meminfo=true;
  check(query.invoke(null,0x9048).equals(0x9049));check(query.invoke(null,0x87fb).equals(0x87fc));check(GL11.calls==3);
  check(cls.getMethod("other").invoke(null).equals("unrelated"));check(cls.getMethod("wide").invoke(null).equals(123456789123456L));
  try{ClientGlCapabilities.glGetInteger(99);throw new AssertionError();}catch(IllegalArgumentException expected){check(expected.getMessage().equals("delegate"));}
  try{ClientGlCapabilities.patch(original,true);throw new AssertionError();}catch(java.io.IOException expected){}
  try{ClientMethodOwner.redirect(original,"bad","org/lwjgl/opengl/GL11","client/ClientGlCapabilities","glGetInteger","(I)I",false);throw new AssertionError();}catch(java.io.IOException expected){}
  try{ClientWorldGc.prepare(original);throw new AssertionError();}catch(java.io.IOException expected){}
  try{ClientWorldGc.verify(original);throw new AssertionError();}catch(java.io.IOException expected){}
  try{ClientSoundResources.prepare(new byte[32],new byte[32]);throw new AssertionError();}catch(java.io.IOException expected){}
  try{ClientSoundResources.verify(new byte[32]);throw new AssertionError();}catch(java.io.IOException expected){}
  String map="// authored fixture\\r\\nsound = res/missingsound.ogg\\r\\ntexture = keep.png\\r\\n";
  byte[] mapping=map.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  check(Arrays.equals(mapping,ClientSoundResources.remap(ClientSoundResources.remap(mapping,false),true)));
  try{ClientSoundResources.remap("missing".getBytes(),false);throw new AssertionError();}catch(java.io.IOException expected){}
  System.out.println("CAPABILITY_GUARD_PASS originalErrorRetained=true unrelatedCallsRetained=true reverseByteExact=true");
 }
}''',
          'client/PrivateSessionFixture.java': '''package client;
import java.nio.*; import java.nio.file.*; import java.io.*; import java.util.jar.*;
public class PrivateSessionFixture {
 public static void main(String[] args)throws Exception{
  if(args[0].equals("verify")){
   ClientGraphicsPatch.verifySelected();
   Class<?> type=Class.forName("com.wurmonline.client.resources.InternalPack");
   var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object pack=ctor.newInstance();
   var open=type.getDeclaredMethod("openStream",String.class);open.setAccessible(true);
   try(InputStream in=(InputStream)open.invoke(pack,"mappings.txt")){ClientSoundResources.verifyMappings(in.readAllBytes());}
   try(InputStream in=(InputStream)open.invoke(pack,ClientSoundResources.NEW)){ClientSoundResources.verify(in.readAllBytes());}
   System.out.println("PRIVATE_OVERLAY_PASS internalPackResourceSelection=true");return;
  }
  if(args[0].equals("wav")){
   byte[] wav=ClientSoundResources.silentWav();ClientSoundResources.verify(wav);
   try(var audio=javax.sound.sampled.AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))){
    var f=audio.getFormat();byte[] pcm=audio.readAllBytes();
    if(f.getSampleRate()!=44100 || f.getChannels()!=1 || f.getSampleSizeInBits()!=16 || pcm.length!=8820)throw new AssertionError("invalid PCM format");
    for(byte b:pcm)if(b!=0)throw new AssertionError("not silent");
   }
   System.out.println("AUTHORED_WAV_PASS");return;
  }
  byte[] original,mappings;
  try(JarFile jar=new JarFile(args[1])){
   original=jar.getInputStream(jar.getJarEntry(ClientSoundResources.PREFIX+ClientSoundResources.OLD)).readAllBytes();
   mappings=jar.getInputStream(jar.getJarEntry(ClientSoundResources.MAPPINGS)).readAllBytes();
  }
  byte[] changedMapping=ClientSoundResources.prepare(mappings,original);ClientSoundResources.verifyMappings(changedMapping);
  byte[] changed=ClientSoundResources.silentWav();
  var create=Class.forName("com.wurmonline.client.sound.formats.OggInputStream").getDeclaredConstructor(InputStream.class,String.class);
  create.setAccessible(true);
  {
   InputStream data=(InputStream)create.newInstance(new ByteArrayInputStream(original),"res/missingsound.ogg");
   byte[] pcm=data.readAllBytes();
   var rateMethod=data.getClass().getDeclaredMethod("getRate");rateMethod.setAccessible(true);
   var channelMethod=data.getClass().getDeclaredMethod("getChannels");channelMethod.setAccessible(true);
   int rate=(Integer)rateMethod.invoke(data);
   int channels=(Integer)channelMethod.invoke(data);
   if(rate!=0 || channels!=0 || pcm.length!=0)throw new AssertionError("original failure not reproduced");
   data.close();
   System.out.println("ACTUAL_OGG_DECODER fixed=false rate="+rate+" channels="+channels+" bytes="+pcm.length);
  }
  {
   Object data=Class.forName("com.wurmonline.client.sound.formats.WavData").getMethod("create",InputStream.class).invoke(null,new ByteArrayInputStream(changed));
   if(data==null)throw new AssertionError("WAV decoder failed");
   int rate=(Integer)data.getClass().getMethod("getRate").invoke(data),channels=(Integer)data.getClass().getMethod("getChannels").invoke(data);
   ByteBuffer pcm=(ByteBuffer)data.getClass().getMethod("getData").invoke(data);
   if(rate!=44100 || channels!=1 || pcm.remaining()!=8820 || !pcm.isDirect())throw new AssertionError("invalid WAV decoded format");
   for(int i=pcm.position();i<pcm.limit();i++)if(pcm.get(i)!=0)throw new AssertionError("not silence");
   System.out.println("ACTUAL_WAV_DECODER fixed=true rate="+rate+" channels="+channels+" bytes="+pcm.remaining());
  }
 }
}'''
        }
        paths=[]
        for name,code in sources.items():
            p=cls.home/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code);paths.append(str(p))
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),
            *map(str,(ROOT/'runtime-probe/src').rglob('*.java')),*paths],check=True)

    def run_fixture(self,mode,*extra,flags=()):
        r=subprocess.run(['java','-Xms32m','-Xmx32m','-XX:+UseSerialGC',*flags,'-cp',str(self.classes),
            'client.SessionFixFixture',mode,*map(str,extra)],capture_output=True,text=True,timeout=20)
        self.assertEqual(r.returncode,0,r.stdout+r.stderr);return r.stdout

    def test_capability_guard_delegates_supported_queries_and_preserves_other_methods(self):
        self.assertIn('CAPABILITY_GUARD_PASS',self.run_fixture('capability',self.classes/'fixture/Engine.class'))

    def test_default_retains_periodic_collection(self):
        self.assertIn('SCOPED_GC_PASS skip=false',self.run_fixture('gc'))

    def test_opt_in_skips_periodic_request_but_retains_pressure_and_other_gc(self):
        self.assertIn('SCOPED_GC_PASS skip=true',self.run_fixture('gc',flags=['-Dwurm.client.skipPeriodicGc=true']))

    def test_authored_wav_decodes_to_exact_silent_pcm(self):
        r=subprocess.run(['java','-cp',str(self.classes),'client.PrivateSessionFixture','wav'],capture_output=True,text=True,timeout=10)
        self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertIn('AUTHORED_WAV_PASS',r.stdout)

    @unittest.skipUnless(CLIENT,'private pinned client JAR required')
    def test_actual_client_overlay_and_original_ogg_failure_then_valid_fallback(self):
        client=Path(CLIENT)
        self.assertEqual(hashlib.sha256(client.read_bytes()).hexdigest(),'79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19')
        # Keep stub GL classes out of this real-client qualification classpath.
        helpers=self.home/'private-helpers';helpers.mkdir()
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(helpers),
            *map(str,(ROOT/'runtime-probe/src').rglob('*.java')),str(self.home/'client/PrivateSessionFixture.java')],check=True)
        cp=os.pathsep.join(map(str,[helpers,client]));overlay=self.home/'private-overlay.jar'
        r=subprocess.run(['java',f'-Dwurm.client.offscreenOverlay={overlay}','-cp',cp,'client.ClientBootstrap','prepare-graphics'],capture_output=True,text=True,timeout=20)
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        r=subprocess.run(['java',f'-Dwurm.client.offscreenOverlay={overlay}','-cp',str(overlay)+os.pathsep+cp,'client.PrivateSessionFixture','verify'],capture_output=True,text=True,timeout=20)
        self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertIn('PRIVATE_OVERLAY_PASS',r.stdout)
        r=subprocess.run(['java','-cp',cp,'client.PrivateSessionFixture','decode',str(client)],capture_output=True,text=True,timeout=20)
        self.assertEqual(r.returncode,0,r.stdout+r.stderr)
        self.assertIn('fixed=false rate=0 channels=0',r.stdout)
        self.assertIn('fixed=true rate=44100 channels=1',r.stdout)

if __name__=='__main__':unittest.main()
