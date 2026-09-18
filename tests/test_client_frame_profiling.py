"""Slow-work CPU/allocation attribution, live stack bounds and allocation-free hot hooks."""
from pathlib import Path
import re,subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
CHECK=r'''package client;
import java.lang.reflect.*;import java.lang.management.*;import java.util.concurrent.*;
public class FrameCheck {
 static volatile Object retained;static volatile long value;
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void finish(ClientFrameProfiler p){for(int i=1;i<=8;i++)p.accept(i);}
 static long field(ClientFrameProfiler p,String name)throws Exception{var f=ClientFrameProfiler.class.getDeclaredField(name);f.setAccessible(true);return ((Number)f.get(p)).longValue();}
 public static void main(String[] args)throws Exception {
  // Enable the same allocation source without requiring private game interfaces.
  var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
  var counters=ClientJobProfiler.class.getDeclaredField("counters");counters.setAccessible(true);counters.set(null,bean);
  var cap=new ClientJobProfiler.Capture(System.nanoTime(),60_000_000_000L);var p=cap.frames;
  finish(p);check(field(p,"frames")==0); // ignore partial frame on attach
  CountDownLatch started=new CountDownLatch(1);
  Thread worker=new Thread(()->{
   p.accept(0);retained=new byte[1024*1024];started.countDown();
   try{Thread.sleep(220);}catch(InterruptedException e){throw new AssertionError(e);}finish(p);
  },"fixture-sleeping-game");
  worker.start();started.await();Thread.sleep(65);p.watch();p.watch();worker.join();
  check(field(p,"recorded")==1&&field(p,"stacks")==1);p.emit(false);
  p.accept(0);long until=System.nanoTime()+80_000_000L;long n=1;
  while(System.nanoTime()<until){n=n*1664525+1013904223;value=n;}
  finish(p);p.emit(false);
  for(int j=0;j<20000;j++){p.accept(0);finish(p);}
  long before=bean.getCurrentThreadAllocatedBytes();
  for(int j=0;j<100000;j++){p.accept(0);finish(p);}
  long allocated=bean.getCurrentThreadAllocatedBytes()-before;check(allocated<65536);
  p.emit(false);
  // Synthetic time exercises overflow without waiting for 64 actual stalls.
  Method mark=ClientFrameProfiler.class.getDeclaredMethod("mark",int.class,long.class);mark.setAccessible(true);
  for(int j=0;j<100;j++) {
   long t=System.nanoTime();mark.invoke(p,0,t);mark.invoke(p,1,t+100_000_000L);
   for(int i=2;i<=8;i++)mark.invoke(p,i,t+100_000_000L+i);
   if(j==31||j==63)p.emit(false);
  }
  check(field(p,"recorded")==64&&field(p,"omitted")>0);p.emit(true);
  long frames=field(p,"frames");cap.cancel();p.accept(0);finish(p);check(field(p,"frames")==frames);
  p.detach();check(field(p,"phase")==-1);
  System.out.println("FRAME_PROFILER_PASS hotAllocatedBytes="+allocated+" retainedStalls=64 cancelled=true");
 }
}'''
class FrameProfilingTest(unittest.TestCase):
 def test_cpu_wait_allocation_stack_limits_partial_frames_stop_and_hot_allocation(self):
  with tempfile.TemporaryDirectory() as td:
   src=Path(td)/'FrameCheck.java';src.write_text(CHECK)
   subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',td,str(src),*map(str,(ROOT/'runtime-probe/src').rglob('*.java'))],check=True,capture_output=True)
   r=subprocess.run(['java','-Xms32m','-Xmx128m','-cp',td,'client.FrameCheck'],capture_output=True,text=True,timeout=30)
   self.assertEqual(r.returncode,0,r.stdout+r.stderr);self.assertIn('FRAME_PROFILER_PASS',r.stdout)
   rows=[dict(re.findall(r'(\w+)=([^ ]+)',line)) for line in r.stdout.splitlines() if ' FRAME_STALL ' in line]
   self.assertEqual(len(rows),64)
   self.assertGreaterEqual(float(rows[0]['workMs']),200)
   self.assertLess(float(rows[0]['workCpuMs']),float(rows[0]['workMs'])/2)
   self.assertGreaterEqual(int(rows[0]['workAllocatedBytes']),1024*1024)
   self.assertGreater(float(rows[1]['workCpuMs']),20)
   self.assertEqual(r.stdout.count(' STALL_STACK '),1)
   self.assertIn('Thread.sleep',r.stdout)
   print(r.stdout.splitlines()[-1])
