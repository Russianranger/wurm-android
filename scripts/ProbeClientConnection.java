package client;

import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Developer-only real-JAR ABI/loopback fixture. No Wurm server or world is simulated as a success. */
public final class ProbeClientConnection {
    private static final class Holder { private static Object clientObject; }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field f = current.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type);
    }
    private static void expect(ClientConnectionMonitor monitor, String expected) throws Exception {
        var sample = monitor.sample();
        if (!sample.phase().equals(expected)) throw new AssertionError(sample);
        System.out.println("REAL_CONNECTION_FIXTURE " + sample.line(0));
    }
    private static ByteBuffer response(boolean accepted, String message) {
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(3 + text.length).put((byte)(accepted ? 1 : 0)).putShort((short)text.length).put(text).flip();
    }
    public static void main(String[] args) throws Exception {
        Class<?> engine = Class.forName("com.wurmonline.client.WurmClientBase");
        Object steam = Class.forName("wurm.android.compat.ClientHooks").getMethod("initializeSteam").invoke(null);
        engine.getField("steamHandler").set(null, steam);
        Class<?> comm = Class.forName("com.wurmonline.client.comm.SimpleServerConnectionClass");
        Object connection = comm.getConstructor(Class.forName("com.wurmonline.client.comm.ServerConnectionListenerClass")).newInstance(new Object[]{null});
        Object client = allocate(engine); Holder.clientObject = client;
        field(engine, "serverConnection").set(client, connection);
        Object splash = allocate(Class.forName("com.wurmonline.client.startup.splash.StartupRenderer"));
        field(engine, "startupRenderer").set(client, splash);
        field(splash.getClass(), "startupMessage").set(splash, "Fixture local handshake");
        ClientConnectionMonitor monitor = new ClientConnectionMonitor(Holder.class, Thread.currentThread(), System.out::println);
        Class<?> transportType = Class.forName("com.wurmonline.client.comm.SimpleServerConnectionClass$SocketConnection2");
        Constructor<?> ctor = transportType.getDeclaredConstructor(String.class, int.class); ctor.setAccessible(true);
        Class<?> socketType = transportType.getSuperclass();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Object transport = ctor.newInstance("127.0.0.1", listener.getLocalPort());
            try (Socket peer = listener.accept()) {
                peer.setSoTimeout(3000); field(comm, "connection").set(connection, transport);
                comm.getMethod("sendSteamAuthTicket", boolean.class).invoke(connection, false);
                expect(monitor, "AUTH_WAIT");
                // The original writer swaps its two buffers on one tick, drains on the next.
                for (int i = 0; i < 20 && (Integer)socketType.getMethod("getUnflushed").invoke(transport) > 0; i++)
                    socketType.getMethod("tickWriting", long.class).invoke(transport, 0L);
                int payload = (Integer) socketType.getMethod("getSentBytes").invoke(transport);
                int received = peer.getInputStream().readNBytes(payload + 2).length;
                if (payload <= 0 || received != payload + 2) throw new AssertionError("Ticket payload did not reach fixture socket");
                System.out.println("REAL_TICKET_WIRE_PASS payload=" + payload + " framedBytes=" + received + "; fixture receiver only, no real server authentication");
                Method auth = comm.getDeclaredMethod("reallyHandleCmdSteamAuth", ByteBuffer.class); auth.setAccessible(true);
                auth.invoke(connection, response(false, "Fixture authentication denied"));
                expect(monitor, "AUTH_DENIED");
                auth.invoke(connection, response(true, ""));
                comm.getMethod("setLoginInfo", String.class, String.class, String.class, boolean.class).invoke(connection, "Fixture", "", "", false);
                comm.getMethod("login", String.class).invoke(connection, "76561198000000001");
                expect(monitor, "LOGIN_WAIT");
                int before = (Integer) socketType.getMethod("getUnflushed").invoke(transport);
                monitor.sample();
                if ((Integer) socketType.getMethod("getUnflushed").invoke(transport) != before) throw new AssertionError("Observer consumed outgoing login");
                if ((Boolean) comm.getMethod("isLoggedIn").invoke(connection)) throw new AssertionError("Fixture falsely established login");
                System.out.println("REAL_CONNECTION_MONITOR_PASS original ticket/parser/login methods; read-only observation; world/login acceptance NOT tested");
            } finally { socketType.getMethod("disconnect").invoke(transport); }
        }
        System.exit(0);
    }
}
