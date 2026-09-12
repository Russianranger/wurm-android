package wurm.android.compat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Headless bind-file storage. This never executes console commands. */
public final class KeybindStore {
    private static final int LIMIT = 1024 * 1024;
    private static final Pattern BIND = Pattern.compile("(?i)^bind\\s+(\\S+)\\s+(.+)$");
    private final LinkedHashMap<String, List<String>> bindings = new LinkedHashMap<>();
    private Path source;
    private byte[] snapshot;
    private List<String> comments = List.of();
    private boolean dirty, otherCommands;

    private static String command(String value) {
        String text = value.trim();
        if (text.isEmpty() || text.length() > 16384 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid binding action");
        if (segments(text).size()!=1) throw new IllegalArgumentException("Quote semicolons inside a binding action");
        return text.contains("\"") ? text : text.toUpperCase(Locale.ROOT);
    }
    private static String key(String value) {
        if (!value.matches("[A-Za-z0-9_+.-]{1,80}")) throw new IllegalArgumentException("Invalid binding key");
        return value.toUpperCase(Locale.ROOT);
    }
    private static byte[] read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(LIMIT + 1);
            if (bytes.length > LIMIT) throw new IOException("Keybindings exceed 1 MiB");
            return bytes;
        }
    }
    private static List<String> segments(String line) {
        List<String> parts = new ArrayList<>();
        boolean quoted=false, escaped=false; int start=0;
        for (int i=0;i<line.length();i++) {
            char c=line.charAt(i);
            if (escaped) { escaped=false; continue; }
            if (c=='\\' && quoted) { escaped=true; continue; }
            if (c=='"') quoted=!quoted;
            if (c==';' && !quoted) { parts.add(line.substring(start,i).trim()); start=i+1; }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quote in keybindings");
        parts.add(line.substring(start).trim()); return parts;
    }
    public synchronized void load(Path file) throws IOException {
        Path next=file.toAbsolutePath().normalize(); byte[] bytes=read(next);
        String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        KeybindStore parsed=new KeybindStore(); List<String> retained=new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            String trimmed=line.trim();
            if (trimmed.length()>16384) throw new IOException("Keybinding line exceeds 16 KiB");
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) { retained.add(line); continue; }
            for (String part : segments(trimmed)) {
                if (part.isEmpty()) continue;
                var match=BIND.matcher(part);
                if (match.matches()) parsed.add(match.group(2),match.group(1),false);
                else if (part.matches("(?i)^bind(?:\\s.*)?$"))
                    throw new IOException("Incomplete bind command");
                else parsed.otherCommands=true;
            }
        }
        bindings.clear(); bindings.putAll(parsed.bindings); source=next; snapshot=bytes;
        comments=retained; otherCommands=parsed.otherCommands; dirty=false;
        System.out.println("[client] KEYBINDS_LOADED actions="+bindings.size()+" keys="+keyCount()+" file="+next+" executor=none javafx=false");
    }
    public synchronized int keyCount() { return bindings.values().stream().mapToInt(List::size).sum(); }
    public synchronized Map<String,List<String>> snapshot() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        bindings.forEach((action,keys) -> result.put(action,List.copyOf(keys)));
        return result;
    }
    public synchronized String revision() throws IOException {
        if (source == null) throw new IOException("Keybindings are not loaded yet");
        if (dirty || !Arrays.equals(snapshot,read(source))) throw new IOException("Bindings changed; reload the selected profile before editing");
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public synchronized boolean editable() { return source != null && !otherCommands && !dirty; }
    public synchronized File file() { return source == null ? null : source.toFile(); }
    public static String editorKey(String value) {
        String[] parts=key(value.trim()).split("[-+]",-1);
        Set<String> mods=new HashSet<>();
        for (int i=0;i<parts.length-1;i++)
            if (!Set.of("CTRL","ALT","SHIFT").contains(parts[i]) || !mods.add(parts[i]))
                throw new IllegalArgumentException("Use CTRL, ALT or SHIFT once before a key");
        String base=parts[parts.length-1];
        if (base.isEmpty()) throw new IllegalArgumentException("Choose a key after the modifier");
        StringBuilder result=new StringBuilder();
        for (String mod:List.of("ALT","CTRL","SHIFT")) if (mods.contains(mod)) result.append(mod).append('+');
        return result.append(base).toString();
    }
    /** Save one complete action atomically. Conflicts never silently steal another action's key. */
    public synchronized void edit(String action,List<String> keys,String expected) throws IOException {
        if (!revision().equals(expected)) throw new IOException("Keybindings changed; refresh the editor");
        if (!editable()) throw new IOException("This file contains custom console commands; its contents are preserved");
        String name=command(action);
        LinkedHashSet<String> wanted=new LinkedHashSet<>();
        for (String value:keys) if (!wanted.add(editorKey(value))) throw new IllegalArgumentException("Duplicate key in this action");
        for (var entry:bindings.entrySet()) if (!entry.getKey().equals(name)) for (String value:entry.getValue())
            if (wanted.contains(editorKey(value))) throw new IllegalArgumentException(value+" is already assigned to "+entry.getKey()+"; clear that binding first");
        if (List.copyOf(wanted).equals(bindings.getOrDefault(name,List.of()))) return;
        Map<String,List<String>> before=snapshot();
        bindings.put(name,new ArrayList<>(wanted)); dirty=true;
        try { save(source); }
        catch (IOException | RuntimeException failure) {
            bindings.clear(); before.forEach((a,k) -> bindings.put(a,new ArrayList<>(k))); dirty=false;
            throw failure;
        }
    }
    public synchronized String get(String action, int index) {
        List<String> values=bindings.get(command(action));
        return values == null || index<0 || index>=values.size() ? null : values.get(index);
    }
    public synchronized String owner(String value) {
        String wanted=key(value);
        for (var entry : bindings.entrySet()) if (entry.getValue().contains(wanted)) return entry.getKey();
        return null;
    }
    public synchronized boolean add(String action, String value, boolean primary) {
        String name=command(action), bound=key(value);
        String previous=owner(bound);
        if (name.equals(previous) && (!primary || bound.equals(get(name,0)))) return true;
        if (previous != null) bindings.get(previous).remove(bound);
        List<String> values=bindings.computeIfAbsent(name, ignored -> new ArrayList<>());
        if (primary && !values.isEmpty()) values.set(0,bound); else values.add(bound);
        dirty=true; return true;
    }
    public synchronized boolean remove(String action) {
        List<String> values=bindings.get(command(action));
        if (values==null || values.isEmpty()) return false;
        values.remove(0); dirty=true; return true;
    }
    public synchronized void save(Path file) throws IOException {
        Path target=file.toAbsolutePath().normalize();
        if (source==null || !target.equals(source)) throw new IOException("KEYBINDS_TARGET_CHANGED: load the selected profile first");
        if (!Arrays.equals(snapshot,read(target))) throw new IOException("KEYBINDS_FILE_CHANGED: refusing to overwrite external changes");
        if (!dirty) { System.out.println("[client] KEYBINDS_PRESERVED unchanged=true file="+target); return; }
        if (otherCommands) throw new IOException("KEYBINDS_CUSTOM_COMMANDS: preserving original file; edit a bind-only file to save changes");
        StringBuilder text=new StringBuilder("// Saved by Wurm Android headless keybinding storage\n");
        comments.forEach(line -> text.append(line).append('\n'));
        bindings.forEach((action,keys) -> keys.forEach(bound -> text.append("bind ").append(bound).append(' ').append(action).append('\n')));
        byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length>LIMIT) throw new IOException("Keybindings exceed 1 MiB");
        Path backup=target.resolveSibling(target.getFileName()+".android-backup");
        if (!Files.exists(backup)) Files.copy(target,backup);
        Path pending=Files.createTempFile(target.getParent(),"keybindings-",".pending");
        try {
            try (FileOutputStream out=new FileOutputStream(pending.toFile())) { out.write(bytes); out.getFD().sync(); }
            Files.move(pending,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(pending); }
        snapshot=bytes; dirty=false;
        System.out.println("[client] KEYBINDS_SAVED keys="+keyCount()+" file="+target+" atomic=true");
    }
}
