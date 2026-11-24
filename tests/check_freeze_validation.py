"""Run the real legacy and batch freeze hooks with caller policies and prompt responses."""
from pathlib import Path
import subprocess
import tempfile
import shutil

ROOT = Path(__file__).resolve().parents[1]
namespace = {'__file__': str(ROOT / 'tests/check_uninstall_targets.py')}
source = (ROOT / 'tests/check_uninstall_targets.py').read_text()
exec(compile(source.split('with tempfile.TemporaryDirectory')[0], namespace['__file__'], 'exec'), namespace)
stubs = namespace['stubs']
stubs['android/os/Binder.java'] = '''package android.os; public class Binder {
 public static int uid=2000; public static int getCallingUid(){return uid;} public static int getCallingPid(){return 24680;}
 public static long clearCallingIdentity(){return 1;}public static void restoreCallingIdentity(long t){}
}'''
stubs['android/content/ContentResolver.java'] = '''package android.content; public class ContentResolver {
 public android.os.Bundle policy,record;public int records;
 public android.os.Bundle call(Object uri,String method,String arg,android.os.Bundle extras){
 if(method.equals("get"))return policy;records++;record=extras;return new android.os.Bundle();}
}'''
stubs['android/app/ActivityThread.java'] = '''package android.app; public class ActivityThread {
 public static android.content.Context context=new android.content.Context();
 public static ActivityThread currentActivityThread(){return new ActivityThread();}
 public android.content.Context getSystemContext(){return context;}
}'''
stubs['android/content/Intent.java'] = '''package android.content; public class Intent extends java.util.HashMap<String,Object> {
 public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_MULTIPLE_TASK=2,FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS=4;
 public Intent setClassName(String p,String c){return this;}public Intent addFlags(int f){return this;}
 public Intent putExtra(String k,Object v){put(k,v);return this;}
}'''
stubs['android/os/ResultReceiver.java'] = '''package android.os; public class ResultReceiver {
 public ResultReceiver(Object h){}protected void onReceiveResult(int c,Bundle b){}public void send(int c,Bundle b){onReceiveResult(c,b);}
}'''
stubs['android/content/Context.java'] = stubs['android/content/Context.java'].replace('public int prompts;', 'public int prompts;public int response=1;public Intent prompt;').replace(
 'prompts++;throw new AssertionError("Unexpected prompt");',
 'prompts++;prompt=i;((android.os.ResultReceiver)i.get("decision_receiver")).send(response,new android.os.Bundle());')
stubs['com/lijinmu/arashi/uninstall/UninstallScreenState.java'] = stubs['com/lijinmu/arashi/uninstall/UninstallScreenState.java'].replace(
 'return true;', 'return off;').replace('public static boolean isScreenOff', 'public static boolean off;public static boolean isScreenOff')
