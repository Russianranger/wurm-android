package client;
import java.nio.file.*;
import java.lang.invoke.*;
import java.util.*;
import java.util.regex.*;

public class InventoryFixture {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    static class Loader extends ClassLoader { Class<?> load(byte[] b) {return defineClass(null,b,0,b.length);} }
    static boolean baseline(String value,String expression) {return value.matches(expression);}
    static String failure(String value,String expression,boolean cached) {
        try {if(cached)ClientInventoryNumbers.matches(value,expression);else baseline(value,expression);return "none";}
        catch(Throwable e){return e.getClass().getName()+(e instanceof PatternSyntaxException?":"+e.getMessage():"");}
    }
    public static void main(String[] args)throws Throwable {
        if(args[0].equals("private")) {
            byte[] original=Files.readAllBytes(Path.of(args[1])),patched=ClientInventoryPatch.prepare(original);
            ClientInventoryPatch.verify(patched);
            check(Arrays.equals(original,ClientInventoryPatch.transform(patched,true,2139,"renderComponent","(Lcom/wurmonline/client/renderer/backend/Queue;F)V")));
            byte[] bad=patched.clone();bad[bad.length-1]^=1;
            try{ClientInventoryPatch.verify(bad);throw new AssertionError();}catch(Exception expected){}
            try{ClientInventoryPatch.prepare(patched);throw new AssertionError();}catch(Exception expected){}
            Files.createDirectories(Path.of(args[2]).getParent());Files.write(Path.of(args[2]),patched);
            System.out.println("INVENTORY_PRIVATE_PATCH_PASS");return;
        }
        if(args[0].equals("verify")) {
            Class.forName("com.wurmonline.client.renderer.gui.WurmTreeList$TreeListPanel",false,InventoryFixture.class.getClassLoader()).getDeclaredMethods();
            System.out.println("INVENTORY_PRIVATE_VERIFY_PASS");return;
        }
        byte[] original=Files.readAllBytes(Path.of(args[1]));int offset=Integer.parseInt(args[2]);
        byte[] patched=ClientInventoryPatch.transform(original,false,offset,"render","(Ljava/lang/String;C)Z");
        check(Arrays.equals(original,ClientInventoryPatch.transform(patched,true,offset,"render","(Ljava/lang/String;C)Z")));
        Class<?> before=new Loader().load(original),after=new Loader().load(patched);
        var type=MethodType.methodType(boolean.class,String.class,char.class);
        MethodHandle a=MethodHandles.publicLookup().findStatic(before,"render",type),b=MethodHandles.publicLookup().findStatic(after,"render",type);
        Set<Character> separators=new HashSet<>();for(Locale locale:Locale.getAvailableLocales())separators.add(new java.text.DecimalFormatSymbols(locale).getDecimalSeparator());
        String[] values={"","0","123","123.45","1,2",".5","5.",".",",","1.2.3","-1","+1","1e2","NaN","Infinity","١٢٣","１２３","item","12\n","12\r\n"," ","123\u066B45","\uD83D\uDE00"};
        for(char separator:separators)for(String value:values)check((boolean)a.invokeExact(value,separator)==(boolean)b.invokeExact(value,separator));
        Random random=new Random(81051);String chars="0123456789.,+- e\n\u066B\u0661";
        for(int i=0;i<10000;i++){StringBuilder s=new StringBuilder();for(int j=random.nextInt(25);j>0;j--)s.append(chars.charAt(random.nextInt(chars.length())));String v=s.toString();char sep=i%2==0?'.':',';check((boolean)a.invokeExact(v,sep)==(boolean)b.invokeExact(v,sep));}
        for(String expression:new String[]{"[","(",null,"\\d*\\.?\\d*","(a+)\\1"})for(String value:new String[]{null,"aaaa","123.5"})check(failure(value,expression,false).equals(failure(value,expression,true)));
        // Each rendering worker has its own matcher; changing expressions replaces its sole cache entry.
        List<Thread> threads=new ArrayList<>();List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());
        for(int i=0;i<8;i++){final int n=i;Thread t=new Thread(()->{try{for(int j=0;j<10000;j++){String expression=j%2==0?"a+":"b+",v=n%2==0?"aaaa":"bbbb";check(ClientInventoryNumbers.matches(v,expression)==v.matches(expression));}}catch(Throwable e){errors.add(e);}});threads.add(t);t.start();}
        for(Thread t:threads)t.join();check(errors.isEmpty());
        String large=new String(new char[100000]);var weak=new java.lang.ref.WeakReference<>(large);ClientInventoryNumbers.matches(large,"x*");large=null;
        for(int i=0;i<10&&weak.get()!=null;i++){System.gc();Thread.sleep(10);}check(weak.get()==null);
        for(int i=0;i<30000;i++){check((boolean)a.invokeExact("123.45",'.'));check((boolean)b.invokeExact("123.45",'.'));}
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long start=bean.getCurrentThreadAllocatedBytes();
        for(int i=0;i<100000;i++)check((boolean)a.invokeExact("123.45",'.'));
        long baseline=bean.getCurrentThreadAllocatedBytes()-start;start=bean.getCurrentThreadAllocatedBytes();
        for(int i=0;i<100000;i++)check((boolean)b.invokeExact("123.45",'.'));
        long reused=bean.getCurrentThreadAllocatedBytes()-start;check(reused<baseline/3);
        System.out.println("INVENTORY_REUSE_PASS calls=100000 baselineBytes="+baseline+" reusedBytes="+reused+" separators="+separators.size()+" exactRegex=true noItemRetention=true");
    }
}
