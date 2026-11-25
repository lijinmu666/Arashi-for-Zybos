"""Run real removal interceptors against the firmware's factory/publication sequence."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'android/util/Log.java': '''package android.util;
public class Log {
public static int i(String t,String m){return 0;}
public static int w(String t,String m,Throwable e){return 0;}
}''',
    'android/os/IBinder.java': 'package android.os; public interface IBinder {}',
    'android/os/ServiceManager.java': '''package android.os;
public class ServiceManager {
public static void addService(String n, IBinder b) {}
public static void addService(String n, IBinder b, boolean a, int p) {}
}''',
    'com/android/server/ZybosSysApiService.java': '''package com.android.server;
public class ZybosSysApiService implements android.os.IBinder {
public static int constructors, receivers;
public ZybosSysApiService(){constructors++;receivers++;}
public static ZybosSysApiService create(Object context){return new ZybosSysApiService();}
public void systemReady(){}
}''',
    'com/android/server/SystemServer.java': '''package com.android.server;
public class SystemServer { public void startOtherServices(){} }''',
    'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed;
public class ConfigManager { public static boolean enabled;
public static boolean isEnabledCached(String k){return enabled;} }''',
    'io/github/libxposed/api/XposedModule.java': '''package io.github.libxposed.api;
import java.lang.reflect.*; import java.util.*;
public class XposedModule {
public String fail;
public interface Action {Object run() throws Throwable;}
public interface Interceptor {Object run(Chain c) throws Throwable;}
public static class Chain {
Object[] args; Action original;
Chain(Object[] a,Action p){args=a;original=p;}
public Object getArg(int i){return args[i];}
public Object proceed() throws Throwable{return original.run();}
}
public final Map<Executable,Interceptor> hooks=new HashMap<>();
public final Set<Executable> deoptimized=new HashSet<>();
public class Builder {Executable method; Builder(Executable m){method=m;}
public void intercept(Interceptor i){
if(method.getName().equals(fail))throw new IllegalStateException("installation failed");
hooks.put(method,i);
}}
public Builder hook(Executable m){return new Builder(m);}
public void deoptimize(Executable m){deoptimized.add(m);}
public Object call(Executable m,Object[] a,Action p) throws Throwable {
Interceptor i=hooks.get(m);return i==null?p.run():i.run(new Chain(a,p));
}}
''',
    'RemovalCheck.java': '''import java.lang.reflect.*;
import java.util.*;
import android.os.*;
import com.android.server.*;
import com.lijinmu.arashi.xposed.*;
import com.lijinmu.arashi.xposed.hooks.ZybosSysApiServiceHooks;
import io.github.libxposed.api.XposedModule;
public class RemovalCheck {
static Method create,add,addFull;
static int publications;
static void check(boolean ok){if(!ok)throw new AssertionError();}
static void publish(XposedModule m,String name,IBinder binder,boolean full) throws Throwable {
Object[] args=full?new Object[]{name,binder,false,8}:new Object[]{name,binder};
m.call(full?addFull:add,args,()->{
if(name.equals("zybos_manager"))check(binder!=null);
publications++;return null;
});
}
static Object create(XposedModule m) throws Throwable {
return m.call(create,new Object[]{null},()->ZybosSysApiService.create(null));
}
static void boot(boolean enabled,boolean toggle) throws Throwable {
ConfigManager.enabled=enabled;
int before=ZybosSysApiService.constructors;
int beforePub=publications;
XposedModule m=new XposedModule();
ZybosSysApiServiceHooks.hookSystemServer(m,RemovalCheck.class.getClassLoader());
check(m.deoptimized.contains(SystemServer.class.getMethod("startOtherServices")));
Object service=create(m);
if(toggle)ConfigManager.enabled=!enabled;
publish(m,"zybos_manager",(IBinder)service,false);
publish(m,"zybos_manager",(IBinder)service,true);
check((service==null)==enabled);
check(ZybosSysApiService.constructors-before==(enabled?0:1));
check(ZybosSysApiService.receivers==ZybosSysApiService.constructors);
check(publications-beforePub==(enabled?0:2));
// Existing/stray binders cannot be republished either; other services still work.
publish(m,"zybos_manager",new IBinder(){},true);
check(publications-beforePub==(enabled?0:3));
publish(m,"activity",new IBinder(){},false);
check(publications-beforePub==(enabled?1:4));
check((create(m)==null)==enabled);
}
public static void main(String[] args) throws Throwable {
create=ZybosSysApiService.class.getMethod("create",Object.class);
add=ServiceManager.class.getMethod("addService",String.class,IBinder.class);
addFull=ServiceManager.class.getMethod("addService",String.class,IBinder.class,boolean.class,int.class);
check(!HookConfig.defaultEnabled(HookConfig.KEY_REMOVE_ZYBOS_SYS_API_SERVICE));
check(Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_REMOVE_ZYBOS_SYS_API_SERVICE));
boot(false,false);boot(true,false);boot(false,true);boot(true,true);
// Unrelated publication must not sample the preference before the factory runs.
XposedModule late=new XposedModule();ConfigManager.enabled=false;
ZybosSysApiServiceHooks.hookSystemServer(late,RemovalCheck.class.getClassLoader());
publish(late,"unrelated",new IBinder(){},true);
ConfigManager.enabled=true;check(create(late)==null);
// Partial installation must never suppress creation or publish a null binder.
for(String failure:new String[]{"addService","create"}){
XposedModule broken=new XposedModule();broken.fail=failure;ConfigManager.enabled=true;
ZybosSysApiServiceHooks.hookSystemServer(broken,RemovalCheck.class.getClassLoader());
Object service=create(broken);check(service!=null);
int before=publications;publish(broken,"zybos_manager",(IBinder)service,false);
check(publications==before+1);
}
XposedModule absent=new XposedModule();
ZybosSysApiServiceHooks.hookSystemServer(absent,new ClassLoader(RemovalCheck.class.getClassLoader()){
@Override public Class<?> loadClass(String name)throws ClassNotFoundException{
if(name.equals("com.android.server.ZybosSysApiService"))throw new ClassNotFoundException(name);
return super.loadClass(name);
}});
check(absent.hooks.isEmpty());
System.out.println("ZybosSysApiService removal checks passed");
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
    files = list(base.rglob('*.java')) + [actual / 'HookConfig.java', actual / 'hooks/ZybosSysApiServiceHooks.java']
    subprocess.run(['javac', '-d', str(base / 'classes'), *map(str, files)], check=True)
    subprocess.run(['java', '-cp', str(base / 'classes'), 'RemovalCheck'], check=True)
