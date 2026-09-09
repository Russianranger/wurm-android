"""Authored ABI fixtures: observe handshake/retry state without changing it or exposing credentials."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ClientConnectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.home = Path(cls.temp.name)
        source = cls.home/'ConnectionFixture.java'
        source.write_text('''package client;
import java.nio.ByteBuffer;
import java.util.*;
public class ConnectionFixture {
 static class Engine { private static Client clientObject; }
 static class Client { private Splash startupRenderer; private Connection serverConnection; }
 static class Splash { private String startupMessage="Waiting for Steam authentication..."; private boolean isReconnect; }
 static class Connection {
  private boolean isConnecting,steamAuthenticateSucces,loggedIn,disconnected;
  private String steamAuthenticateFailedMessage="",loginStatus="",disconnectReason="";
  private final String password="NEVER_REPORT_PASSWORD",ticket="NEVER_REPORT_TICKET";
  private Transport connection;
 }
 static class BaseTransport {
  private boolean connected=true,writing;
  private int totalBytesWritten=73,bytesRead=17;
  private ByteBuffer writeBuffer_w=ByteBuffer.allocate(20).position(7),writeBuffer_r=ByteBuffer.allocate(3);
 }
 static class Transport extends BaseTransport { }
 static void expect(ClientConnectionMonitor monitor,String phase) throws Exception {
  var sample=monitor.sample();
  if(!sample.phase().equals(phase))throw new AssertionError(sample);
  System.out.println(sample.line(1000));
 }
 public static void main(String[] args) throws Exception {
  List<String> lines=Collections.synchronizedList(new ArrayList<>());
  if(args[0].equals("unavailable")) {
   java.util.concurrent.CountDownLatch hold=new java.util.concurrent.CountDownLatch(1);
   Thread game=new Thread(()->{try{hold.await();}catch(InterruptedException e){}});game.start();
   try(var monitor=ClientConnectionMonitor.start(Object.class,game)) {
    var field=ClientConnectionMonitor.class.getDeclaredField("observer");field.setAccessible(true);
    Thread observer=(Thread)field.get(monitor);observer.join(3000);
    if(observer.isAlive())throw new AssertionError("ABI failure did not finish observer");
    if(!game.isAlive())throw new AssertionError("observer killed game");
   } finally {hold.countDown();game.join();}
   return;
  }
  var monitor=new ClientConnectionMonitor(Engine.class,Thread.currentThread(),lines::add);
  expect(monitor,"INITIALIZING");
  Client client=new Client();Engine.clientObject=client;client.startupRenderer=new Splash();
  expect(monitor,"INITIALIZING");
  Connection connection=new Connection();client.serverConnection=connection;expect(monitor,"SOCKET_CONNECTING");
  connection.connection=new Transport();connection.isConnecting=true;expect(monitor,"AUTH_WAIT");
  connection.steamAuthenticateSucces=true;expect(monitor,"LOGIN_WAIT");
  connection.isConnecting=false;connection.loginStatus="fixture login rejected";expect(monitor,"LOGIN_DENIED");
  connection.loginStatus="";connection.steamAuthenticateSucces=false;
  connection.steamAuthenticateFailedMessage="fixture auth rejected";expect(monitor,"AUTH_DENIED");
  client.startupRenderer.isReconnect=true;expect(monitor,"RETRY_WAIT");
  connection.disconnected=true;connection.disconnectReason="fixture disconnect";expect(monitor,"DISCONNECTED");
  connection.disconnected=false;client.startupRenderer.isReconnect=false;
  connection.steamAuthenticateSucces=true;connection.loggedIn=true;expect(monitor,"LOGIN_ACCEPTED");
  client.startupRenderer=null;expect(monitor,"GAME_LOOP");
  BaseTransport transport=connection.connection;
  if(transport.writeBuffer_w.position()!=7||transport.writeBuffer_r.remaining()!=3||transport.totalBytesWritten!=73||
      transport.bytesRead!=17||transport.writing)throw new AssertionError("observer mutated buffers/counters");
  if(!connection.steamAuthenticateSucces||!connection.loggedIn||connection.isConnecting)throw new AssertionError("observer mutated auth state");
  if(args[0].equals("text")) {
   connection.loginStatus="first\\nsecond\\rthird\\t"+"x".repeat(5000);
   String text=monitor.sample().detail();
   if(text.contains("\\n")||text.contains("\\r")||text.contains("\\t")||text.length()>1000)throw new AssertionError(text);
   System.out.println("BOUNDED_TEXT_PASS");
  }
  System.out.println("READ_ONLY_PASS pendingBytes=10 payloadQueued=73 bytesRead=17");
 }
}''')
        result = subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(cls.home),
            str(ROOT/'runtime-probe/src/client/ClientConnectionMonitor.java'), str(source)], capture_output=True, text=True)
        if result.returncode: raise AssertionError(result.stdout+result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def run_fixture(self, mode):
        result = subprocess.run(['java', '-cp', str(self.home), 'client.ConnectionFixture', mode],
            capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertNotIn('NEVER_REPORT_', result.stdout)
        return result.stdout

    def test_realistic_state_transitions_do_not_change_connection_or_buffers(self):
        text = self.run_fixture('states')
        for phase in ['INITIALIZING', 'SOCKET_CONNECTING', 'AUTH_WAIT', 'LOGIN_WAIT', 'LOGIN_DENIED',
                      'AUTH_DENIED', 'RETRY_WAIT', 'DISCONNECTED', 'LOGIN_ACCEPTED', 'GAME_LOOP']:
            self.assertIn('phase='+phase+' ', text)
        self.assertIn('READ_ONLY_PASS', text)
        self.assertIn('pendingBytes=10', text)
        self.assertIn('startup=Waiting for Steam authentication...', text)
        self.assertIn('loginMessage=fixture login rejected', text)

    def test_server_text_is_bounded_and_cannot_inject_new_log_lines(self):
        self.assertIn('BOUNDED_TEXT_PASS', self.run_fixture('text'))

    def test_missing_abi_stops_only_observer_and_is_reported(self):
        text = self.run_fixture('unavailable')
        self.assertIn('MONITOR_UNAVAILABLE java.lang.NoSuchFieldException', text)
        self.assertIn('MONITOR_STOPPED', text)


if __name__ == '__main__':
    unittest.main()
