"""Exercise production retention SQL and the actual bounded cross-process log queue."""
from pathlib import Path
import json
import re
import shutil
import sqlite3
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/lijinmu/arashi'
source = (JAVA / 'logs/RuntimeLogStore.java').read_text()


def strings(expression):
    return ''.join(json.loads(item) for item in re.findall(r'"(?:[^"\\]|\\.)*"', expression))


def constant(name):
    return strings(re.search(r'String ' + name + r' = (.*?);', source, re.S).group(1))


db = sqlite3.connect(':memory:')
for expression in re.findall(r'db\.execSQL\((.*?\));', source, re.S):
    sql = strings(expression)
    if sql.startswith(('CREATE ', 'INSERT INTO state')):
        db.execute(sql)
now = 2_000_000_000_000
cutoff = now - 12 * 60 * 60 * 1000


def add(time, message='test'):
    db.execute('INSERT INTO logs(time,level,tag,process,pid,message) VALUES (?, ?, ?, ?, ?, ?)',
               (time, 'INFO', 'test', 'test.process', 1, message))


for time in (cutoff - 1, cutoff, cutoff + 1, now, now + 1):
    add(time)
db.execute('DELETE FROM logs WHERE ' + constant('EXPIRED'), (cutoff, now))
assert db.execute('SELECT time FROM logs ORDER BY time').fetchall() == [(cutoff + 1,), (now,)]
db.execute('DELETE FROM logs')
for index in range(205):
    add(now - index)
before = 2**63 - 1
ids = []
while True:
    batch = db.execute('SELECT _id FROM logs WHERE ' + constant('PAGE')
                       + ' ORDER BY _id DESC LIMIT 100', (before, cutoff, now)).fetchall()
    if not batch:
        break
    ids.extend(row[0] for row in batch)
    before = batch[-1][0]
assert len(ids) == len(set(ids)) == 205

# Export must include all retained records, not only the first UI page.
export_section = source[source.index('static synchronized int export'):]
export_where = re.search(r'"time > \? AND time <= \?"', export_section).group(0)
assert db.execute('SELECT COUNT(*) FROM logs WHERE ' + json.loads(export_where), (cutoff, now)).fetchone()[0] == 205
assert '"_id ASC"' in export_section

# The clear watermark is persisted in the same transaction as the deletion.
db.execute('DELETE FROM logs')
update = re.search(r'"UPDATE state SET cleared_before = \? WHERE _id = 1"', source).group(0)
db.execute(json.loads(update), (now,))
assert db.execute('SELECT cleared_before FROM state').fetchone()[0] == now
assert 'time <= clearedBefore' in source
for index in range(10025):
    add(now)
db.execute(constant('TRIM'))
assert db.execute('SELECT COUNT(*) FROM logs').fetchone()[0] == 10000
assert db.execute('SELECT MIN(_id) FROM logs').fetchone()[0] == db.execute('SELECT MAX(_id)-9999 FROM logs').fetchone()[0]

