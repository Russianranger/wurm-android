"""Bounded job attribution, exact dispatch semantics, and optional real-client overlay."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
CLIENT=os.environ.get('WURM_TEST_CLIENT_JAR')

FIXTURE=r'''
package client;
import java.nio.file.*;
import java.util.*;
import java.lang.ref.WeakReference;
import com.wurmonline.client.job.Job;
public class JobFixture {
 static volatile Object sink;
 static int callbacks;
 static final RuntimeException failure=new IllegalStateException("original job failure");
 static final Error fatal=new AssertionError("original job error");
 public static class Allocating implements Job {
  public void execute(Object argument) { if(argument==failure)throw failure; if(argument==fatal)throw fatal; sink=new byte[32768]; }
 }
 public static class Noop implements Job {public void execute(Object argument){}}
 static void check(boolean ok) {if(!ok)throw new AssertionError();}
 static void stop()throws Exception {
  Thread observer=Thread.getAllStackTraces().keySet().stream().filter(t->t.getName().equals("wurm-job-observation")).findFirst().orElseThrow();
  check(observer.isDaemon()); ClientJobProfiler.command("stop"); observer.join(5000); check(!observer.isAlive());
 }
 static WeakReference<?>[] temporary()throws Throwable {
  Object arg=new Object(); Job job=new Allocating(); ClientJobProfiler.execute(job,arg);
  return new WeakReference<?>[]{new WeakReference<>(job),new WeakReference<>(arg)};
 }
 public static void main(String[] args)throws Throwable {
  System.setProperty("wurm.client.jobProfiling","true"); ClientJobProfiler.prepare();
  if(args[0].equals("overlay")) {
   System.setProperty("wurm.client.offscreenOverlay",args[1]);ClientGraphicsPatch.prepare();
   try(var jar=new java.util.jar.JarFile(args[1])){check(jar.size()==12);}
   System.out.println("PRIVATE_JOB_OVERLAY_READY");return;
  }
  if(args[0].equals("verify-overlay")) {
   System.setProperty("wurm.client.offscreenOverlay",args[1]);ClientGraphicsPatch.verifySelected();
   System.out.println("PRIVATE_JOB_OVERLAY_SELECTED");return;
  }
  if(args[0].equals("private")) {
   try(var jar=new java.util.jar.JarFile(args[1])) {
    byte[] original=jar.getInputStream(jar.getJarEntry(ClientJobPatch.EXECUTOR)).readAllBytes();
    byte[] patched=ClientJobPatch.prepare(original); ClientJobPatch.verify(patched);
    check(Arrays.equals(original,ClientJobPatch.patch(patched,ClientGraphicsPatch.sha(patched),true)));
    Path out=Path.of(args[2],ClientJobPatch.EXECUTOR); Files.createDirectories(out.getParent()); Files.write(out,patched);
    patched[patched.length-1]^=1;
    try{ClientJobPatch.verify(patched);throw new AssertionError();}catch(java.io.IOException expected){}
   }
   System.out.println("PRIVATE_JOB_PATCH_PASS reverseByteExact=true"); return;
  }
  if(args[0].equals("manager")) {
   // Execute the imported manager/executor with authored work, preserving callback and wait semantics.
   ClientJobProfiler.command("start");
   Class<?> manager=Class.forName("com.wurmonline.client.job.JobManager");
   Object instance=manager.getMethod("getInstance").invoke(null);
   Class<?> callback=Class.forName("com.wurmonline.client.job.JobCompletionCallback");
   var completed=new java.util.concurrent.CountDownLatch(64);
   Object notify=java.lang.reflect.Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a)->{completed.countDown();return null;});
   var schedule=manager.getMethod("schedule",Job.class,Object.class,callback);
   for(int i=0;i<64;i++) schedule.invoke(instance,new Allocating(),new Object(),notify);
   check(completed.await(5,java.util.concurrent.TimeUnit.SECONDS));
   manager.getMethod("shutdown").invoke(instance); stop();
   System.out.println("PRIVATE_EXECUTOR_PASS callbacks=64 shutdown=true"); return;
  }
  if(args[0].equals("retention")) {
   ClientJobProfiler.command("start"); var refs=temporary();
   // Forced cleanup is confined to this host-only weak-reference test.
   for(int i=0;i<10 && (refs[0].get()!=null || refs[1].get()!=null);i++){System.gc();Thread.sleep(20);}
   check(refs[0].get()==null && refs[1].get()==null); stop();
   System.out.println("JOB_REFERENCES_RELEASED"); return;
  }
  if(args[0].equals("deadline")) {
   var capture=new ClientJobProfiler.Capture(System.nanoTime(),50_000_000L);
   long before=System.nanoTime();ClientJobProfiler.record(capture);
   check(System.nanoTime()-before<2_000_000_000L && capture.retainedPairs()==0);
   System.out.println("JOB_RECORDING_EXPIRED");return;
  }
  if(args[0].equals("overhead")) {
   var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
   Object job=new Noop(),argument=new Object();
   for(int i=0;i<20000;i++)ClientJobProfiler.execute(job,argument);
   long before=bean.getCurrentThreadAllocatedBytes(),start=System.nanoTime();
   for(int i=0;i<100000;i++)ClientJobProfiler.execute(job,argument);
   long inactive=bean.getCurrentThreadAllocatedBytes()-before, inactiveNs=System.nanoTime()-start;
   ClientJobProfiler.command("start");
   for(int i=0;i<20000;i++)ClientJobProfiler.execute(job,argument);
   before=bean.getCurrentThreadAllocatedBytes();start=System.nanoTime();
   for(int i=0;i<100000;i++)ClientJobProfiler.execute(job,argument);
   long active=bean.getCurrentThreadAllocatedBytes()-before,activeNs=System.nanoTime()-start;stop();
   check(inactive<65536 && active<65536);
   System.out.println("JOB_HOOK_OVERHEAD inactiveBytes="+inactive+" activeBytes="+active+" inactiveNsPerCall="+(inactiveNs/100000)+" activeNsPerCall="+(activeNs/100000));return;
  }
  byte[] original=Files.readAllBytes(Path.of(args[1]));
  byte[] patched=ClientJobPatch.patch(original,ClientGraphicsPatch.sha(original),false);
  check(Arrays.equals(original,ClientJobPatch.patch(patched,ClientGraphicsPatch.sha(patched),true)));
  try{ClientJobPatch.prepare(original);throw new AssertionError();}catch(java.io.IOException expected){}
  class Loader extends ClassLoader {Class<?> define(byte[] b){return defineClass(null,b,0,b.length);}}
  Class<?> executor=new Loader().define(patched);
  var ctor=executor.getConstructor(Job.class,Object.class,Runnable.class);
  Runnable callback=()->callbacks++;
  ((Runnable)ctor.newInstance(new Allocating(),new Object(),callback)).run(); check(callbacks==1);
  ClientJobProfiler.command("start"); ClientJobProfiler.command("start");
  Runnable worker=(Runnable)ctor.newInstance(new Allocating(),new Object(),callback);
  Thread t=new Thread(()->{for(int i=0;i<128;i++)worker.run();},"Job executor 0"); t.start();t.join();check(callbacks==129);
  for(Throwable error:new Throwable[]{failure,fatal}) {
   Runnable throwing=(Runnable)ctor.newInstance(new Allocating(),error,callback);
   try{throwing.run();throw new AssertionError("not forwarded");}catch(Throwable actual){check(actual==error);}
  }
  check(callbacks==129); stop();
  var capture=new ClientJobProfiler.Capture(System.nanoTime(),10_000_000_000L);
  for(int i=0;i<160;i++)capture.add(i,"worker","fixture.Job",i<5?-1:100,12,true);
  var snap=capture.snapshot();check(snap.completed()==160 && snap.omitted()==32 && snap.unavailable()==5 && snap.bytes()==15500);
  check(capture.retainedPairs()==128 && snap.rows().size()==128);
  capture.cancel();capture.add(999,"worker","fixture.Other",100,12,true);check(capture.snapshot().completed()==0);
  capture.clear();check(capture.retainedPairs()==0);
  var deadline=new ClientJobProfiler.Capture(100,50);check(deadline.accepts(149) && !deadline.accepts(150));
  ClientJobProfiler.command("start");stop(); // A completed recording permits another bounded recording.
  System.out.println("JOB_PROFILING_PASS originalExceptions=true bounded=true");
 }
}
'''

class JobProfilingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();cls.addClassCleanup(cls.tmp.cleanup)
        cls.home=Path(cls.tmp.name);cls.classes=cls.home/'classes'
        sources={
          'com/wurmonline/client/job/Job.java':'package com.wurmonline.client.job; public interface Job {void execute(Object argument);}',
          'fixture/Executor.java':'''package fixture; import com.wurmonline.client.job.Job;
public class Executor implements Runnable {final Job job;final Object argument;final Runnable callback;
 public Executor(Job j,Object a,Runnable c){job=j;argument=a;callback=c;}
 public void run(){job.execute(argument);callback.run();} public long untouched(){return 123456789123456L;}}''',
          'client/JobFixture.java':FIXTURE}
        paths=[]
        for name,code in sources.items():
            path=cls.home/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(code);paths.append(str(path))
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),
            *map(str,(ROOT/'runtime-probe/src').rglob('*.java')),*paths],check=True)

    def launch(self,mode,*args,cp=None):
        result=subprocess.run(['java','-Xms32m','-Xmx128m','-XX:+UseSerialGC',
            '-Xlog:gc=info:stdout','-cp',cp or str(self.classes),'client.JobFixture',mode,*map(str,args)],
            capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        return result.stdout

    def test_dispatch_allocation_exceptions_bounds_stop_and_restart(self):
        out=self.launch('fixture',self.classes/'fixture/Executor.class')
        self.assertIn('JOB_PROFILING_PASS',out)
        self.assertIn('class=client.JobFixture$Allocating',out)
        self.assertIn('thread=Job_executor_0',out)
        self.assertIn('calls=128',out)
        self.assertIn('ALREADY_RECORDING',out)
        self.assertEqual(out.count(' END '),2)
        self.assertNotIn('(System.gc())',out)

    def test_capture_does_not_retain_job_or_argument(self):
        self.assertIn('JOB_REFERENCES_RELEASED',self.launch('retention'))

    def test_recording_expires_without_a_stop_command(self):
        out=self.launch('deadline')
        self.assertIn('JOB_RECORDING_EXPIRED',out)
        self.assertIn('END reason=duration',out)

    def test_warmed_hook_does_not_allocate_per_job(self):
        out=self.launch('overhead')
        print(next(line for line in out.splitlines() if 'JOB_HOOK_OVERHEAD ' in line))
        self.assertNotIn('(System.gc())',out)

    @unittest.skipUnless(CLIENT,'owner-supplied exact client required')
    def test_exact_private_executor_callback_completion_and_shutdown(self):
        overlay=self.home/'overlay'
        self.assertIn('PRIVATE_JOB_PATCH_PASS',self.launch('private',CLIENT,overlay))
        out=self.launch('manager',cp=os.pathsep.join(map(str,[overlay,CLIENT,self.classes])))
        self.assertIn('PRIVATE_EXECUTOR_PASS',out)
        self.assertIn('class=client.JobFixture$Allocating',out)
        self.assertNotIn('(System.gc())',out)

    @unittest.skipUnless(CLIENT,'owner-supplied exact client required')
    def test_optional_job_hook_coexists_with_complete_private_graphics_overlay(self):
        overlay=self.home/'full-overlay.jar'
        self.assertIn('PRIVATE_JOB_OVERLAY_READY',self.launch('overlay',overlay,cp=os.pathsep.join(map(str,[self.classes,CLIENT]))))
        out=self.launch('verify-overlay',overlay,cp=os.pathsep.join(map(str,[overlay,CLIENT,self.classes])))
        self.assertIn('PRIVATE_JOB_OVERLAY_SELECTED',out)
        self.assertIn('JOB_PROFILING_PATCH_ACTIVE',out)
