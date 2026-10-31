"""Exercise framework-wide icon overlay transactions and non-persistent enablement."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/lijinmu/arashi'
STUBS = {
'android/graphics/RectF.java': 'package android.graphics; public class RectF {public RectF(float a,float b,float c,float d){}}',
'android/graphics/Path.java': 'package android.graphics; public class Path {public float[] approximate(float error){if(error>.02f)throw new AssertionError();return new float[]{0,62,0,.5f,100,38,1,62,0};}}',
'android/os/Build.java': 'package android.os; public class Build {public static class VERSION {public static int SDK_INT=34;}}',
'android/os/IBinder.java': 'package android.os;public interface IBinder {}',
'android/os/Binder.java': 'package android.os;public class Binder {public static int clears,restores;public static long clearCallingIdentity(){return ++clears;}public static void restoreCallingIdentity(long token){restores++;}}',
'android/os/ServiceManager.java': 'package android.os;public class ServiceManager {public static IBinder getService(String s){return new IBinder(){};}}',
'android/util/Log.java': 'package android.util;public class Log {public static int w(String a,String b,Throwable t){return 0;}}',
'com/lijinmu/arashi/xposed/ConfigManager.java': 'package com.lijinmu.arashi.xposed;public class ConfigManager {public static boolean hasSettingsSnapshot(){return false;}public static boolean isEnabledCached(String k){return false;}}',
'com/lijinmu/arashi/xposed/HookConfig.java': 'package com.lijinmu.arashi.xposed;public class HookConfig {public static final String KEY_IOS_SYSTEM_ICON_MASK="mask";}',
'com/lijinmu/arashi/logs/HookLogBridge.java': 'package com.lijinmu.arashi.logs;public class HookLogBridge {public static void info(String m){}public static void error(String m,Throwable t){}}',
'com/lijinmu/arashi/xposed/hooks/IosIconDrawable.java': 'package com.lijinmu.arashi.xposed.hooks;public class IosIconDrawable {public static android.graphics.Path maskFor(android.graphics.RectF r){return new android.graphics.Path();}}',
'android/content/om/OverlayIdentifier.java': '''package android.content.om;public class OverlayIdentifier {
public final String owner,name;public OverlayIdentifier(String p,String n){owner=p;name=n;}
public String getPackageName(){return owner;}public String getOverlayName(){return name;}
public String toString(){return owner+":"+name;}}''',
'android/content/om/OverlayInfo.java': 'package android.content.om;public class OverlayInfo {public boolean isEnabled(){return true;}}',
'android/content/om/FabricatedOverlay.java': '''package android.content.om;public class FabricatedOverlay {
public static String owner,name,target,resource,data;public static int type;
public static class Builder {public Builder(String o,String n,String t){owner=o;name=n;target=t;}
public Builder setResourceValue(String r,int t,String d){resource=r;type=t;data=d;return this;}
public FabricatedOverlay build(){return new FabricatedOverlay();}}}''',
'android/content/om/OverlayManagerTransaction.java': '''package android.content.om;import java.util.*;public class OverlayManagerTransaction {
public boolean register,unregister;public List<Integer> users=new ArrayList<>();
public static class Builder {OverlayManagerTransaction t=new OverlayManagerTransaction();
public Builder registerFabricatedOverlay(FabricatedOverlay o){t.register=true;return this;}
public Builder unregisterFabricatedOverlay(OverlayIdentifier id){if(!id.toString().equals("android:ArashiIosIconMask"))throw new AssertionError("other overlay");t.unregister=true;return this;}
public Builder setEnabled(OverlayIdentifier id,boolean enabled,int user){if(!enabled)throw new AssertionError();t.users.add(user);return this;}
public OverlayManagerTransaction build(){return t;}}}''',
'android/content/om/IOverlayManager.java': '''package android.content.om;import android.os.IBinder;
public interface IOverlayManager {void commit(OverlayManagerTransaction t);OverlayInfo getOverlayInfoByIdentifier(OverlayIdentifier id,int user);
class Stub {public static final Impl INSTANCE=new Impl();public static IOverlayManager asInterface(IBinder b){return INSTANCE;}
public static class Impl implements IOverlayManager {public int commits;public boolean present,fail;public OverlayManagerTransaction last;
public void commit(OverlayManagerTransaction t){commits++;if(fail)throw new IllegalStateException("idmap failed");last=t;present=t.register;}
public OverlayInfo getOverlayInfoByIdentifier(OverlayIdentifier id,int user){return present?new OverlayInfo():null;}}}}''',
'io/github/libxposed/api/XposedModule.java': '''package io.github.libxposed.api;import java.lang.reflect.*;
public class XposedModule {public static Callback callback;public static int hooks;
public Hook hook(Method m){hooks++;return new Hook();}public class Hook {public void intercept(Callback c){callback=c;}}
public interface Callback {Object invoke(Chain c)throws Throwable;}public interface Chain {Object getThisObject();Object proceed()throws Throwable;}}''',
'com/android/server/om/OverlayManagerSettings.java': '''package com.android.server.om;import java.util.*;import java.io.*;import android.content.om.*;
public class OverlayManagerSettings {private final ArrayList<SettingsItem> mItems=new ArrayList<>();
private static class SettingsItem {private final OverlayIdentifier mOverlay;SettingsItem(OverlayIdentifier i){mOverlay=i;}}
public void add(String p,String n){mItems.add(new SettingsItem(new OverlayIdentifier(p,n)));}
public List<String> ids(){List<String> ids=new ArrayList<>();for(SettingsItem i:mItems)ids.add(i.mOverlay.toString());return ids;}
void persist(OutputStream stream){} }''',
'com/lijinmu/arashi/xposed/hooks/TestGlobalIcons.java': '''package com.lijinmu.arashi.xposed.hooks;
import java.util.*;import android.content.om.*;import io.github.libxposed.api.XposedModule;
public class TestGlobalIcons {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
public static void main(String[] args)throws Throwable {
android.os.Build.VERSION.SDK_INT=31;
GlobalIconMaskHooks.hookSystemServer(new XposedModule(),TestGlobalIcons.class.getClassLoader());
check(XposedModule.hooks==0,"Android 12 must not pretend to support string overlays");
android.os.Build.VERSION.SDK_INT=34;
GlobalIconMaskHooks.hookSystemServer(new XposedModule(),TestGlobalIcons.class.getClassLoader());
GlobalIconMaskHooks.hookSystemServer(new XposedModule(),TestGlobalIcons.class.getClassLoader());
check(XposedModule.hooks==1,"single system hook");
var service=IOverlayManager.Stub.INSTANCE;
GlobalIconMaskHooks.apply(false,new int[]{0,10});
check(service.commits==0,"default off must not register an overlay");
GlobalIconMaskHooks.apply(true,new int[]{0,10});
check(service.last.register&&service.last.users.equals(List.of(0,10)),"enable every Android user");
check(FabricatedOverlay.owner.equals("android")&&FabricatedOverlay.target.equals("android"),"framework target");
check(FabricatedOverlay.resource.equals("android:string/config_icon_mask")&&FabricatedOverlay.type==3,"only icon mask string");
check(FabricatedOverlay.data.equals("M62.0,0.0L100.0,38.0L62.0,0.0Z"),"desktop mask points");
GlobalIconMaskHooks.apply(false,new int[]{0,10});
check(service.last.unregister&&!service.last.register&&!service.present,"remove only own overlay");
service.fail=true;
try {GlobalIconMaskHooks.apply(true,new int[]{0});throw new AssertionError("failure swallowed");}
catch(ReflectiveOperationException expected){}
check(android.os.Binder.clears==android.os.Binder.restores,"restore binder identity even after failure");
var settings=new com.android.server.om.OverlayManagerSettings();
settings.add("vendor.icons","round");settings.add("android","ArashiIosIconMask");settings.add("android","OtherOverlay");settings.add("other","ArashiIosIconMask");
var originals=settings.ids();
XposedModule.callback.invoke(new XposedModule.Chain(){public Object getThisObject(){return settings;}public Object proceed(){
check(settings.ids().equals(List.of("vendor.icons:round","android:OtherOverlay","other:ArashiIosIconMask")),"persist only other overlays");return null;}});
check(settings.ids().equals(originals),"restore live settings ordering");
try {XposedModule.callback.invoke(new XposedModule.Chain(){public Object getThisObject(){return settings;}public Object proceed(){throw new IllegalStateException("disk error");}});throw new AssertionError();}
catch(IllegalStateException expected){}
check(settings.ids().equals(originals),"restore live overlay even when persistence fails");
System.out.println("Global icon mask: all-user framework transactions, off cleanup, non-persistence and failure restoration passed");
}}''',
}
entry = (JAVA / 'xposed/HookEntry.java').read_text()
assert 'SystemIconMaskHooks.hook(this)' not in entry
assert 'GlobalIconMaskHooks.hookSystemServer(this, cl)' in entry
scope = (JAVA / 'xposed/HookScopes.java').read_text()
uses = scope.split('private static boolean usesSystemServer', 1)[1].split('private static String[] applicationScopes', 1)[0]
assert 'KEY_IOS_SYSTEM_ICON_MASK' not in uses
with tempfile.TemporaryDirectory() as directory:
    base = Path(directory)
    for name, source in STUBS.items():
        dest = base / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(source)
    dest = base / 'com/lijinmu/arashi/xposed/hooks/GlobalIconMaskHooks.java'
    dest.write_text((JAVA / 'xposed/hooks/GlobalIconMaskHooks.java').read_text())
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', str(base)] + [str(p) for p in base.rglob('*.java')], check=True)
    subprocess.run(['java', '-cp', str(base), 'com.lijinmu.arashi.xposed.hooks.TestGlobalIcons'], check=True)
