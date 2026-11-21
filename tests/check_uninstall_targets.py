"""Run the actual uninstall hook policy with host Android doubles."""
from pathlib import Path
import ast
import subprocess
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[1]
module = ast.parse((ROOT / 'tests/check_behavior_hooks.py').read_text())
stubs = ast.literal_eval(next(n.value for n in module.body if isinstance(n, ast.Assign)
                            and any(isinstance(t, ast.Name) and t.id == 'SOURCES' for t in n.targets)))
stubs = {'io/github/libxposed/api/XposedModule.java': stubs['io/github/libxposed/api/XposedModule.java'].replace(
    'public Object getArg(int i)', 'public Object getThisObject(){return owner;} public Object getArg(int i)')}
stubs.update({
'android/content/Context.java': '''package android.content; public class Context {
 public static final int MODE_PRIVATE=0; public int prompts;
 public ContentResolver resolver=new ContentResolver(); public android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
 public ContentResolver getContentResolver(){return resolver;} public android.content.pm.PackageManager getPackageManager(){return pm;}
 public SharedPreferences getSharedPreferences(String n,int m){return new SharedPreferences();}
 public void startActivity(Intent i){prompts++;throw new AssertionError("Unexpected prompt");}
}''',
'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences {
 public boolean getBoolean(String k,boolean d){return d;} public SharedPreferences edit(){return this;}
 public SharedPreferences putBoolean(String k,boolean v){return this;} public void apply(){}
}''',
'android/content/ContentResolver.java': '''package android.content; public class ContentResolver {
 public android.os.Bundle policy,record;
 public android.os.Bundle call(Object uri,String method,String arg,android.os.Bundle extras){
 if(method.equals("get"))return policy; record=extras;return new android.os.Bundle();}
}''',
'android/content/Intent.java': '''package android.content; public class Intent {
 public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_MULTIPLE_TASK=2,FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS=4;
 public Intent setClassName(String p,String c){return this;} public Intent addFlags(int f){return this;}
 public Intent putExtra(String k,Object v){return this;}
}''',
'android/content/IntentSender.java': '''package android.content; public class IntentSender {
 public static class SendIntentException extends Exception {} public void sendIntent(Context c,int r,Intent i,Object a,Object b)throws SendIntentException{}
}''',
'android/content/pm/ApplicationInfo.java': '''package android.content.pm; public class ApplicationInfo {public CharSequence loadLabel(PackageManager p){return "App";}}''',
'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {
 public String[] getPackagesForUid(int uid){return new String[]{"caller.app"};}
 public ApplicationInfo getApplicationInfo(String p,int f){return new ApplicationInfo();}
}''',
'android/content/pm/VersionedPackage.java': '''package android.content.pm; public class VersionedPackage {public String getPackageName(){return "target.app";}}''',
'android/content/pm/PackageInstaller.java': '''package android.content.pm; public class PackageInstaller {
 public static final String EXTRA_STATUS="status",EXTRA_STATUS_MESSAGE="message",EXTRA_PACKAGE_NAME="package";
 public static final int STATUS_SUCCESS=0,STATUS_FAILURE_ABORTED=3;
}''',
'android/os/Binder.java': '''package android.os; public class Binder {
 public static int getCallingUid(){return 2000;} public static int getCallingPid(){return -1;}
 public static long clearCallingIdentity(){return 1;} public static void restoreCallingIdentity(long i){}
}''',
'android/os/Bundle.java': '''package android.os; public class Bundle extends java.util.HashMap<String,Object> {
 public boolean getBoolean(String k,boolean d){return containsKey(k)?(Boolean)get(k):d;}
 public int getInt(String k,int d){return containsKey(k)?(Integer)get(k):d;}
 @SuppressWarnings("unchecked") public java.util.ArrayList<String> getStringArrayList(String k){return (java.util.ArrayList<String>)get(k);}
 public void putBoolean(String k,boolean v){put(k,v);} public void putInt(String k,int v){put(k,v);}
 public void putLong(String k,long v){put(k,v);} public void putString(String k,String v){put(k,v);}
}''',
'android/os/ResultReceiver.java': '''package android.os; public class ResultReceiver {public ResultReceiver(Object h){} protected void onReceiveResult(int c,Bundle b){}}''',
'android/os/SystemClock.java': '''package android.os; public class SystemClock {public static long elapsedRealtime(){return 0;}}''',
'android/util/Log.java': '''package android.util; public class Log {
 public static int i(String t,String m){return 0;} public static int w(String t,String m){return 0;} public static int w(String t,String m,Throwable e){return 0;}
}''',
'com/lijinmu/arashi/xposed/SettingsProvider.java': '''package com.lijinmu.arashi.xposed; public class SettingsProvider {
 public static String URI="uri",METHOD_GET_UNINSTALL="get",METHOD_ADD_UNINSTALL_RECORD="record";
}''',
'com/lijinmu/arashi/uninstall/UninstallScreenState.java': '''package com.lijinmu.arashi.uninstall; public class UninstallScreenState {
 public static final String SCREEN_OFF_REASON="设备息屏"; public static final int SCREEN_OFF_RESULT=4;
 public static boolean isScreenOff(android.content.Context c){return true;}
}''',
'com/lijinmu/arashi/uninstall/UninstallHistoryStore.java': '''package com.lijinmu.arashi.uninstall; public class UninstallHistoryStore {
 public static final String TIME="time",CALLER_UID="uid",CALLER_PACKAGE="caller",CALLER_NAME="name",TARGET_PACKAGE="target",TARGET_NAME="targetName",ALLOWED="allowed",REASON="reason";
}''',
'Check.java': '''import android.content.*;import android.os.*;import java.util.*;import java.lang.reflect.*;
import com.lijinmu.arashi.uninstall.*;import com.lijinmu.arashi.xposed.hooks.UninstallHooks;
public class Check {
 static Context c=new Context(); static Method evaluate;
 static void list(String key,String... values){c.resolver.policy.put(key,new ArrayList<>(Arrays.asList(values)));}
 static void check(String target,boolean allowed,String reason,boolean spoof)throws Exception {
  Object d=evaluate.invoke(null,c,2000,target,"caller.app");
  for(var entry:Map.of("allowed",(Object)allowed,"reason",reason,"spoofSuccess",spoof).entrySet()){
   Field f=d.getClass().getDeclaredField(entry.getKey());f.setAccessible(true);
   if(!entry.getValue().equals(f.get(d)))throw new AssertionError(entry.getKey()+" "+f.get(d));
  }
  if(!reason.equals(c.resolver.record.get(UninstallHistoryStore.REASON)) ||
     !Boolean.valueOf(allowed).equals(c.resolver.record.get(UninstallHistoryStore.ALLOWED)))throw new AssertionError("record");
  if(c.prompts!=0)throw new AssertionError("prompt");
 }
 public static void main(String[] args)throws Exception {
  evaluate=UninstallHooks.class.getDeclaredMethod("evaluateRequest",Context.class,int.class,String.class,String.class);evaluate.setAccessible(true);
  c.resolver.policy=new Bundle();c.resolver.policy.putBoolean(UninstallConfig.ENABLED,true);
  list(UninstallConfig.BLOCKED_TARGETS,"pkg:target.app");list(UninstallConfig.WHITELIST,"uid:2000");
  check("target.app",false,"目标应用禁止卸载",false); // Target beats a trusted shell caller.
  check("other.app",true,"白名单",false); // Caller trust still applies to other targets.
  check("target.app.extra",true,"白名单",false); // Exact package matching.
  c.resolver.policy.putBoolean(UninstallConfig.SPOOF_RESULT,true);
  check("target.app",false,"目标应用禁止卸载",true); // Spoofing must not allow deletion or alter history.
  c.resolver.policy.putBoolean(UninstallConfig.ENABLED,false);
  check("target.app",true,"验证未启用",false);
  c.resolver.policy.putBoolean(UninstallConfig.ENABLED,true);
  list(UninstallConfig.BLOCKED_TARGETS);check("target.app",true,"白名单",false); // Unchecking takes effect immediately.
  list(UninstallConfig.BLACKLIST,"uid:2000");check("target.app",false,"黑名单",true);
  list(UninstallConfig.BLACKLIST);list(UninstallConfig.WHITELIST);list(UninstallConfig.BLOCKED_TARGETS,"pkg:caller.app");
  check("target.app",false,"设备息屏",true); // Target list never matches the caller instead.
  check(null,false,"设备息屏",true);
  c.resolver.policy.remove(UninstallConfig.BLOCKED_TARGETS);check("target.app",false,"设备息屏",true); // Existing configs.
  c.resolver.policy.putBoolean(UninstallConfig.WHITELIST_ONLY,true);
  check("target.app",false,"非白名单拒绝",true); // Empty whitelist denies without a prompt.
  list(UninstallConfig.WHITELIST,"pkg:caller.app");check("target.app",true,"白名单",false);
  list(UninstallConfig.BLACKLIST,"uid:2000");check("target.app",false,"黑名单",true);
  list(UninstallConfig.BLACKLIST);list(UninstallConfig.BLOCKED_TARGETS,"pkg:target.app");
  check("target.app",false,"目标应用禁止卸载",true);
  list(UninstallConfig.BLOCKED_TARGETS);list(UninstallConfig.WHITELIST,"uid:2000");
  check("target.app",true,"白名单",false); // UID whitelist remains supported.
  list(UninstallConfig.WHITELIST);c.resolver.policy.putBoolean(UninstallConfig.ENABLED,false);
  check("target.app",true,"验证未启用",false);
  c.resolver.policy.putBoolean(UninstallConfig.ENABLED,true);
  c.resolver.policy.putBoolean(UninstallConfig.WHITELIST_ONLY,false);
  check("target.app",false,"设备息屏",true); // Disabling restores confirmation rules.
  c.resolver.policy=null;Object d=evaluate.invoke(null,c,2000,"target.app",null);
  Field f=d.getClass().getDeclaredField("allowed");f.setAccessible(true);if(!(Boolean)f.get(d))throw new AssertionError("unavailable policy");
  System.out.println("Uninstall target protection: priority, exact matching, enablement, live updates, spoofing and records passed");
 }
}''',
})
with tempfile.TemporaryDirectory(prefix='arashi-uninstall-targets-') as directory:
    tmp = Path(directory)
    for name, source in stubs.items():
        p = tmp/name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(source)
    sources = [ROOT/'app/src/main/java'/p for p in [
        'com/lijinmu/arashi/xposed/hooks/UninstallHooks.java',
        'com/lijinmu/arashi/xposed/Reflect.java',
        'com/lijinmu/arashi/uninstall/UninstallConfig.java']]
    compiler = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '-d', str(tmp/'classes'), *map(str, tmp.rglob('*.java')), *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', str(tmp/'classes'), 'Check'], check=True)
