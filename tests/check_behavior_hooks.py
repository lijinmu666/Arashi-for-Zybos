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
public Object getThisObject() { return owner; }
public Object proceed() throws Throwable { return original.run(); }
}
public final Map<Executable, Interceptor> hooks = new HashMap<>();
public class Builder { Executable m; Builder(Executable e) {m=e;}
public void intercept(Interceptor i) { hooks.put(m,i); }}
public Builder hook(Executable e) {return new Builder(e);}
public final Set<Executable> deoptimized = new HashSet<>();
public void deoptimize(Executable e) {deoptimized.add(e);}
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
    'com/android/server/ZybosSysApiService.java': '''package com.android.server;
public class ZybosSysApiService {
private void startPadMsWatchdog() {}
private void ensurePadMsAlive() {}
public void systemReady() {}
public void unrelatedApi() {}
static void access$500(ZybosSysApiService s) {s.ensurePadMsAlive();}
}''',
    'com/android/server/pm/ProtectedPackages.java': '''package com.android.server.pm;
public class ProtectedPackages {
public final java.util.Set<String> mZybEnforcedDisabledPackages = new java.util.HashSet<>();
public synchronized boolean isZybEnforcedDisabled(String name) {return mZybEnforcedDisabledPackages.contains(name);}
public synchronized void setZybEnforcedDisabledPackages(java.util.Set<String> names) {mZybEnforcedDisabledPackages.clear();mZybEnforcedDisabledPackages.addAll(names);}
public boolean isPackageStateProtected(int user, String name) {return true;}
}''',
    'com/android/server/pm/PackageManagerService.java': '''package com.android.server.pm;
public class PackageManagerService {
public final ProtectedPackages mProtectedPackages = new ProtectedPackages();
private void registerZybEnforcedDisabledObserver() {}
private void refreshZybEnforcedDisabledPackages() {}
public void systemReady() {}
private void setEnabledSetting() {}
private void updateSettingsInternalLI() {}
private void markPackageUninstalledForUserLPw() {}
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
for (String version : new String[]{"V7.14.0", "V7.14.1", "V9.1.2"})
check(BehaviorCompatibility.supportsVersion(version, 14));
for (String version : new String[]{"V7.13.99", "V7.10.0", "unknown", ""})
check(!BehaviorCompatibility.supportsVersion(version, 14));
check(!HookConfig.defaultEnabled(HookConfig.KEY_DISABLE_INSTALLER_AUTH));
BehaviorHooks.hookSystemServer(m,BehaviorHooksCheck.class.getClassLoader());
read=android.provider.Settings.Global.class.getDeclaredMethod("getInt",android.content.ContentResolver.class,String.class,int.class);
ready=AdbService.class.getDeclaredMethod("systemReady");
disable=AdbService.class.getDeclaredMethod("shouldDisableWifiAdb");
cleanup=ActivityManagerService.class.getDeclaredMethod("deleteIllegalApps");
check(HookConfig.defaultEnabled(HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING)==BehaviorCompatibility.isSupported());
check(HookConfig.defaultEnabled(HookConfig.KEY_BLOCK_APP_UNINSTALL)==BehaviorCompatibility.isSupported());
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
Class<?> vendor = Class.forName("com.android.server.ZybosSysApiService");
for (String name : new String[]{"startPadMsWatchdog", "ensurePadMsAlive"}) {
 Method method = vendor.getDeclaredMethod(name);
 final int[] calls = {0};
 m.call(method,null,new Object[0],()->{calls[0]++; return null;});
 check(calls[0]==1);
 ConfigManager.enabled.add(HookConfig.KEY_DISABLE_PADMS_WATCHDOG);
 m.call(method,null,new Object[0],()->{throw new AssertionError("Watchdog ran");});
 ConfigManager.enabled.clear();
 m.call(method,null,new Object[0],()->{calls[0]++; return null;});
 check(calls[0]==2);
}
check(HookConfig.defaultEnabled(HookConfig.KEY_DISABLE_PADMS_WATCHDOG)==BehaviorCompatibility.isPadMsWatchdogSupported());
check(BehaviorCompatibility.isAllowed(HookConfig.KEY_DISABLE_PADMS_WATCHDOG)==BehaviorCompatibility.isPadMsWatchdogSupported());
check(BehaviorCompatibility.isAllowed(HookConfig.KEY_ALLOW_WIRELESS_DEBUGGING)==BehaviorCompatibility.isSupported());
check(BehaviorCompatibility.isAllowed(HookConfig.KEY_BLOCK_APP_UNINSTALL)==BehaviorCompatibility.isSupported());
check(java.util.Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_DISABLE_PADMS_WATCHDOG));
check(!m.hooks.containsKey(vendor.getMethod("unrelatedApi")));
check(!m.hooks.containsKey(vendor.getMethod("systemReady")));
// Vendor forced-disable policy: runtime toggle, existing state and firmware gate.
Class<?> policy = Class.forName("com.android.server.pm.ProtectedPackages");
Class<?> pms = Class.forName("com.android.server.pm.PackageManagerService");
Object service = pms.getConstructor().newInstance();
com.android.server.pm.ProtectedPackages protectedApps =
 (com.android.server.pm.ProtectedPackages)pms.getField("mProtectedPackages").get(service);
Method query = policy.getMethod("isZybEnforcedDisabled",String.class);
Method setter = policy.getMethod("setZybEnforcedDisabledPackages",java.util.Set.class);
java.util.Set<String> blocked = java.util.Collections.singleton("test.blocked");
protectedApps.setZybEnforcedDisabledPackages(blocked);
check((Boolean)m.call(query,protectedApps,new Object[]{"test.blocked"},()->true));
ConfigManager.enabled.add(HookConfig.KEY_DISABLE_FORCED_FREEZE);
boolean supported = BehaviorCompatibility.isForcedFreezeSupported();
check(BehaviorCompatibility.isAllowed(HookConfig.KEY_DISABLE_FORCED_FREEZE)==supported);
check(!HookConfig.defaultEnabled(HookConfig.KEY_DISABLE_FORCED_FREEZE));
check(java.util.Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_DISABLE_FORCED_FREEZE));
check((Boolean)m.call(query,protectedApps,new Object[]{"test.blocked"},()->true)==!supported);
// Loader hooks clear stale state and suppress both initial registration and later refresh.
for (String name : new String[]{"registerZybEnforcedDisabledObserver","refreshZybEnforcedDisabledPackages"}) {
 Method loader = pms.getDeclaredMethod(name);
 protectedApps.setZybEnforcedDisabledPackages(blocked);
 int[] loads={0};
 m.call(loader,service,new Object[0],()->{loads[0]++; return null;});
 check(loads[0]==(supported?0:1));
 check(protectedApps.mZybEnforcedDisabledPackages.isEmpty()==supported);
}
protectedApps.setZybEnforcedDisabledPackages(blocked);
int[] writes={0};
m.call(setter,protectedApps,new Object[]{blocked},()->{writes[0]++;protectedApps.setZybEnforcedDisabledPackages(blocked);return null;});
check(writes[0]==(supported?0:1));
check(protectedApps.mZybEnforcedDisabledPackages.isEmpty()==supported);
// All enable/install/uninstall policy branches share this membership check.
for (String operation : new String[]{"enable","default-state","install","uninstall-user"})
 check(!(Boolean)m.call(query,protectedApps,new Object[]{"test.blocked"},()->false));
if (supported) {
 for (String caller : new String[]{"systemReady","setEnabledSetting","updateSettingsInternalLI","markPackageUninstalledForUserLPw"})
  check(m.deoptimized.contains(pms.getDeclaredMethod(caller)));
}
// Android's administrator protections and actual enabled state are never rewritten.
check(!m.hooks.containsKey(policy.getMethod("isPackageStateProtected",int.class,String.class)));
ConfigManager.enabled.remove(HookConfig.KEY_DISABLE_FORCED_FREEZE);
m.call(setter,protectedApps,new Object[]{blocked},()->{protectedApps.setZybEnforcedDisabledPackages(blocked);return null;});
check(protectedApps.isZybEnforcedDisabled("test.blocked"));
check((Boolean)m.call(query,protectedApps,new Object[]{"test.blocked"},()->true));
// Newer firmware may omit the vendor classes/methods: installation must tolerate this.
BehaviorHooks.hookSystemServer(new XposedModule(),new ClassLoader(BehaviorHooksCheck.class.getClassLoader()) {
 protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
  if (name.startsWith("com.android.server.pm.")) throw new ClassNotFoundException(name);
  return super.loadClass(name,resolve);
 }
});
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

    for version in ('V7.10.0', 'V7.13.0', 'V7.14.0', 'V7.14.1', 'V9.1.2', '', 'unknown', '9.1.2'):
        subprocess.run(['java', '-Dtest.os.version=' + version, '-cp', str(base / 'classes'), 'BehaviorHooksCheck'], check=True)
