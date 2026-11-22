"""Execute the real behavior interceptors against a small simulated vendor framework."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'android/os/SystemProperties.java': '''package android.os;
public class SystemProperties { public static String get(String key) { return System.getProperty("test.os.version", "V7.13.0"); }}''',
    'android/content/ContentResolver.java': 'package android.content; public class ContentResolver {}',
    'android/provider/Settings.java': '''package android.provider;
public class Settings { public static class Global {
public static int getInt(android.content.ContentResolver r, String k, int d) { return d; }
}}''',
    'android/util/Log.java': '''package android.util;
public class Log { public static int w(String t, String m, Throwable e) { return 0; }}''',
    'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed;
public class ConfigManager {
public static final java.util.Set<String> enabled = new java.util.HashSet<>();
public static boolean isEnabledCached(String k) { return enabled.contains(k); }
}''',
    'io/github/libxposed/api/XposedModule.java': '''package io.github.libxposed.api;
import java.lang.reflect.*; import java.util.*;
public class XposedModule {
public interface Action { Object run() throws Throwable; }
public interface Interceptor { Object run(Chain c) throws Throwable; }
public static class Chain {
Object owner; Object[] args; Action original;
Chain(Object o, Object[] a, Action p) { owner=o; args=a; original=p; }
public Object getArg(int i) { return args[i]; }
public Object proceed() throws Throwable { return original.run(); }
}
public final Map<Executable, Interceptor> hooks = new HashMap<>();
public class Builder { Executable m; Builder(Executable e) {m=e;}
public void intercept(Interceptor i) { hooks.put(m,i); }}
public Builder hook(Executable e) {return new Builder(e);}
public void deoptimize(Executable e) {}
public Object call(Executable e, Object o, Object[] a, Action p) throws Throwable {
Interceptor i=hooks.get(e); return i==null?p.run():i.run(new Chain(o,a,p));
}}''',
    'com/android/server/adb/AdbService.java': '''package com.android.server.adb;
public class AdbService {
public boolean shouldDisableWifiAdb() {return true;}
public void systemReady() {}
private void setAdbEnabled(boolean e, byte t) {}
}''',
    'com/android/server/am/ActivityManagerService.java': '''package com.android.server.am;
public class ActivityManagerService {
public static final java.util.Set<String> mZybMustDeletedApps = new java.util.HashSet<>();
public ActivityManagerService() {}
public void deleteIllegalApps() {}
public void manualUninstall() {}
}''',
    'BehaviorHooksCheck.java': '''import com.lijinmu.arashi.xposed.*;
import com.lijinmu.arashi.xposed.hooks.BehaviorHooks;
import com.android.server.adb.AdbService;
import com.android.server.am.ActivityManagerService;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
public class BehaviorHooksCheck {
static XposedModule m = new XposedModule();
static Method read, ready, cleanup, disable;
static int get(String key, int value) throws Throwable {
return (Integer)m.call(read,null,new Object[]{null,key,value},()->value);
}
static void check(boolean b) {if(!b)throw new AssertionError();}
public static void main(String[] args) throws Throwable {
for (String version : new String[]{"V7.13.0", "V7.13.1", "V7.14.0", "V8.0.0", "V7.100.0"})
check(BehaviorCompatibility.supportsVersion(version));
for (String version : new String[]{"", "V7.10.0", "V7.12.99", "V6.99.0", "7.13.0", "unknown", "V7.13", "V7.13.-1"})
check(!BehaviorCompatibility.supportsVersion(version));
check(!BehaviorCompatibility.supportsVersion(null));
BehaviorHooks.hookSystemServer(m,BehaviorHooksCheck.class.getClassLoader());
if (!BehaviorCompatibility.isSupported()) {check(m.hooks.isEmpty());return;}
read=android.provider.Settings.Global.class.getDeclaredMethod("getInt",android.content.ContentResolver.class,String.class,int.class);
ready=AdbService.class.getDeclaredMethod("systemReady");
disable=AdbService.class.getDeclaredMethod("shouldDisableWifiAdb");
cleanup=ActivityManagerService.class.getDeclaredMethod("deleteIllegalApps");
check(!HookConfig.defaultEnabled(HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING));
check(!HookConfig.defaultEnabled(HookConfig.KEY_BLOCK_APP_UNINSTALL));
check(java.util.Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_BLOCK_APP_UNINSTALL));
// Both disabled: execute firmware normally and retain blacklist.
ActivityManagerService.mZybMustDeletedApps.add("bin.mt.plus.canary");
int[] runs={0};
m.call(cleanup,null,new Object[0],()->{runs[0]++; check(get("adb_wifi_enabled",1)==1);return null;});
check(runs[0]==1 && !ActivityManagerService.mZybMustDeletedApps.isEmpty());
check((Boolean)m.call(disable,null,new Object[0],()->true));
// Wireless alone: boot exemption is scoped, cleanup still works, wifi isn't cleared.
ConfigManager.enabled.add(HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING);
check(!(Boolean)m.call(disable,null,new Object[0],()->true));
check(get("zpad_prop_special_settings",0)==0);
m.call(ready,null,new Object[0],()->{check(get("zpad_prop_special_settings",0)==1);return null;});
check(get("zpad_prop_special_settings",0)==0);
m.call(cleanup,null,new Object[0],()->{runs[0]++;check(get("adb_wifi_enabled",1)==0);check(get("adb_enabled",1)==1);return null;});
check(runs[0]==2 && get("adb_wifi_enabled",1)==1);
// Exceptions must not leak overrides into later calls on the same thread.
try {m.call(ready,null,new Object[0],()->{throw new IllegalStateException();});}catch(IllegalStateException expected){}
check(get("zpad_prop_special_settings",0)==0);
try {m.call(cleanup,null,new Object[0],()->{throw new IllegalStateException();});}catch(IllegalStateException expected){}
check(get("adb_wifi_enabled",1)==1);
// Removal protection alone: clear every entry before skipping the original body.
ConfigManager.enabled.clear();ConfigManager.enabled.add(HookConfig.KEY_BLOCK_APP_UNINSTALL);
ActivityManagerService.mZybMustDeletedApps.add("another.package");
m.call(cleanup,null,new Object[0],()->{throw new AssertionError("Cleanup ran");});
check(ActivityManagerService.mZybMustDeletedApps.isEmpty());
ActivityManagerService.mZybMustDeletedApps.add("bin.mt.plus.canary");
m.call(ActivityManagerService.class.getConstructor(),null,new Object[0],()->null);
check(ActivityManagerService.mZybMustDeletedApps.isEmpty());
check(!m.hooks.containsKey(ActivityManagerService.class.getMethod("manualUninstall")));
ConfigManager.enabled.add(HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING);
m.call(cleanup,null,new Object[0],()->{throw new AssertionError("Both enabled cleanup ran");});
ConfigManager.enabled.clear();
m.call(cleanup,null,new Object[0],()->{runs[0]++; return null;});
check(runs[0]==3);
System.out.println("Behavior hook checks passed");
}}
''',
}
with tempfile.TemporaryDirectory() as directory:
    base = Path(directory)
    for name, source in SOURCES.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    actual = ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed'
    files = list(base.rglob('*.java')) + [actual / 'HookConfig.java', actual / 'BehaviorCompatibility.java', actual / 'hooks/BehaviorHooks.java']
    subprocess.run(['javac', '-d', str(base / 'classes'), *map(str, files)], check=True)
    subprocess.run(['java', '-cp', str(base / 'classes'), 'BehaviorHooksCheck'], check=True)

    for version in ('V7.10.0', '', 'unknown'):
        subprocess.run(['java', '-Dtest.os.version=' + version, '-cp', str(base / 'classes'), 'BehaviorHooksCheck'], check=True)