stubs['com/android/server/pm/PackageManagerService.java'] = '''package com.android.server.pm; public class PackageManagerService {
 public void setEnabledSetting(String p,String c,int s,int f,int u,String caller){}
 public void setEnabledSettings(java.util.List<?> settings,int u,String caller){}
 public static class Setting {
  String p,c;int s;public Setting(String p,String c,int s){this.p=p;this.c=c;this.s=s;}
  public String getPackageName(){return p;}public String getComponentName(){return c;}public int getEnabledState(){return s;}
 }
}'''
stubs['Check.java'] = '''import android.content.*;import android.os.*;import java.util.*;import java.lang.reflect.*;
import com.lijinmu.arashi.uninstall.*;import com.lijinmu.arashi.xposed.hooks.UninstallHooks;
import com.android.server.pm.PackageManagerService;import com.android.server.pm.PackageManagerService.Setting;
import io.github.libxposed.api.XposedModule;
public class Check {
 static Context c=android.app.ActivityThread.context;static XposedModule module=new XposedModule();
 static PackageManagerService pms=new PackageManagerService();static Method single,batch;static int applied;
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void list(String key,String... values){c.resolver.policy.put(key,new ArrayList<>(Arrays.asList(values)));}
 static void call(Method m,Object[] args,boolean allowed,boolean spoof)throws Throwable {
  int before=applied;boolean denied=false;
  try{module.call(m,pms,args,()->{applied++;return null;});}catch(SecurityException expected){denied=true;}
  check(applied==before+(allowed?1:0));check(denied==(!allowed&&!spoof));
 }
 static void single(int state,String component,boolean allowed,boolean spoof)throws Throwable {
  call(single,new Object[]{"target.app",component,state,0,10,"caller.app"},allowed,spoof);
 }
 static void reason(String expected){check(expected.equals(c.resolver.record.get("reason")));}
 public static void main(String[] args)throws Throwable {
  Method install=UninstallHooks.class.getDeclaredMethod("hookFreeze",XposedModule.class,ClassLoader.class);
  install.setAccessible(true);install.invoke(null,module,Check.class.getClassLoader());check(module.hooks.size()==2);
  single=PackageManagerService.class.getDeclaredMethod("setEnabledSetting",String.class,String.class,int.class,int.class,int.class,String.class);
  batch=PackageManagerService.class.getDeclaredMethod("setEnabledSettings",List.class,int.class,String.class);
  c.resolver.policy=new Bundle();c.resolver.policy.putBoolean(UninstallConfig.ENABLED,true);
  list(UninstallConfig.BLACKLIST,"uid:2000");
  single(2,null,true,false);check(c.resolver.records==0); // New switch defaults off.
  c.resolver.policy.putBoolean(UninstallConfig.FREEZE_ENABLED,true);
  for(int state:new int[]{2,3,4}){
   single(state,null,false,false);reason("黑名单");
   check("freeze".equals(c.resolver.record.get("operation")));
   check(Integer.valueOf(state).equals(c.resolver.record.get("requested_state")));
   check(Integer.valueOf(10).equals(c.resolver.record.get("user_id")));
   check(Integer.valueOf(24680).equals(c.resolver.record.get("caller_pid")));
  }
  int records=c.resolver.records;
  single(0,null,true,false);single(1,null,true,false);single(3,"SomeActivity",true,false);
  check(c.resolver.records==records); // Enabling and component toggles never enter freeze validation.
  c.resolver.policy.putBoolean(UninstallConfig.SPOOF_RESULT,true);single(2,null,false,true);
  c.resolver.policy.putBoolean(UninstallConfig.SPOOF_RESULT,false);
  list(UninstallConfig.WHITELIST,"uid:2000");single(3,null,false,false); // Blacklist wins.
  list(UninstallConfig.BLACKLIST);single(3,null,true,false);reason("白名单");
  list(UninstallConfig.BLOCKED_TARGETS,"pkg:target.app");single(3,null,false,false);reason("目标应用禁止冻结");
  list(UninstallConfig.BLOCKED_TARGETS);list(UninstallConfig.WHITELIST);
  c.resolver.policy.putBoolean(UninstallConfig.WHITELIST_ONLY,true);single(3,null,false,false);reason("非白名单拒绝");
  c.resolver.policy.putBoolean(UninstallConfig.WHITELIST_ONLY,false);
  c.response=0;single(2,null,false,false);reason("弹窗拒绝");check("freeze".equals(c.prompt.get("operation")));
  c.response=1;single(2,null,true,false);reason("弹窗允许");
  c.response=2;single(2,null,false,false);reason("超时默认拒绝");
  UninstallScreenState.off=true;single(2,null,false,false);reason("设备息屏");UninstallScreenState.off=false;
  list(UninstallConfig.WHITELIST,"uid:0");Binder.uid=0;single(3,null,true,false);reason("白名单");
  list(UninstallConfig.BLACKLIST,"pkg:caller.app");single(3,null,false,false);reason("黑名单");
  list(UninstallConfig.BLACKLIST);Binder.uid=2000;list(UninstallConfig.WHITELIST,"uid:2000");
  list(UninstallConfig.BLOCKED_TARGETS,"pkg:target.app");
  call(batch,new Object[]{List.of(new Setting("other.app",null,3),new Setting("target.app",null,2)),0,"caller.app"},false,false);
  reason("目标应用禁止冻结"); // A rejected entry must prevent the entire platform batch.
  call(batch,new Object[]{List.of(new Setting("target.app","Activity",3),new Setting("target.app",null,1)),0,"caller.app"},true,false);
  c.resolver.policy.putBoolean(UninstallConfig.ENABLED,false);single(3,null,true,false);
  c.resolver.policy=null;single(3,null,true,false); // Same policy-unavailable behavior as uninstall validation.
  System.out.println("Freeze hooks: single/batch, states, caller lists, target protection, prompts, metadata and spoofing passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='arashi-freeze-') as directory:
    tmp = Path(directory)
    for name, source in stubs.items():
        p = tmp / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(source)
    sources = [ROOT / 'app/src/main/java' / p for p in [
        'com/lijinmu/arashi/xposed/hooks/UninstallHooks.java',
        'com/lijinmu/arashi/xposed/Reflect.java',
        'com/lijinmu/arashi/uninstall/UninstallConfig.java']]
    compiler = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run([*compiler, '-d', str(tmp / 'classes'), *map(str, tmp.rglob('*.java')), *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', str(tmp / 'classes'), 'Check'], check=True)
