package client;
import java.nio.file.*;
import java.util.*;
import java.lang.ref.WeakReference;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.client.renderer.backend.Queue;
public class GuiFixture {
    static void check(boolean condition){if(!condition)throw new AssertionError();}
    static void start()throws Exception{
        ClientJobProfiler.command("start");
        // Let the initial sampler snapshot finish before asserting fixture window counts.
        long deadline=System.nanoTime()+5_000_000_000L;
        while(ClientJobProfiler.current().sampler.getState()!=Thread.State.TIMED_WAITING){
            if(System.nanoTime()>deadline)throw new AssertionError("sampler did not settle");Thread.sleep(1);
        }
    }
    static void stop()throws Exception{
        var t=Thread.getAllStackTraces().keySet().stream().filter(x->x.getName().equals("wurm-job-observation")).findFirst().orElseThrow();
        ClientJobProfiler.command("stop");t.join(5000);check(!t.isAlive());
    }
    static WeakReference<?>[] renderTemporary()throws Throwable{
        var c=new WurmComponent();var q=new Queue();ClientGuiProfiler.component(c,q,0.5f);
        return new WeakReference<?>[]{new WeakReference<>(c),new WeakReference<>(q)};
    }
    public static void main(String[] args)throws Throwable{
        if(args[0].equals("patch")){
            byte[] original=Files.readAllBytes(Path.of(args[1]));
            byte[] patched;
            if(args.length==3){patched=ClientGuiPatch.prepare(original);ClientGuiPatch.verify(patched);check(Arrays.equals(original,ClientGuiPatch.transform(patched,true,ClientGuiPatch.SITES)));}
            else{
                List<ClientGuiPatch.Site> sites=new ArrayList<>();int i=0;
                for(String offset:args[3].split(",")){var s=ClientGuiPatch.SITES.get(i++);sites.add(new ClientGuiPatch.Site(Integer.parseInt(offset),i==4?0xb6:Integer.parseInt(args[4]),s.owner(),s.name(),s.desc(),s.helper(),s.erased()));}
                patched=ClientGuiPatch.transform(original,false,sites);check(Arrays.equals(original,ClientGuiPatch.transform(patched,true,sites)));
                try{ClientGuiPatch.prepare(original);throw new AssertionError();}catch(java.io.IOException expected){}
            }
            Files.write(Path.of(args[2]),patched);patched[patched.length-1]^=1;
            try{ClientGuiPatch.verify(patched);throw new AssertionError();}catch(Exception expected){}
            System.out.println("GUI_PATCH_PASS");return;
        }
        if(args[0].equals("verify-class")){
            Class<?> c=Class.forName("com.wurmonline.client.renderer.gui.Renderer",false,GuiFixture.class.getClassLoader());
            check(c.getDeclaredMethods().length>0);System.out.println("GUI_PRIVATE_VERIFY_PASS");return;
        }
        System.setProperty("wurm.client.jobProfiling","true");ClientJobProfiler.prepare();
        Renderer r=new Renderer();Queue q=new Queue();
        r.execute(q);check(r.calls==5&&r.component.calls==1&&r.component.fraction==0.5f&&r.lastX==17&&r.lastY==29);
        start();
        r.execute(q);check(r.calls==10&&r.component.calls==2);
        var capture=ClientJobProfiler.current();var snap=capture.gui.snapshot();
        check(snap.completed()==6&&snap.rows().size()==5&&snap.omitted()==0&&snap.bytes()>=4096);
        check(snap.rows().stream().anyMatch(x->x.job.equals("overlay.crosshair")&&x.calls==2));
        for(Throwable error:new Throwable[]{new IllegalArgumentException("identity"),new AssertionError("identity")}){
            r.component.failure=error;
            try{ClientGuiProfiler.component(r.component,q,0.25f);throw new AssertionError("missing failure");}catch(Throwable actual){check(actual==error);}
        }
        r.component.failure=null;
        check(capture.gui.snapshot().rows().stream().mapToLong(x->x.failed).sum()==2);
        r.component.allocate=false;
        for(int i=0;i<20000;i++)ClientGuiProfiler.component(r.component,q,0.25f);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        long before=bean.getCurrentThreadAllocatedBytes();
        for(int i=0;i<100000;i++)ClientGuiProfiler.component(r.component,q,0.25f);
        long active=bean.getCurrentThreadAllocatedBytes()-before;check(active<65536);
        var weak=renderTemporary();for(int i=0;i<10&&(weak[0].get()!=null||weak[1].get()!=null);i++){System.gc();Thread.sleep(10);}
        check(weak[0].get()==null&&weak[1].get()==null);
        stop();check(capture.gui.retainedPairs()==0);
        for(int i=0;i<20000;i++)ClientGuiProfiler.component(r.component,q,0.25f);
        before=bean.getCurrentThreadAllocatedBytes();
        for(int i=0;i<100000;i++)ClientGuiProfiler.component(r.component,q,0.25f);
        long inactive=bean.getCurrentThreadAllocatedBytes()-before;check(inactive<65536);
        var cap=new ClientJobProfiler.Capture(System.nanoTime(),1_000_000_000L);
        for(int i=0;i<150;i++)cap.gui.add(i,"worker","fixture.Component",100,10,true);
        check(cap.gui.snapshot().omitted()==22);cap.cancel();cap.gui.add(151,"worker","late",100,10,true);check(cap.gui.snapshot().completed()==0);cap.clear();check(cap.gui.retainedPairs()==0);
        start();r.execute(q);check(ClientJobProfiler.current().gui.snapshot().completed()==6);stop();
        System.out.println("GUI_DISPATCH_PASS activeBytes="+active+" inactiveBytes="+inactive+" exceptions=identity retainedReceivers=0");
    }
}
