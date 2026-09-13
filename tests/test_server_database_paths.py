"""Database path regression: authored SQLite fixtures and optional private Wurm connection factory."""
from pathlib import Path
import hashlib
import os
import sqlite3
import subprocess
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
STOCK=os.environ.get('WURM_TEST_STOCK_SERVER_JAR')
ARCHIVE=os.environ.get('WURM_TEST_STOCK_ZIP')
SQLITE=os.environ.get('WURM_TEST_SQLITE_JAR')
NAMES=['creatures','deities','economy','items','login','logs','players','templates','zones']

class ServerDatabasePathsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(prefix='wurm-database-paths-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root=Path(cls.temp.name)
        cls.classes=cls.root/'classes'
        source=cls.root/'DatabaseFixture.java'
        source.write_text('''package server;
import java.nio.file.*;
import java.util.*;
import java.sql.*;
public class DatabaseFixture {
 public static void main(String[] args) throws Exception {
  Path runtime=Path.of(args[1]); String world=args[2];
  if(args[0].equals("verify")) { ServerDatabasePreflight.verify(runtime,world); return; }
  Properties p=new Properties();
  try(var in=Files.newInputStream(runtime.resolve(world).resolve("wurm.ini"))) { p.load(in); }
  Class<?> configType=Class.forName("org.sqlite.SQLiteConfig");
  Object config=configType.getConstructor().newInstance();
  Class<?> schemaType=Class.forName("com.wurmonline.server.database.WurmDatabaseSchema");
  Class<?> factoryType=Class.forName("com.wurmonline.server.database.SqliteConnectionFactory");
  int count=0;
  for(Object schema:schemaType.getEnumConstants()) {
   Object factory=factoryType.getConstructor(String.class,schemaType,configType).newInstance(p.getProperty("DB_HOST"),schema,config);
   try(Connection c=(Connection)factoryType.getMethod("createConnection").invoke(factory);
       var rows=c.createStatement().executeQuery("SELECT count(*) FROM sqlite_master")) {
    if(!rows.next() || rows.getInt(1)==0) throw new AssertionError("empty database");
    Path actual=((Path)factoryType.getMethod("getFilePath").invoke(factory)).toRealPath();
    if(!actual.startsWith(runtime.resolve(world).toRealPath())) throw new AssertionError("wrong world's database");
    count++;
   }
  }
  if(count!=9) throw new AssertionError("database count");
  System.out.println("ACTUAL_WURM_DATABASE_CONNECTIONS_OK count="+count);
 }
}''')
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.classes),
            str(ROOT/'runtime-probe/src/server/ServerDatabasePreflight.java'),str(source)],check=True)

    def fixture(self,host='Adventure'):
        runtime=Path(tempfile.mkdtemp(dir=self.root,prefix='runtime-'))
        world=runtime/'Adventure';(world/'sqlite').mkdir(parents=True)
        (world/'wurm.ini').write_text('DB_HOST='+host+'\n')
        for name in NAMES:
            with sqlite3.connect(world/'sqlite'/f'wurm{name}.db') as db:
                db.execute('CREATE TABLE fixture (value TEXT)')
        return runtime

    def java(self,runtime,mode='verify',world='Adventure',cp=()):
        return subprocess.run(['java','-cp',os.pathsep.join([str(self.classes),*map(lambda p:str(Path(p).resolve()),cp)]),
            'server.DatabaseFixture',mode,str(runtime),world],cwd=runtime,text=True,capture_output=True,timeout=30)

    def test_default_localhost_failure_is_reported_without_creating_databases(self):
        runtime=self.fixture('localhost')
        before={p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()}
        r=self.java(runtime)
        self.assertNotEqual(r.returncode,0)
        self.assertIn('WORLD_DATABASE_PATH_FAILED: missing localhost/sqlite',r.stderr)
        self.assertFalse((runtime/'localhost').exists())
        self.assertEqual(before,{p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()})

    def test_selected_world_and_real_shared_directory_pass_without_file_changes(self):
        for shared in [False,True]:
            runtime=self.fixture()
            if shared:
                (runtime/'Adventure/sqlite').rename(runtime/'sqlite')
                (runtime/'localhost').mkdir()
                (runtime/'sqlite').rename(runtime/'localhost/sqlite')
                (runtime/'Adventure/wurm.ini').write_text('DB_HOST=localhost\n')
            before={p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()}
            r=self.java(runtime)
            self.assertEqual(r.returncode,0,r.stderr)
            self.assertIn('WORLD_DATABASE_PATHS_OK',r.stdout)
            self.assertEqual(before,{p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*') if p.is_file()})

    def test_missing_corrupt_external_and_linked_files_fail(self):
        for mode in ['missing','corrupt','external','link']:
            runtime=self.fixture();file=runtime/'Adventure/sqlite/wurmitems.db'
            if mode=='missing':file.unlink()
            elif mode=='corrupt':file.write_bytes(bytes(512))
            elif mode=='external':(runtime/'Adventure/wurm.ini').write_text('DB_HOST=../other\n')
            else:
                file.rename(file.with_suffix('.original'));file.symlink_to(file.with_suffix('.original'))
            self.assertNotEqual(self.java(runtime).returncode,0,mode)

    @unittest.skipUnless(STOCK and SQLITE,'Optional original Wurm JAR and pinned SQLite JDBC')
    def test_actual_factory_reproduces_failure_and_connects_all_nine_after_host_correction(self):
        runtime=self.fixture('localhost')
        before={p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*.db')}
        r=self.java(runtime,'actual',cp=[SQLITE,STOCK])
        self.assertNotEqual(r.returncode,0)
        self.assertIn('SQLITE_CANTOPEN',r.stderr)
        (runtime/'Adventure/wurm.ini').write_text('DB_HOST=Adventure\n')
        r=self.java(runtime,'actual',cp=[SQLITE,STOCK])
        self.assertEqual(r.returncode,0,r.stderr)
        self.assertIn('ACTUAL_WURM_DATABASE_CONNECTIONS_OK count=9',r.stdout)
        self.assertEqual(before,{p.relative_to(runtime):p.read_bytes() for p in runtime.rglob('*.db')})

    @unittest.skipUnless(STOCK and SQLITE and ARCHIVE,'Optional original server archive/JAR and pinned SQLite JDBC')
    def test_original_worlds_open_their_own_supplied_databases(self):
        runtime=Path(tempfile.mkdtemp(dir=self.root,prefix='stock-'))
        with zipfile.ZipFile(ARCHIVE) as z:
            for world in ['Adventure','Creative']:
                (runtime/world/'sqlite').mkdir(parents=True)
                (runtime/world/'wurm.ini').write_bytes(z.read(f'WurmServerLauncher/dist/{world}/wurm.ini').replace(b'\nDB_HOST=localhost',('\nDB_HOST='+world).encode()))
                for name in NAMES:
                    (runtime/world/'sqlite'/f'wurm{name}.db').write_bytes(z.read(f'WurmServerLauncher/dist/{world}/sqlite/wurm{name}.db'))
        before={p.relative_to(runtime):hashlib.sha256(p.read_bytes()).hexdigest() for p in runtime.rglob('*.db')}
        for world in ['Adventure','Creative']:
            self.assertEqual(self.java(runtime,world=world).returncode,0)
            r=self.java(runtime,'actual',world,cp=[SQLITE,STOCK])
            self.assertEqual(r.returncode,0,r.stderr)
            self.assertIn('ACTUAL_WURM_DATABASE_CONNECTIONS_OK count=9',r.stdout)
        self.assertEqual(before,{p.relative_to(runtime):hashlib.sha256(p.read_bytes()).hexdigest() for p in runtime.rglob('*.db')})
