"""Process observation using synthetic proc data and this host JVM; no Wurm files."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("java"), "JDK 17 needed")
class WorldProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix="wurm-world-probe-")
        cls.classes = Path(cls.tmp.name)
        harness = cls.classes / "WorldProbeHarness.java"
        harness.write_text('''package server;
import java.nio.file.*;
public class WorldProbeHarness {
    public static void main(String[] args) throws Exception {
        if (args.length == 2) WorldProbe.inspect(Path.of(args[0]), Path.of(args[1]));
        else {
            Path root = Path.of(".").toRealPath();
            Path map = root.resolve("Adventure/top_layer.map");
            Files.createDirectories(map.getParent()); Files.writeString(map, "synthetic map");
            try (var open = java.nio.channels.FileChannel.open(map);
                 var socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
                System.out.println("EXPECTED_PORT=" + socket.getLocalPort());
                WorldProbe.capture();
                if (open.size() != 13) throw new AssertionError("source changed");
            }
        }
    }
}''')
        subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", str(cls.classes),
                        str(ROOT / "runtime-probe/src/server/WorldProbe.java"), str(harness)], check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def invoke(self, root, proc=None):
        args = [str(root), str(proc)] if proc is not None else []
        result = subprocess.run(["java", "-cp", str(self.classes), "server.WorldProbeHarness", *args],
                                cwd=root, capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_filters_foreign_sockets_and_paths_and_decodes_ipv4_ipv6(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            proc = root / "proc"
            (proc / "fd").mkdir(parents=True)
            (proc / "net").mkdir()
            for fd, target in enumerate([str(root / "Adventure/top_layer.map"), str(root / "localhost/sqlite/wurmitems.db"),
                                        str(root / "private-password.txt"), "/outside/secret.db", "socket:[123]", "socket:[456]"]):
                (proc / "fd" / str(fd)).symlink_to(target)
            (proc / "maps").write_text(f"1-2 rw-s 0 00:00 1 {root}/Adventure/map_cave.map\n")
            def row(address, state, inode):
                return f"0: {address} 00000000:0000 {state} 0:0 0:0 0 1000 0 {inode}\n"
            (proc / "net/tcp").write_text(row("0100007F:0E8C", "0A", 123) + row("00000000:1234", "0A", 999) +
                                          row("0100007F:4321", "01", 123))
            (proc / "net/tcp6").write_text(row("00000000000000000000000001000000:6988", "0A", 456))
            out = self.invoke(root, proc)
            self.assertIn("OPEN_FILE Adventure/top_layer.map", out)
            self.assertIn("OPEN_FILE localhost/sqlite/wurmitems.db", out)
            self.assertIn("MAPPED_FILE Adventure/map_cave.map", out)
            self.assertIn("TCP_LISTEN tcp [127.0.0.1]:3724", out)
            self.assertIn("TCP_LISTEN tcp6 [0:0:0:0:0:0:0:1]:27016", out)
            self.assertNotIn("secret", out)
            self.assertNotIn("password", out)
            self.assertNotIn(":4660", out)
            self.assertNotIn(":17185", out)

    def test_unavailable_proc_is_explicit_not_no_listeners_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = self.invoke(Path(tmp), Path(tmp) / "missing")
            self.assertIn("FD_UNAVAILABLE", out)
            self.assertIn("MAPS_UNAVAILABLE", out)
            self.assertIn("TCP_UNAVAILABLE tcp ", out)
            self.assertNotIn("TCP_LISTEN", out)
            self.assertNotIn("TCP_SCAN", out)

    def test_real_child_observes_own_open_file_and_loopback_listener(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = self.invoke(Path(tmp))
            port = out.split("EXPECTED_PORT=", 1)[1].splitlines()[0]
            self.assertIn("OPEN_FILE Adventure/top_layer.map", out)
            self.assertIn(f"]:{port}", out)
            self.assertIn("SNAPSHOT_END", out)


if __name__ == "__main__":
    unittest.main()
