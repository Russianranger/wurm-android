package client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned keyboard/mouse IPC boundary; renderer adapter is deliberately separate. */
public final class DesktopInput {
    public record Event(String kind, int code, double x, double y) {}
    private final Set<Integer> keys = new HashSet<>(), buttons = new HashSet<>();
    public synchronized Event accept(String line) {
        if (line.length() > 160) throw new IllegalArgumentException("Oversized input event");
        String[] p = line.split(" ");
        if (p.length == 3 && (p[0].equals("KEY") || p[0].equals("BUTTON"))) {
            int code = Integer.parseInt(p[1]); int down = Integer.parseInt(p[2]);
            if (code < 0 || code > (p[0].equals("KEY") ? 255 : 7) || (down != 0 && down != 1)) throw new IllegalArgumentException("Invalid input code");
            Set<Integer> held = p[0].equals("KEY") ? keys : buttons;
            if (down == 1) held.add(code); else held.remove(code);
            return new Event(p[0], code, down, 0);
        }
        if (p.length == 3 && p[0].equals("MOVE")) {
            double x = Double.parseDouble(p[1]), y = Double.parseDouble(p[2]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 1000 || Math.abs(y) > 1000) throw new IllegalArgumentException("Invalid mouse delta");
            return new Event("MOVE", 0, x, y);
        }
        if (p.length == 3 && p[0].equals("POINT")) {
            double x = Double.parseDouble(p[1]), y = Double.parseDouble(p[2]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
                throw new IllegalArgumentException("Invalid pointer position");
            return new Event("POINT", 0, x, y);
        }
        if (p.length == 2 && p[0].equals("WHEEL")) {
            int amount = Integer.parseInt(p[1]); if (Math.abs((long)amount) > 1200) throw new IllegalArgumentException("Invalid wheel");
            return new Event("WHEEL", 0, amount, 0);
        }
        if (line.equals("RESET")) { keys.clear(); buttons.clear(); return new Event("RESET", 0, 0, 0); }
        throw new IllegalArgumentException("Unknown input protocol");
    }
    public synchronized int heldCount() { return keys.size() + buttons.size(); }
    public static void diagnostic() throws IOException {
        DesktopInput input = new DesktopInput();
        System.out.println("[client] INPUT_READY protocol=1 sink=diagnostic; LWJGL adapter NOT attached");
        long received = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("STOP")) break;
                try {
                    Event e = input.accept(line); received++;
                    if (!e.kind().equals("MOVE") || received % 30 == 0)
                        System.out.println("[client] INPUT_RECEIVED " + e + " held=" + input.heldCount() + " sink=diagnostic");
                } catch (RuntimeException invalid) { System.out.println("[client] INPUT_REJECTED " + invalid.getClass().getSimpleName()); }
            }
        } finally { input.accept("RESET"); }
        System.out.println("[client] INPUT_CLOSED events=" + received + " held=" + input.heldCount());
    }
}
