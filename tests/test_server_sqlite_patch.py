"""Authored SQL/class fixtures; optional private validation against the owner's supplied server JAR."""
from pathlib import Path
import hashlib
import os
import sqlite3
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SERVER = os.environ.get('WURM_TEST_SERVER_JAR')


class ServerSqlitePatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix='wurm-position-patch-')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.root = Path(cls.temp.name)
        source = cls.root / 'server/PatchFixture.java'
        source.parent.mkdir()
        source.write_text('''package server;
import java.nio.file.*;
public class PatchFixture {
 public static void main(String[] args) throws Exception {
  if (args[0].equals("sql")) { System.out.print(ServerSqlitePatch.SQLITE); return; }
  if (args[0].equals("prepare")) { ServerSqlitePatch.prepare(Path.of(args[1]),Path.of(args[2])); return; }
  if (args[0].equals("verify")) { ServerSqlitePatch.verifySelected(); return; }
  byte[] original=Files.readAllBytes(Path.of(args[1]));
  String hash=ServerSqlitePatch.sha(original);
  byte[] patched=ServerSqlitePatch.redirect(original,hash,"fixture old SQL","fixture new SQL");
  class Loader extends ClassLoader { Class<?> load(byte[] b) { return defineClass(null,b,0,b.length); } }
  Class<?> before=new Loader().load(original), after=new Loader().load(patched);
  if (!before.getMethod("sql").invoke(null).equals("fixture old SQL") ||
      !after.getMethod("sql").invoke(null).equals("fixture new SQL")) throw new AssertionError("constant dispatch");
  for (String m : new String[]{"number","text"})
   if (!before.getMethod(m).invoke(null).equals(after.getMethod(m).invoke(null))) throw new AssertionError("unrelated data");
  byte[] restored=ServerSqlitePatch.redirect(patched,ServerSqlitePatch.sha(patched),"fixture new SQL","fixture old SQL");
  if (!java.util.Arrays.equals(original,restored)) throw new AssertionError("reverse mismatch");
  for (String[] rule : new String[][]{{"wrong hash","fixture old SQL"},{hash,"missing SQL"}}) {
   try { ServerSqlitePatch.redirect(original,rule[0],rule[1],"new"); throw new AssertionError("unguarded patch"); }
   catch (java.io.IOException expected) { }
  }
  System.out.println("POSITION_PATCH_CONTRACT_PASS");
 }
}''')
        fixture = cls.root / 'Fixture.java'
        fixture.write_text('''public class Fixture {
 public static String sql() { return "fixture old SQL"; }
 public static long number() { return 123456789012345L; }
 public static String text() { return "untouched \\u0000 \\ud83d\\ude00"; }
}''')
        subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '17', '-d', str(cls.root),
            str(ROOT/'runtime-probe/src/server/ServerSqlitePatch.java'), str(source), str(fixture)], check=True)
        cls.sql = cls.run_java('sql').stdout

    @classmethod
    def run_java(cls, *args, extra_cp=(), properties=()):
        return subprocess.run(['java', *properties, '-cp', os.pathsep.join([*map(str,extra_cp), str(cls.root)]),
            'server.PatchFixture', *map(str,args)], text=True, capture_output=True, timeout=20)

    def test_constant_only_patch_preserves_code_and_rejects_unknown_input(self):
        result = self.run_java('contract', self.root/'Fixture.class')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('POSITION_PATCH_CONTRACT_PASS', result.stdout)

    def test_real_sqlite_insert_update_and_rollback_preserve_row_and_other_columns(self):
        with sqlite3.connect(':memory:') as db:
            db.execute('PRAGMA foreign_keys=ON')
            db.execute('PRAGMA recursive_triggers=ON')
            db.execute('CREATE TABLE POSITION (WURMID BIGINT PRIMARY KEY, POSX REAL, POSY REAL, POSZ REAL, ROTATION REAL, ZONEID INT, LAYER INT, ONBRIDGE BIGINT, KEEP TEXT DEFAULT "unchanged")')
            db.execute('CREATE TABLE CHILD (OWNER BIGINT REFERENCES POSITION(WURMID) ON DELETE CASCADE)')
            db.execute('CREATE TRIGGER no_delete BEFORE DELETE ON POSITION BEGIN SELECT RAISE(ABORT,"delete forbidden"); END')
            initial = (1.25,2.5,-3.75,90.0,42,-1,123456789012345,987654321012345)
            db.execute(self.sql, initial)
            db.execute('INSERT INTO CHILD VALUES (?)', (initial[7],))
            db.execute('UPDATE POSITION SET KEEP="retained"')
            identity = db.execute('SELECT rowid FROM POSITION').fetchone()[0]
            db.commit()
            changed = (4.25,5.5,-6.75,180.0,43,0,-10,initial[7])
            db.execute(self.sql, changed)
            row = db.execute('SELECT POSX,POSY,POSZ,ROTATION,ZONEID,LAYER,ONBRIDGE,WURMID,KEEP,rowid FROM POSITION').fetchone()
            self.assertEqual(row, changed + ('retained', identity))
            self.assertEqual(db.execute('SELECT count(*) FROM CHILD').fetchone()[0], 1)
            db.rollback()
            self.assertEqual(db.execute('SELECT POSX FROM POSITION').fetchone()[0], initial[0])

    def test_production_rejects_uninspected_class_and_missing_overlay(self):
        source, target = self.root/'unknown.jar', self.root/'unknown-overlay.jar'
        with zipfile.ZipFile(source,'w') as z:
            z.write(self.root/'Fixture.class','com/wurmonline/server/creatures/CreaturePos.class')
        result = self.run_java('prepare', source, target)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('SERVER_SQLITE_PATCH_UNSUPPORTED', result.stderr)
        self.assertFalse(target.exists())
        result = self.run_java('verify', properties=[f'-Dwurm.server.sqliteOverlay={target}'])
        self.assertNotEqual(result.returncode, 0)

    @unittest.skipUnless(SERVER, 'Optional: set WURM_TEST_SERVER_JAR to legally owned pinned server.jar')
    def test_supplied_server_overlay_selects_exact_class_and_keeps_item_patches(self):
        original = Path(SERVER).read_bytes()
        self.assertEqual(hashlib.sha256(original).hexdigest(),'9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06')
        target = self.root/'actual-overlay.jar'
        result = self.run_java('prepare', SERVER, target)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(Path(SERVER).read_bytes(), original)
        entry = 'com/wurmonline/server/creatures/CreaturePos.class'
        with zipfile.ZipFile(target) as z, zipfile.ZipFile(SERVER) as source:
            self.assertEqual(z.namelist(), [entry])
            self.assertIn(b'ON CONFLICT DO UPDATE', z.read(entry))
            for name in ('ItemDbStrings','FrozenItemDbStrings','CoinDbStrings','BodyDbStrings'):
                self.assertNotIn(b'ON DUPLICATE KEY UPDATE',source.read('com/wurmonline/server/items/'+name+'.class'))
        prop = [f'-Dwurm.server.sqliteOverlay={target}']
        result = self.run_java('verify', extra_cp=[target,SERVER], properties=prop)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('POSITION_PATCH_ACTIVE', result.stdout)
        result = self.run_java('verify', extra_cp=[SERVER,target], properties=prop)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('SERVER_SQLITE_PATCH_NOT_SELECTED', result.stderr)
        with zipfile.ZipFile(target,'a') as z: z.writestr('unexpected.txt','tamper')
        self.assertNotEqual(self.run_java('verify', extra_cp=[target,SERVER], properties=prop).returncode, 0)

    @unittest.skipUnless(SERVER and os.environ.get('WURM_TEST_SQLITE_JAR'), 'Optional private server + pinned SQLite JDBC fixtures')
    def test_actual_player_and_creature_save_methods_with_pinned_sqlite(self):
        # Only the supplied CreaturePos executes; dependencies below are authored test APIs.
        base = self.root/'actual-methods'
        sources = {
            'com/wurmonline/shared/constants/CounterTypes.java': 'package com.wurmonline.shared.constants; public interface CounterTypes {}',
            'com/wurmonline/server/Server.java': 'package com.wurmonline.server; public class Server { public static java.util.Random rand=new java.util.Random(1); public static Server getInstance(){return new Server();} public boolean isPS(){return Boolean.getBoolean("fixture.personal");} }',
            'com/wurmonline/server/WurmId.java': 'package com.wurmonline.server; public class WurmId {public static int getType(long id){return id==987654321024L?0:1;}}',
            'com/wurmonline/server/Constants.java': '''package com.wurmonline.server; public class Constants {
 public static boolean useScheduledExecutorToUpdatePlayerPositionInDatabase=false, useScheduledExecutorToUpdateCreaturePositionInDatabase=false;
 public static int numberOfDbPlayerPositionsToUpdateEachTime=10, numberOfDbCreaturePositionsToUpdateEachTime=10;
}''',
            'com/wurmonline/server/DbConnector.java': '''package com.wurmonline.server; public class DbConnector {
 public static java.sql.Connection players, creatures;
 public static java.sql.Connection getPlayerDbCon(){return players;}
 public static java.sql.Connection getCreatureDbCon(){return creatures;}
 public static void returnConnection(java.sql.Connection c){}
}''',
            'com/wurmonline/server/utils/DbUtilities.java': '''package com.wurmonline.server.utils; public class DbUtilities {
 public static void closeDatabaseObjects(java.sql.Statement s,java.sql.ResultSet r) throws java.sql.SQLException {if(r!=null)r.close();if(s!=null)s.close();}
}''',
            'com/wurmonline/server/creatures/Creature.java': 'package com.wurmonline.server.creatures; public class Creature {public static float normalizeAngle(float a){return (a%360+360)%360;}}',
            'com/wurmonline/server/creatures/PositionFixture.java': '''package com.wurmonline.server.creatures;
import java.sql.*;
import com.wurmonline.server.DbConnector;
public class PositionFixture {
 public static void main(String[] args) throws Exception {
  Class.forName("org.sqlite.JDBC");
  try(Connection p=DriverManager.getConnection("jdbc:sqlite::memory:"); Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
   DbConnector.players=p; DbConnector.creatures=c;
   for (Connection db : new Connection[]{p,c}) {
    db.createStatement().execute("CREATE TABLE POSITION (WURMID BIGINT PRIMARY KEY,POSX REAL,POSY REAL,POSZ REAL,ROTATION REAL,ZONEID INT,LAYER INT,ONBRIDGE BIGINT,KEEP TEXT DEFAULT 'original')");
    db.createStatement().execute("CREATE TRIGGER no_delete BEFORE DELETE ON POSITION BEGIN SELECT RAISE(ABORT,'delete forbidden'); END");
    boolean player=db==p; long id=player?987654321024L:987654321025L;
    if (Boolean.getBoolean("fixture.personal")) db.createStatement().execute("INSERT INTO POSITION(WURMID) VALUES ("+id+")");
    CreaturePos pos=new CreaturePos(id,8.25f,12.5f,-3.75f,450,42,-1,-10,false);
    var bridgeField=CreaturePos.class.getDeclaredField("bridgeId");bridgeField.setAccessible(true);bridgeField.setLong(pos,123456789012345L);
    try { save(pos,player,42); }
    catch(SQLException expected) {
     if(!args[0].equals("original") || !expected.getMessage().contains("DUPLICATE")) throw expected;
     System.out.println("ORIGINAL_POSITION_FAILURE "+(player?"player":"creature")); continue;
    }
    if(args[0].equals("original")) throw new AssertionError("Original unexpectedly accepted");
    check(db,id,8.25f,12.5f,-3.75f,90,42,-1,123456789012345L,"original");
    db.createStatement().execute("UPDATE POSITION SET KEEP='retained'");
    long row; try(ResultSet rs=db.createStatement().executeQuery("SELECT rowid FROM POSITION")){rs.next();row=rs.getLong(1);}
    pos.setPosX(16.25f);pos.setPosY(20.5f);pos.setPosZ(-6.75f,true);pos.setRotation(180);pos.setLayer(0);bridgeField.setLong(pos,-10);
    save(pos,player,43);
    check(db,id,16.25f,20.5f,-6.75f,180,43,0,-10,"retained");
    try(ResultSet rs=db.createStatement().executeQuery("SELECT rowid FROM POSITION")){rs.next();if(rs.getLong(1)!=row)throw new AssertionError("row identity");}
    System.out.println("ACTUAL_POSITION_SAVE_PASS "+(player?"player":"creature"));
   }
  }
 }
 static void save(CreaturePos p,boolean player,int zone) throws SQLException {if(player)p.savePlayerPosition(zone,true);else p.saveCreaturePosition(zone,true);}
 static void check(Connection db,long id,float x,float y,float z,float rot,int zone,int layer,long bridge,String keep) throws Exception {
  try(ResultSet r=db.createStatement().executeQuery("SELECT * FROM POSITION")) {
   if(!r.next() || r.getLong("WURMID")!=id || r.getFloat("POSX")!=x || r.getFloat("POSY")!=y || r.getFloat("POSZ")!=z || r.getFloat("ROTATION")!=rot || r.getInt("ZONEID")!=zone || r.getInt("LAYER")!=layer || r.getLong("ONBRIDGE")!=bridge || !r.getString("KEEP").equals(keep) || r.next()) throw new AssertionError("binding/row mismatch");
  }
 }
}'''
        }
        for who in ('Creature','Player'):
            sources[f'com/wurmonline/server/utils/{who}PositionDatabaseUpdater.java'] = f'package com.wurmonline.server.utils; public class {who}PositionDatabaseUpdater {{public {who}PositionDatabaseUpdater(String n,int c){{}} public void addToQueue({who}PositionDbUpdatable p){{throw new AssertionError("scheduler unexpected");}} public void saveImmediately(){{throw new AssertionError("scheduler unexpected");}} }}'
        files=[]
        for name,text in sources.items():
            file=base/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);files.append(str(file))
        result=subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-cp',SERVER,'-d',str(base),*files],text=True,capture_output=True)
        self.assertEqual(result.returncode,0,result.stderr)
        target=base/'actual-overlay.jar'
        result=self.run_java('prepare',SERVER,target)
        self.assertEqual(result.returncode,0,result.stderr)
        for mode,patched,personal in [('original',False,False),('patched',True,False),('personal',True,True)]:
            cp=os.pathsep.join(map(str,[base,*([target] if patched else []),os.environ['WURM_TEST_SQLITE_JAR'],SERVER]))
            result=subprocess.run(['java',f'-Dfixture.personal={str(personal).lower()}','-cp',cp,'com.wurmonline.server.creatures.PositionFixture',mode],text=True,capture_output=True,timeout=20)
            self.assertEqual(result.returncode,0,mode+result.stdout+result.stderr)
            for who in ('player','creature'):
                self.assertIn(('ORIGINAL_POSITION_FAILURE' if mode=='original' else 'ACTUAL_POSITION_SAVE_PASS')+' '+who,result.stdout)


if __name__ == '__main__': unittest.main()
