package probe;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise native networking without Wurm classes or world files. */
public final class NetworkProbe {
    public static void main(String[] args) throws Exception { run(); }

    public static void run() throws Exception {
        System.out.println("[network] ENUMERATE_BEGIN");
        try {
            int count = 0;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface item = interfaces.nextElement();
                item.getHardwareAddress();
                item.isUp();
                item.getMTU();
                // Exercise address creation/free without logging local addresses.
                Enumeration<InetAddress> addresses = item.getInetAddresses();
                while (addresses.hasMoreElements()) addresses.nextElement().getAddress();
                count++;
            }
            System.out.println("[network] ENUMERATE_OK count=" + count);
        } catch (SocketException | SecurityException unavailable) {
            // Ordinary Android restrictions are reported independently of the
            // required loopback test. A native abort cannot be caught here.
            System.out.println("[network] ENUMERATE_UNAVAILABLE: " + unavailable);
        }

        System.out.println("[network] LOCALHOST_BEGIN");
        boolean loopbackResolved = false;
        for (InetAddress address : InetAddress.getAllByName("localhost"))
            loopbackResolved |= address.isLoopbackAddress();
        if (!loopbackResolved) throw new IOException("localhost did not resolve to loopback");
        System.out.println("[network] LOCALHOST_OK");

        System.out.println("[network] LOOPBACK_BEGIN");
        InetAddress local = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, local)) {
            server.setSoTimeout(3000);
            Thread responder = new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    accepted.setSoTimeout(3000);
                    if (accepted.getInputStream().read() != 37) throw new IOException("Wrong test request");
                    accepted.getOutputStream().write(38);
                    accepted.getOutputStream().flush();
                } catch (Throwable failure) { serverFailure.set(failure); }
            }, "probe-loopback");
            responder.setDaemon(true);
            responder.start();
            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(local, server.getLocalPort()), 3000);
                client.setSoTimeout(3000);
                client.getOutputStream().write(37);
                client.getOutputStream().flush();
                if (client.getInputStream().read() != 38) throw new IOException("Wrong test response");
            }
            responder.join(3500);
            if (responder.isAlive()) throw new IOException("Loopback thread did not exit");
            if (serverFailure.get() != null) throw new IOException("Loopback responder failed", serverFailure.get());
        }
        System.out.println("[network] NETWORK_OK: localhost resolution and TCP loopback exchange");
    }
}