stubs = {
'android/os/Bundle.java': '''package android.os;import java.util.*;
public class Bundle {public static final Bundle EMPTY=new Bundle();public Map<String,Object> data=new HashMap<>();
 public void putLong(String k,long v){data.put(k,v);}public void putInt(String k,int v){data.put(k,v);}public void putString(String k,String v){data.put(k,v);}
 public long getLong(String k,long v){return data.containsKey(k)?(Long)data.get(k):v;}public int getInt(String k,int v){return data.containsKey(k)?(Integer)data.get(k):v;}
 public String getString(String k){return (String)data.get(k);}public <T> void putParcelableArrayList(String k,ArrayList<T> v){data.put(k,v);}
}''',
'android/os/Process.java': 'package android.os;public class Process {public static int myPid(){return 7;}public static void killProcess(int p){}}',
'android/os/Build.java': 'package android.os;public class Build {public static String MODEL="test";public static class VERSION {public static String RELEASE="14";}}',
'android/util/Log.java': 'package android.util;public class Log {public static String getStackTraceString(Throwable t){return t.toString();}}',
'android/net/Uri.java': 'package android.net;public class Uri {public static Uri parse(String s){return new Uri();}}',
'android/content/ContentResolver.java': '''package android.content;import android.os.Bundle;import android.net.Uri;
public class ContentResolver {public static int mode=0;public static Bundle last;
 public Bundle call(Uri u,String m,String a,Bundle b){last=b;if(mode==0)throw new RuntimeException("unavailable");return mode==1?null:Bundle.EMPTY;}}''',
'android/content/Context.java': '''package android.content;public class Context {public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){return this;}public ContentResolver getContentResolver(){return new ContentResolver();}
 public <T>T getSystemService(Class<T> c){return null;}public SharedPreferences getSharedPreferences(String n,int m){return null;}}''',
'android/content/ContentValues.java': 'package android.content;public class ContentValues {public void put(String k,Object v){}}',
'android/content/ComponentName.java': 'package android.content;public class ComponentName {public ComponentName(Context c,Class<?> cls){}}',
'android/content/SharedPreferences.java': '''package android.content;import java.util.*;public interface SharedPreferences {
 Map<String,?> getAll();void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);
 interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences p,String k);}}''',
'android/database/Cursor.java': '''package android.database;public interface Cursor extends AutoCloseable {
 boolean moveToFirst();boolean moveToNext();long getLong(int i);int getInt(int i);String getString(int i);void close();}''',
'android/database/sqlite/SQLiteDatabase.java': '''package android.database.sqlite;import android.database.Cursor;import android.content.ContentValues;
public class SQLiteDatabase {public void beginTransaction(){}public void setTransactionSuccessful(){}public void endTransaction(){}
 public Cursor rawQuery(String s,String[] a){return null;}public long insertOrThrow(String t,String n,ContentValues v){return 1;}
 public int delete(String t,String w,String[] a){return 1;}public void execSQL(String s){}public void execSQL(String s,Object[] a){}
 public Cursor query(String t,String[] c,String w,String[] a,String g,String h,String o,String l){return null;}
 public Cursor query(String t,String[] c,String w,String[] a,String g,String h,String o){return null;}}''',
'android/database/sqlite/SQLiteOpenHelper.java': '''package android.database.sqlite;import android.content.Context;public abstract class SQLiteOpenHelper {
 public SQLiteOpenHelper(Context c,String n,Object f,int v){}public SQLiteDatabase getWritableDatabase(){return new SQLiteDatabase();}
 public abstract void onCreate(SQLiteDatabase d);public abstract void onUpgrade(SQLiteDatabase d,int o,int n);}''',
'android/app/Application.java': 'package android.app;public class Application extends android.content.Context {public void onCreate(){}public static String getProcessName(){return "test";}}',
'android/app/ActivityThread.java': '''package android.app;import android.content.Context;public class ActivityThread {
 public static boolean available=true;public static Context currentApplication(){return available?new Context():null;}
 public static ActivityThread currentActivityThread(){return new ActivityThread();}public Context getSystemContext(){return null;}}''',
'android/app/job/JobParameters.java': 'package android.app.job;public class JobParameters {}',
'android/app/job/JobService.java': '''package android.app.job;public abstract class JobService extends android.content.Context {
 public abstract boolean onStartJob(JobParameters p);public abstract boolean onStopJob(JobParameters p);public void jobFinished(JobParameters p,boolean b){}}''',
'android/app/job/JobInfo.java': '''package android.app.job;import android.content.ComponentName;public class JobInfo {public static class Builder {
 public Builder(int i,ComponentName n){}public Builder setPeriodic(long v){return this;}public Builder setPersisted(boolean b){return this;}public JobInfo build(){return new JobInfo();}}}''',
'android/app/job/JobScheduler.java': 'package android.app.job;public class JobScheduler {public JobInfo getPendingJob(int id){return null;}public int schedule(JobInfo i){return 1;}}',
'com/lijinmu/arashi/BuildConfig.java': 'package com.lijinmu.arashi;public class BuildConfig {public static String VERSION_NAME="test";}',
'com/lijinmu/arashi/xposed/HookConfig.java': 'package com.lijinmu.arashi.xposed;public class HookConfig {public static String PREF_NAME="hooks";}',
'com/lijinmu/arashi/uninstall/UninstallConfig.java': 'package com.lijinmu.arashi.uninstall;public class UninstallConfig {public static String PREF_NAME="uninstall";}',
'com/lijinmu/arashi/notifications/NotificationPreferences.java': 'package com.lijinmu.arashi.notifications;public class NotificationPreferences {public static String PREFS="event_notifications";}',
'Check.java': '''import java.lang.reflect.*;import java.util.*;import android.os.Bundle;import android.content.ContentResolver;
import com.lijinmu.arashi.logs.HookLogBridge;
public class Check {static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  Field pendingField=HookLogBridge.class.getDeclaredField("pending");pendingField.setAccessible(true);
  var pending=(ArrayDeque<?>)pendingField.get(null);Method flush=HookLogBridge.class.getDeclaredMethod("flush");flush.setAccessible(true);
  HookLogBridge.info("first");HookLogBridge.info("second");flush.invoke(null);check(pending.size()==2);
  ContentResolver.mode=1;flush.invoke(null);check(pending.size()==2);
  ContentResolver.mode=2;android.app.ActivityThread.available=false;flush.invoke(null);check(pending.size()==2);
  android.app.ActivityThread.available=true;flush.invoke(null);check(pending.isEmpty());
  var sent=(ArrayList<?>)ContentResolver.last.data.get("entries");check(sent.size()==2&&((Bundle)sent.get(0)).getString("message").equals("first"));
  for(int i=0;i<140;i++)HookLogBridge.info("log-"+i);check(pending.size()==128);
  flush.invoke(null);sent=(ArrayList<?>)ContentResolver.last.data.get("entries");check(sent.size()==16&&pending.size()==112);
  check(((Bundle)sent.get(0)).getString("message").equals("log-12"));
  pending.clear();HookLogBridge.info("x".repeat(20000));flush.invoke(null);
  sent=(ArrayList<?>)ContentResolver.last.data.get("entries");check(((Bundle)sent.get(0)).getString("message").length()==12000);
  System.out.println("Hook log queue retries, ordering, batch and size limits passed");
 }}''',
}
with tempfile.TemporaryDirectory(prefix='arashi-logs-') as directory:
    temp = Path(directory)
    for name, content in stubs.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    sources = list(temp.rglob('*.java')) + list((JAVA / 'logs').glob('*.java')) + [JAVA / 'ArashiApplication.java']
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(path) for path in sources], check=True)
    subprocess.run(['java', '-cp', str(temp / 'classes'), 'Check'], check=True)
print('Runtime log retention boundary, pagination, complete export and storage cap passed')
