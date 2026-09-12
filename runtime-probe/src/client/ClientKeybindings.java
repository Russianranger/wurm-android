package client;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Game-thread bridge: discover the imported client's catalog, edit its active file, then reload. */
public final class ClientKeybindings {
    private static Object field(Class<?> type,Object target,String name) throws ReflectiveOperationException {
        Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static Object call(Object target,String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }
    private static Class<?> settings() throws ClassNotFoundException {
        return Class.forName("com.wurmonline.client.launcherfx.WurmSettingsFX");
    }
    @SuppressWarnings("unchecked") private static Map<String,List<String>> bindings() throws ReflectiveOperationException {
        return (Map<String,List<String>>)settings().getMethod("androidBindings").invoke(null);
    }
    private static List<String[]> catalog(Map<String,List<String>> bindings) throws ReflectiveOperationException {
        List<String[]> result=new ArrayList<>(); Set<String> known=new HashSet<>();
        Class<?> type=Class.forName("com.wurmonline.client.options.keybinding.PlayerKeybind");
        for (Object option:type.getEnumConstants()) {
            String action=((String)call(option,"getCommand")).toUpperCase(Locale.ROOT);
            if (!known.add(action)) continue;
            Object category=call(option,"getCategory");
            result.add(new String[]{action,(String)call(option,"getDisplayName"),(String)call(category,"getName")});
        }
        for (String action:bindings.keySet()) if (known.add(action)) result.add(new String[]{action,action,"Other / custom bindings"});
        if (result.size()>1024) throw new IllegalStateException("Keybinding catalog exceeds 1024 actions");
        return result;
    }
    private static List<String> keys() throws ReflectiveOperationException {
        List<String> result=new ArrayList<>();
        for (Object key:Class.forName("com.wurmonline.client.options.keybinding.KeybindButtons").getEnumConstants()) {
            String name=((String)call(key,"getCommandName")).toUpperCase(Locale.ROOT);
            if (!name.isBlank() && name.matches("[A-Z0-9_]+") && !((Enum<?>)key).name().equals("Empty")) result.add(name);
        }
        return result;
    }
    public static void command(String wire) {
        String[] parts=wire.split(" ",-1);
        if (parts.length<2 || !parts[0].matches("[a-f0-9-]{36}")) return;
        Properties response=new Properties(); response.setProperty("request",parts[0]);
        try {
            Class<?> engine=Class.forName("com.wurmonline.client.WurmClientBase");
            if (field(engine,null,"gameThread") != Thread.currentThread()) throw new IllegalStateException("Keybindings require the game thread");
            Object client=field(engine,null,"clientObject");
            if (client==null || field(engine,client,"startupRenderer")!=null) throw new IllegalStateException("Wait until the game has finished loading");
            Object hud=field(engine,client,"hud");
            if (hud==null) throw new IllegalStateException("Game interface is not ready");
            Object profile=engine.getMethod("getProfileManager").invoke(null);
            File active=(File)call(profile,"getKeybindsFile");
            File loaded=(File)settings().getMethod("androidBindingFile").invoke(null);
            if (loaded==null || !active.getCanonicalFile().equals(loaded.getCanonicalFile()))
                throw new IllegalStateException("The active binding profile changed. Restart the client before editing.");
            Map<String,List<String>> current=bindings(); List<String[]> catalog=catalog(current); List<String> keys=keys();
            String revision=(String)settings().getMethod("androidBindingRevision").invoke(null);
            String notice="";
            if (parts.length==5 && parts[1].equals("SET")) {
                if (!revision.equals(parts[2])) throw new IllegalStateException("Bindings changed; refresh the editor");
                int index=Integer.parseInt(parts[3]);
                if (index<0 || index>=catalog.size()) throw new IllegalArgumentException("Unknown action");
                List<String> wanted=parts[4].equals("-") ? List.of() : List.of(parts[4].split(",",-1));
                if (wanted.size()>8) throw new IllegalArgumentException("Use at most eight keys per action");
                for (String key:wanted) {
                    String[] tokens=key.split("\\+",-1);
                    if (!keys.contains(tokens[tokens.length-1])) throw new IllegalArgumentException("Unknown key: "+key);
                }
                // Resolve the existing reload API before changing any bytes.
                Object console=field(hud.getClass(),hud,"console");
                Method reload=console.getClass().getMethod("executeKeybinds");
                Method translate=console.getClass().getDeclaredMethod("translateKeyString",String.class);
                translate.setAccessible(true);
                Set<Integer> codes=new HashSet<>();
                for (String key:wanted) {
                    int code=(Integer)translate.invoke(console,key);
                    if (code==0 || !codes.add(code)) throw new IllegalArgumentException("Unknown or duplicate key: "+key);
                }
                for (var binding:current.entrySet()) if (!binding.getKey().equals(catalog.get(index)[0]))
                    for (String key:binding.getValue()) if (codes.contains((Integer)translate.invoke(console,key)))
                        throw new IllegalArgumentException(key+" is already assigned to "+binding.getKey()+"; clear that binding first");
                settings().getMethod("androidEditBinding",String.class,List.class,String.class).invoke(null,catalog.get(index)[0],wanted,revision);
                try {
                    reload.invoke(console);
                    Object bar=hud.getClass().getMethod("getSelectBar").invoke(hud);
                    bar.getClass().getMethod("updateActions").invoke(bar);
                    hud.getClass().getMethod("updateBinds",boolean.class).invoke(hud,false);
                    notice="Saved and applied to the running game.";
                    System.out.println("[client-ui] KEYBINDS_APPLIED actionIndex="+index+" keys="+wanted.size());
                } catch (ReflectiveOperationException failure) {
                    notice="Saved. Restart the client to apply; live refresh failed.";
                    System.out.println("[client-ui] KEYBINDS_RESTART_REQUIRED "+failure);
                }
                current=bindings(); revision=(String)settings().getMethod("androidBindingRevision").invoke(null);
            } else if (parts.length!=2 || !parts[1].equals("READ")) throw new IllegalArgumentException("Unknown keybinding request");
            response.setProperty("status","ok"); response.setProperty("notice",notice);
            response.setProperty("revision",revision);
            response.setProperty("editable",settings().getMethod("androidBindingsEditable").invoke(null).toString());
            response.setProperty("keyChoices",String.join(",",keys));
            response.setProperty("count",Integer.toString(catalog.size()));
            for (int i=0;i<catalog.size();i++) {
                String[] item=catalog.get(i); String prefix="item."+i+".";
                response.setProperty(prefix+"action",item[0]); response.setProperty(prefix+"label",item[1]);
                response.setProperty(prefix+"category",item[2]); response.setProperty(prefix+"keys",String.join(",",current.getOrDefault(item[0],List.of())));
            }
        } catch (Exception failure) {
            Throwable cause=failure instanceof InvocationTargetException ? failure.getCause() : failure;
            response.setProperty("status","error"); response.setProperty("notice",String.valueOf(cause.getMessage()));
            System.out.println("[client-ui] KEYBINDS_FAILED "+cause);
        }
        String destination=System.getProperty("wurm.client.keybindReport");
        if (destination==null) return;
        Path target=Path.of(destination), pending=target.resolveSibling(target.getFileName()+".pending");
        try {
            ByteArrayOutputStream encoded=new ByteArrayOutputStream(); response.store(encoded,"Wurm Android keybinding response");
            if (encoded.size()>524288) {
                response.clear(); response.setProperty("request",parts[0]); response.setProperty("status","error");
                response.setProperty("notice","Binding catalog exceeds the editor's size limit; files are preserved.");
                encoded.reset(); response.store(encoded,"Wurm Android keybinding response");
            }
            Files.write(pending,encoded.toByteArray());
            Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) { System.out.println("[client-ui] KEYBINDS_RESPONSE_FAILED "+failure); }
        finally { try { Files.deleteIfExists(pending); } catch (IOException ignored) {} }
    }
}
