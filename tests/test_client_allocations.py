"""Counter-window correctness and actual JVM allocation attribution/lifetime."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
FIXTURE=r'''
package client;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
public class Allocations {
 static volatile Object retained;
 static void check(boolean ok) { if(!ok) throw new AssertionError(); }
 public static void main(String[] args) throws Exception {
  var w=new ClientAllocationMeasurements.Window();
  var s=w.observe(1_000_000_000L,new long[]{1,2},new long[]{1000,2000},0);
  check(s.bytes()==0 && s.fresh()==2 && s.elapsedMs()==0);
  s=w.observe(31_000_000_000L,new long[]{2,1,3},new long[]{2400,1100,900000},0);
  check(s.bytes()==500 && s.elapsedMs()==30000 && s.matched()==2 && s.fresh()==1);
  check(s.top().get(0).id()==2 && s.top().get(0).bytes()==400);
  s=w.observe(61_000_000_000L,new long[]{2,3,4},new long[]{20,-1,500},7);
  check(s.bytes()==0 && s.fresh()==2 && s.missing()==1 && s.departed()==2 && s.omitted()==7);
  check(w.retainedThreads()==2);
  for(int n=0;n<500;n++) w.observe(n+62_000_000_000L,new long[]{n+100},new long[]{123},0);
  check(w.retainedThreads()==1);
  check(ClientAllocationMeasurements.safeName("name\n[]=").equals("name____"));
  check(ClientAllocationMeasurements.safeName("x".repeat(100)).length()==48);
  long[] ids=new long[256],counts=new long[256];
  for(int i=0;i<256;i++) {ids[i]=i+1;counts[i]=1;}
  w=new ClientAllocationMeasurements.Window();w.observe(0,ids,counts,0);
  for(int i=0;i<256;i++) counts[i]=i+2;
  s=w.observe(1_000_000_000L,ids,counts,0);check(s.top().size()==4 && s.top().get(0).id()==256);
  var bean=(ThreadMXBean)ManagementFactory.getThreadMXBean();
  check(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
  CountDownLatch ready=new CountDownLatch(1),go=new CountDownLatch(1),done=new CountDownLatch(1),release=new CountDownLatch(1);
  Thread worker=new Thread(()->{try {
   ready.countDown();go.await();
   for(int i=0;i<128;i++) retained=new byte[65536];
   done.countDown();release.await();
  }catch(InterruptedException e){throw new AssertionError(e);}},"allocation-fixture");
  worker.start();ready.await();w=new ClientAllocationMeasurements.Window();
  ClientAllocationMeasurements.sample(bean,w);go.countDown();done.await();
  String line=ClientAllocationMeasurements.sample(bean,w);
  check(line.contains("allocation-fixture]="));
  long bytes=Long.parseLong(line.split("allocation-fixture]=")[1].split(" ")[0]);check(bytes>=8*1024*1024);
  release.countDown();worker.join();ClientAllocationMeasurements.sample(bean,w);
  bean.setThreadAllocatedMemoryEnabled(false);
  try {ClientAllocationMeasurements.sample(bean,w);throw new AssertionError();}
  catch(UnsupportedOperationException expected) { }
  ClientAllocationMeasurements.start();Thread.sleep(200);
  Thread sampler=Thread.getAllStackTraces().keySet().stream().filter(t->t.getName().equals("wurm-client-allocations")).findFirst().orElseThrow();
  check(sampler.isDaemon());
  System.out.println("ALLOCATION_PASS realChurn="+bytes+" bounded=true noForcedGc=true");
 }
}
'''

class AllocationTest(unittest.TestCase):
    def test_window_churn_attribution_missing_counters_and_daemon_lifetime(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'Allocations.java';source.write_text(FIXTURE)
            subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',tmp,str(source),
                str(ROOT/'runtime-probe/src/client/ClientAllocationMeasurements.java')],check=True)
            result=subprocess.run(['java','-Xms32m','-Xmx128m','-XX:+UseSerialGC',
                '-Xlog:gc=info,gc+heap=info:stdout:utctime,pid,tid,tags','-cp',tmp,'client.Allocations'],capture_output=True,text=True,timeout=15)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertIn('ALLOCATION_PASS',result.stdout)
            self.assertEqual(result.stdout.count('[client-allocation]'),1)
            self.assertNotIn('(System.gc())',result.stdout)

