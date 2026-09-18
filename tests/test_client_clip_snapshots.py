"""Real intersection calls, delayed draws, cache bounds and exact private-class reversal."""
import os,subprocess,tempfile,unittest,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CLIENT=os.environ.get('WURM_TEST_CLIENT_JAR')
BASE='com/wurmonline/client/renderer/backend/ScissorControl'
SOURCES={
BASE+'.java':'''package com.wurmonline.client.renderer.backend;
public final class ScissorControl {
 int canvasWidth=720,canvasHeight=480;
 public final class ClipRect {
  int x0,y0,x1,y1;
  ClipRect(int a,int b,int c,int d){x0=a;y0=b;x1=c;y1=d;}
  ClipRect clip(int a,int b,int c,int d){return new ClipRect(Math.max(a,x0),Math.max(b,y0),Math.min(c,x1),Math.min(d,y1));}
  public void doClip(){org.lwjgl.opengl.GL11.glScissor(x0,canvasHeight-y1,x1-x0+1,y1-y0+1);}
 }
}''',
'com/wurmonline/client/GameCrashedException.java':'''package com.wurmonline.client;public class GameCrashedException{public static void warn(String s){}}''',
'com/wurmonline/client/renderer/gui/text/GuiText.java':'''package com.wurmonline.client.renderer.gui.text;public class GuiText{public static void setOrigin(int x,int y){}}''',
'org/lwjgl/opengl/GL11.java':'''package org.lwjgl.opengl;public class GL11{public static int x,y,w,h;public static void glScissor(int a,int b,int c,int d){x=a;y=b;w=c;h=d;}}''',
'client/ClipCheck.java':r'''package client;
import java.nio.file.*;import java.lang.invoke.*;import java.lang.reflect.*;import java.util.*;
public class ClipCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static volatile Object sink;
 public static void main(String[] args)throws Throwable {
  if(args[0].equals("patch")) {
   byte[] original=Files.readAllBytes(Path.of(args[1]));
   byte[] changed=args[3].equals("private")?ClientClipPatch.prepare(original):ClientClipPatch.transform(original,false);
   check(Arrays.equals(original,ClientClipPatch.transform(changed,true)));
   if(args[3].equals("private"))ClientClipPatch.verify(changed);
   Files.write(Path.of(args[2]),changed);
   changed[changed.length-1]^=1;
   try{ClientClipPatch.verify(changed);throw new AssertionError();}catch(Exception expected){}
   System.out.println("CLIP_PATCH_PASS");return;
  }
  Class<?> owner=Class.forName(ClientClipPatch.OWNER.replace('/','.').replace(".class",""));
  Class<?> type=Class.forName(ClientClipPatch.TYPE.replace('/','.'));
  Object control=owner.getConstructor().newInstance();
  var l=MethodHandles.privateLookupIn(type,MethodHandles.lookup());
  MethodHandle ctor=l.findConstructor(type,MethodType.methodType(void.class,owner,int.class,int.class,int.class,int.class))
   .asType(MethodType.methodType(Object.class,Object.class,int.class,int.class,int.class,int.class));
  MethodHandle clipped=l.findVirtual(type,"clip",MethodType.methodType(type,int.class,int.class,int.class,int.class))
   .asType(MethodType.methodType(Object.class,Object.class,int.class,int.class,int.class,int.class));
  MethodHandle[] get=new MethodHandle[4];int i=0;
  for(String name:new String[]{"x0","y0","x1","y1"})get[i++]=l.findGetter(type,name,int.class).asType(MethodType.methodType(int.class,Object.class));
  Object parent=(Object)ctor.invokeExact(control,0,0,720,480);
  System.setProperty("wurm.client.reuseClipSnapshots","true");
  Object a=(Object)clipped.invokeExact(parent,10,20,100,200);
  Object b=(Object)clipped.invokeExact(parent,10,20,100,200);check(a==b);
  // A queued snapshot remains unchanged while other bounds evict its cache slot.
  Random random=new Random(123);
  for(int j=0;j<10000;j++) {
   int x=random.nextInt(),y=random.nextInt(),z=random.nextInt(),w=random.nextInt();
   Object value=(Object)clipped.invokeExact(parent,x,y,z,w);
   check((int)get[0].invokeExact(value)==Math.max(0,x));check((int)get[1].invokeExact(value)==Math.max(0,y));
   check((int)get[2].invokeExact(value)==Math.min(720,z));check((int)get[3].invokeExact(value)==Math.min(480,w));
  }
  check((int)get[0].invokeExact(a)==10&&(int)get[3].invokeExact(a)==200);
  MethodHandle height=MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).findSetter(owner,"canvasHeight",int.class).asType(MethodType.methodType(void.class,Object.class,int.class));
  height.invokeExact(control,600);
  type.getMethod("doClip").invoke(a);check(org.lwjgl.opengl.GL11.x==10&&org.lwjgl.opengl.GL11.y==400&&org.lwjgl.opengl.GL11.w==91&&org.lwjgl.opengl.GL11.h==181);
  Object other=owner.getConstructor().newInstance();Object otherParent=(Object)ctor.invokeExact(other,0,0,720,480);
  Object otherRect=(Object)clipped.invokeExact(otherParent,10,20,100,200);check(otherRect!=a);
  // Disabled comparison calls preserve fresh-object behavior.
  System.setProperty("wurm.client.reuseClipSnapshots","false");
  Object off=(Object)clipped.invokeExact(parent,10,20,100,200);check(off!=(Object)clipped.invokeExact(parent,10,20,100,200));
  var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
  for(int j=0;j<20000;j++)sink=(Object)clipped.invokeExact(parent,10,20,100,200);
  long before=bean.getCurrentThreadAllocatedBytes();
  for(int j=0;j<100000;j++)sink=(Object)clipped.invokeExact(parent,10,20,100,200);
  long without=bean.getCurrentThreadAllocatedBytes()-before;
  System.setProperty("wurm.client.reuseClipSnapshots","true");
  for(int j=0;j<20000;j++)sink=(Object)clipped.invokeExact(parent,10,20,100,200);
  before=bean.getCurrentThreadAllocatedBytes();
  for(int j=0;j<100000;j++)sink=(Object)clipped.invokeExact(parent,10,20,100,200);
  long with=bean.getCurrentThreadAllocatedBytes()-before;
  check(without>=3_000_000&&with<65536);
  System.out.println("CLIP_REUSE_PASS withoutBytes="+without+" withBytes="+with+" "+ClientClipSnapshots.sample());
  ClientClipSnapshots.clear();check(ClientClipSnapshots.sample().contains("entries=0"));
 }
}'''
}
class ClipSnapshotsTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory();cls.addClassCleanup(cls.tmp.cleanup);cls.p=Path(cls.tmp.name);cls.classes=cls.p/'classes'
  paths=[]
  for name,source in SOURCES.items():
   p=cls.p/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);paths.append(str(p))
  subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),*paths,*map(str,(ROOT/'runtime-probe/src').rglob('*.java'))],check=True,capture_output=True)
 def run_case(self,private):
  overlay=self.p/('private' if private else 'fixture');target=overlay/(BASE+'$ClipRect.class');target.parent.mkdir(parents=True,exist_ok=True)
  if private:
   with zipfile.ZipFile(CLIENT) as z:
    source=self.p/'input.class';source.write_bytes(z.read(BASE+'$ClipRect.class'))
    (overlay/(BASE+'.class')).write_bytes(z.read(BASE+'.class'))
  else:source=self.classes/(BASE+'$ClipRect.class')
  r=subprocess.run(['java','-Xverify:all','-cp',str(self.classes),'client.ClipCheck','patch',str(source),str(target),'private' if private else 'fixture'],capture_output=True,text=True,timeout=30)
  self.assertEqual(r.returncode,0,r.stdout+r.stderr)
  r=subprocess.run(['java','-Xverify:all','-cp',os.pathsep.join(map(str,[overlay,self.classes])),'client.ClipCheck','run'],capture_output=True,text=True,timeout=30)
  self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertIn('CLIP_REUSE_PASS',r.stdout);print(('PRIVATE ' if private else 'FIXTURE ')+r.stdout.strip())
 def test_intersection_extremes_snapshot_lifetime_bounds_and_allocations(self):self.run_case(False)
 @unittest.skipUnless(CLIENT,'exact owner-provided client required')
 def test_private_clip_exact_reversal_jvm_verification_and_draw_coordinates(self):self.run_case(True)
