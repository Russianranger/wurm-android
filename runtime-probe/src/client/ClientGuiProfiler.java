package client;

import java.lang.invoke.*;

/** Top-level component/overlay attribution during the existing bounded recording only. */
public final class ClientGuiProfiler {
    private static final MethodHandle[] calls=new MethodHandle[5];
    private static final String[] OVERLAYS={"", "overlay.spyglass", "overlay.crosshair", "overlay.hover", "overlay.messages"};
    private ClientGuiProfiler() { }
    private static synchronized MethodHandle resolve(int kind)throws ReflectiveOperationException {
        if(calls[kind]!=null)return calls[kind];
        Class<?> queue=Class.forName("com.wurmonline.client.renderer.backend.Queue");
        Class<?> owner=Class.forName("com.wurmonline.client.renderer.gui."+(kind==0?"WurmComponent":"Renderer"));
        String name=switch(kind){case 0->"render";case 1->"renderSpyglassDistance";case 2->"renderCrosshair";case 3->"renderHoverInfo";default->"renderOnscreenMessageViewer";};
        MethodType typed=kind==0?MethodType.methodType(void.class,queue,float.class):kind==3?MethodType.methodType(void.class,queue,int.class,int.class):MethodType.methodType(void.class,queue);
        MethodType erased=kind==0?MethodType.methodType(void.class,Object.class,Object.class,float.class):kind==3?MethodType.methodType(void.class,Object.class,Object.class,int.class,int.class):MethodType.methodType(void.class,Object.class,Object.class);
        return calls[kind]=MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).findVirtual(owner,name,typed).asType(erased);
    }
    public static void component(Object widget,Object queue,float fraction)throws Throwable{invoke(0,widget,queue,fraction,0,0);}
    public static void spyglass(Object renderer,Object queue)throws Throwable{invoke(1,renderer,queue,0,0,0);}
    public static void crosshair(Object renderer,Object queue)throws Throwable{invoke(2,renderer,queue,0,0,0);}
    public static void hover(Object renderer,Object queue,int x,int y)throws Throwable{invoke(3,renderer,queue,0,x,y);}
    public static void messages(Object renderer,Object queue)throws Throwable{invoke(4,renderer,queue,0,0,0);}
    private static void invoke(int kind,Object receiver,Object queue,float fraction,int x,int y)throws Throwable{
        MethodHandle call=calls[kind];if(call==null)call=resolve(kind);
        ClientJobProfiler.Capture capture=ClientJobProfiler.current();
        long start=capture==null?0:System.nanoTime();
        if(capture!=null&&!capture.accepts(start))capture=null;
        long before=capture==null?-1:ClientJobProfiler.allocated();boolean success=false;
        try{
            if(kind==0)call.invokeExact(receiver,queue,fraction);
            else if(kind==3)call.invokeExact(receiver,queue,x,y);
            else call.invokeExact(receiver,queue);
            success=true;
        }finally{
            if(capture!=null){
                long after=ClientJobProfiler.allocated(),elapsed=System.nanoTime()-start;
                // Diagnostics must not replace a component exception or retain its receiver/queue.
                try{Thread thread=Thread.currentThread();capture.gui.add(thread.getId(),thread.getName(),kind==0?receiver.getClass().getName():OVERLAYS[kind],before>=0&&after>=before?after-before:-1,elapsed,success);}
                catch(RuntimeException|LinkageError unavailable){capture.cancel();}
            }
        }
    }
}
