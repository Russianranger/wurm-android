"""Authored SQL fixtures plus optional, private stock-server qualification."""
from pathlib import Path
import hashlib
import os
import sqlite3
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
STOCK = os.environ.get('WURM_TEST_STOCK_SERVER_JAR')
PREPARED = os.environ.get('WURM_TEST_SERVER_JAR')
SQLITE = os.environ.get('WURM_TEST_SQLITE_JAR')
JAVASSIST = os.environ.get('WURM_MOD_JAVASSIST_JAR')


class ServerItemSqlitePatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='wurm-items-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        source = cls.root/'ItemFixture.java'
        source.write_text('''package server;
import java.nio.file.*;
import java.sql.*;
import java.net.*;
public class ItemFixture {
 public static void main(String[] args) throws Exception {
  if(args[0].equals("prepare")) { ServerItemSqlitePatch.prepare(Path.of(args[1]),Path.of(args[2])); return; }
  if(args[0].equals("verify")) { ServerItemSqlitePatch.verifySelected(); return; }
  if(args[0].equals("sql")) {
   for(var r:ServerItemSqlitePatch.RULES) for(boolean damage:new boolean[]{false,true})
    System.out.println(r.sqlite(damage));
   return;
  }
  if(args[0].equals("transform")) {
   Class<?> poolType=Class.forName("javassist.ClassPool"), ctType=Class.forName("javassist.CtClass");
   Object pool=poolType.getMethod("getDefault").invoke(null);
   for(var r:ServerItemSqlitePatch.RULES) {
    Object ct=poolType.getMethod("get",String.class).invoke(pool,"com.wurmonline.server.items."+r.name());
    Object method=ctType.getMethod("getDeclaredMethod",String.class).invoke(ct,"setLastMaintained");
    Class.forName("javassist.CtBehavior").getMethod("insertAfter",String.class).invoke(method,"$_ = $_ + \\" /*fixture*/\\";");
   }
   ClassLoader loader=(ClassLoader)Class.forName("javassist.Loader").getConstructor(poolType).newInstance(pool);
   loader.loadClass("server.ServerItemSqlitePatch").getMethod("verifySelected").invoke(null);
   for(var r:ServerItemSqlitePatch.RULES) {
    Class<?> c=loader.loadClass("com.wurmonline.server.items."+r.name());
    var ctor=c.getDeclaredConstructor(); ctor.setAccessible(true);
    if(c.getClassLoader()!=loader || !c.getMethod("setLastMaintained").invoke(ctor.newInstance()).equals(r.sqlite(false)+" /*fixture*/")) throw new AssertionError("transforming loader SQL");
   }
   System.out.println("ITEM_TRANSFORMING_LOADER_PASS"); return;
  }
  // Load only the supplied SQL-holder interface/classes, not server/world startup.
  try(var original=new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()},ClassLoader.getPlatformClassLoader());
      var patched=new URLClassLoader(new URL[]{Path.of(args[2]).toUri().toURL(),Path.of(args[1]).toUri().toURL()},ClassLoader.getPlatformClassLoader());
      var db=DriverManager.getConnection("jdbc:sqlite::memory:")) {
   for(var r:ServerItemSqlitePatch.RULES) {
    String name="com.wurmonline.server.items."+r.name();
    var before=original.loadClass(name); var after=patched.loadClass(name);
    var a=before.getDeclaredConstructor(); a.setAccessible(true);
    var b=after.getDeclaredConstructor(); b.setAccessible(true);
    Object old=a.newInstance(), fixed=b.newInstance();
    for(var m:before.getDeclaredMethods()) {
     if(m.getReturnType()!=String.class || m.getParameterCount()!=0) continue;
     String expectedName=m.getName().equals("setDamage") || m.getName().equals("setLastMaintained") ? m.getName()+"Old" : m.getName();
     Object expected=before.getMethod(expectedName).invoke(old);
     Object actual=after.getMethod(m.getName()).invoke(fixed);
     if(!expected.equals(actual)) throw new AssertionError("SQL dispatch "+r.name()+"."+m.getName());
    }
    db.createStatement().execute("CREATE TABLE "+r.table()+" (WURMID BIGINT PRIMARY KEY, DAMAGE REAL, LASTMAINTAINED BIGINT, KEEP TEXT NOT NULL)");
    db.createStatement().execute("INSERT INTO "+r.table()+" VALUES (987654321012345, 1.25, 11, 'retained')");
    db.setAutoCommit(false);
    try(var m=db.prepareStatement((String)after.getMethod("setLastMaintained").invoke(fixed));
        var d=db.prepareStatement((String)after.getMethod("setDamage").invoke(fixed))) {
     m.setLong(1,123456789012345L); m.setLong(2,987654321012345L); m.addBatch(); m.executeBatch();
     d.setFloat(1,7.25f); d.setLong(2,123456789012346L); d.setLong(3,987654321012345L); d.addBatch(); d.executeBatch();
     try(var rows=db.createStatement().executeQuery("SELECT DAMAGE,LASTMAINTAINED,KEEP,rowid FROM "+r.table())) {
      if(!rows.next() || rows.getFloat(1)!=7.25f || rows.getLong(2)!=123456789012346L || !rows.getString(3).equals("retained") || rows.getLong(4)!=1) throw new AssertionError("bindings/preservation");
     }
    }
    db.rollback();
    try(var rows=db.createStatement().executeQuery("SELECT DAMAGE,LASTMAINTAINED FROM "+r.table())) {
     if(!rows.next() || rows.getFloat(1)!=1.25f || rows.getLong(2)!=11) throw new AssertionError("rollback");
    }
    db.setAutoCommit(true);
   }
  }
  System.out.println("ITEM_ACTUAL_DISPATCH_JDBC_PASS");
 }
}''')
        subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-d',str(cls.root),
            str(ROOT/'runtime-probe/src/server/ServerSqlitePatch.java'),
            str(ROOT/'runtime-probe/src/server/ServerItemSqlitePatch.java'),str(source)],check=True)
        cls.sql = cls.java('sql').stdout.splitlines()

    @classmethod
    def java(cls,*args,cp=(),props=()):
        return subprocess.run(['java',*props,'-cp',os.pathsep.join([*map(str,cp),str(cls.root)]),
            'server.ItemFixture',*map(str,args)],capture_output=True,text=True,timeout=30)

    def test_eight_personal_updates_preserve_rows_children_columns_and_rollback(self):
        self.assertEqual(len(self.sql),8)
        for sql in self.sql:
            with self.subTest(sql=sql), sqlite3.connect(':memory:') as db:
                table=sql.split()[1]
                db.execute('PRAGMA foreign_keys=ON')
                db.execute('PRAGMA recursive_triggers=ON')
                db.execute(f'CREATE TABLE {table} (WURMID BIGINT PRIMARY KEY, DAMAGE REAL, LASTMAINTAINED BIGINT, KEEP TEXT NOT NULL)')
                db.execute(f'CREATE TABLE CHILD (OWNER BIGINT REFERENCES {table}(WURMID) ON DELETE CASCADE)')
                db.execute(f'CREATE TRIGGER no_delete BEFORE DELETE ON {table} BEGIN SELECT RAISE(ABORT,"delete forbidden"); END')
                db.execute(f'INSERT INTO {table} VALUES (123456789012345,1.25,11,"retained")')
                db.execute('INSERT INTO CHILD VALUES (123456789012345)')
                db.commit()
                values=((7.25,987654321012345) if 'DAMAGE=?' in sql else (987654321012345,))+(123456789012345,)
                self.assertEqual(db.execute(sql,values).rowcount,1)
                self.assertEqual(db.execute(f'SELECT rowid,KEEP,DAMAGE,LASTMAINTAINED FROM {table}').fetchone(),
                    (1,'retained',7.25 if 'DAMAGE=?' in sql else 1.25,987654321012345))
                self.assertEqual(db.execute('SELECT count(*) FROM CHILD').fetchone()[0],1)
                # Match the personal-server update contract: a missing item is not recreated.
                self.assertEqual(db.execute(sql,values[:-1]+(-99,)).rowcount,0)
                db.rollback()
                self.assertEqual(db.execute(f'SELECT DAMAGE,LASTMAINTAINED FROM {table}').fetchone(),(1.25,11))

    def test_unknown_classes_and_missing_overlay_fail_without_publication(self):
        source=self.root/'unknown.jar'; target=self.root/'unknown-overlay.jar'
        with zipfile.ZipFile(source,'w') as z:
            for name in ('ItemDbStrings','BodyDbStrings','CoinDbStrings','FrozenItemDbStrings'):
                z.writestr('com/wurmonline/server/items/'+name+'.class',b'authored unknown input')
        result=self.java('prepare',source,target)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('SERVER_ITEM_PATCH_UNSUPPORTED',result.stderr)
        self.assertFalse(target.exists())
        self.assertFalse(target.with_suffix('.jar.pending').exists())
        self.assertNotEqual(self.java('verify',props=[f'-Dwurm.server.itemsOverlay={target}']).returncode,0)

    @unittest.skipUnless(STOCK,'Optional owner-supplied original server.jar')
    def test_stock_overlay_identity_selection_and_unchanged_original(self):
        before=Path(STOCK).read_bytes()
        self.assertEqual(hashlib.sha256(before).hexdigest(),'ba5301b2e9b56dc9ab7eae9d8ac45188336835e37184e329c90128ea8ab01f64')
        target=self.root/'stock-overlay.jar'
        result=self.java('prepare',STOCK,target)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(Path(STOCK).read_bytes(),before)
        with zipfile.ZipFile(target) as z:
            self.assertEqual(len(z.namelist()),4)
            for name in z.namelist(): self.assertNotIn(b'ON DUPLICATE KEY UPDATE',z.read(name))
        prop=[f'-Dwurm.server.itemsOverlay={target}']
        result=self.java('verify',cp=[target,STOCK],props=prop)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout.count('ITEM_PATCH_ACTIVE'),4)
        result=self.java('verify',cp=[STOCK,target],props=prop)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('SERVER_ITEM_PATCH_NOT_SELECTED',result.stderr)
        with zipfile.ZipFile(target,'a') as z: z.writestr('extra','invalid')
        self.assertNotEqual(self.java('verify',cp=[target,STOCK],props=prop).returncode,0)

    @unittest.skipUnless(STOCK and SQLITE,'Optional original server and pinned SQLite JDBC')
    def test_actual_sql_methods_and_jdbc_batches_match_personal_server(self):
        target=self.root/'jdbc-overlay.jar'
        result=self.java('prepare',STOCK,target)
        self.assertEqual(result.returncode,0,result.stderr)
        result=self.java('actual',STOCK,target,cp=[SQLITE])
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('ITEM_ACTUAL_DISPATCH_JDBC_PASS',result.stdout)

    @unittest.skipUnless(STOCK and JAVASSIST,'Optional stock server and pinned mod Javassist')
    def test_transforming_loader_can_modify_generated_item_classes(self):
        target=self.root/'mod-overlay.jar'
        self.assertEqual(hashlib.sha256(Path(JAVASSIST).read_bytes()).hexdigest(),'eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf')
        result=self.java('prepare',STOCK,target)
        self.assertEqual(result.returncode,0,result.stderr)
        result=self.java('transform',cp=[target,STOCK,JAVASSIST],props=[f'-Dwurm.server.itemsOverlay={target}'])
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('ITEM_TRANSFORMING_LOADER_PASS',result.stdout)

    @unittest.skipUnless(PREPARED,'Optional owner-supplied working prepared server.jar')
    def test_previous_runtime_uses_unchanged_item_classes(self):
        target=self.root/'prepared-overlay.jar'
        result=self.java('prepare',PREPARED,target)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('mode=prepared-preserved',result.stdout)
        with zipfile.ZipFile(target) as z: self.assertEqual(z.namelist(),[])
        result=self.java('verify',cp=[target,PREPARED],props=[f'-Dwurm.server.itemsOverlay={target}'])
        self.assertEqual(result.returncode,0,result.stderr)
