from pathlib import Path
import subprocess
import tempfile

methods = ['queryUsageStats', 'queryConfigurationStats', 'queryEventStats', 'queryEvents', 'queryEventsForPackage', 'queryEventsForUser', 'queryEventsForPackageForUser']
stubs = {
'android/os/Binder.java': 'package android.os; public class Binder { public static int pid=42; public static int getCallingPid(){return pid;} }',
'android/util/Log.java': 'package android.util; public class Log { public static int i(String t,String s){return 0;} public static int w(String t,String s){return 0;} }',
'com/lijinmu/arashi/xposed/ConfigManager.java': 'package com.lijinmu.arashi.xposed; public class ConfigManager { public static boolean enabled=true; public static boolean isEnabled(String k){return enabled;} public static boolean shouldKeepBackground(String p){return false;} }',
'com/lijinmu/arashi/xposed/HookConfig.java': 'package com.lijinmu.arashi.xposed; public class HookConfig { public static String KEY_BLOCK_SUPERVISE="control"; }',
'com/lijinmu/arashi/xposed/ProcUtil.java': 'package com.lijinmu.arashi.xposed; public class ProcUtil { public static boolean isProcess(int pid,String p){return pid==42 && p.equals("com.zuoyebang.iot.pad.supervise");} }',
'com/lijinmu/arashi/xposed/Reflect.java': 'package com.lijinmu.arashi.xposed; public class Reflect { public static Object defaultReturnValue(Class<?> c){return c==boolean.class?false:null;} public static java.lang.reflect.Method findMethodExact(Class<?> c,String n,Class<?>...p){try{return c.getDeclaredMethod(n,p);}catch(Exception e){return null;}} }',
'io/github/libxposed/api/XposedModule.java': '''package io.github.libxposed.api; import java.lang.reflect.*; import java.util.*; public class XposedModule {
 public Map<Method,Interceptor> hooks=new HashMap<>(); public interface Interceptor {Object call(Chain c) throws Throwable;}
 public static class Chain {public int calls; public Object getArg(int i){return "test";} public Object proceed(){calls++;return "original";}}
 public Builder hook(Method m){return new Builder(m);} public class Builder {Method m; Builder(Method m){this.m=m;} public void intercept(Interceptor i){hooks.put(m,i);}}
}''',
'com/android/server/usage/UsageStatsService.java': 'package com.android.server.usage; public class UsageStatsService {public static class BinderService {' + ''.join('public Object '+m+'(long start,long end,String pkg){return null;} public Object '+m+'(long start,long end,String pkg,int user){return null;}' for m in methods) + 'public Object unrelated(){return null;}}}',
'Check.java': '''import com.lijinmu.arashi.xposed.hooks.ControlHooks; import com.lijinmu.arashi.xposed.ConfigManager; import io.github.libxposed.api.XposedModule; public class Check {
public static void main(String[] args)throws Throwable { XposedModule mod=new XposedModule(); ControlHooks.hookSystemServer(mod,Check.class.getClassLoader());
if(mod.hooks.size()!=14)throw new AssertionError("missing query overload or unexpected hook");
for(var hook:mod.hooks.values()) {
 ConfigManager.enabled=true; android.os.Binder.pid=42; var c=new XposedModule.Chain(); if(hook.call(c)!=null||c.calls!=0)throw new AssertionError("not blocked");
 android.os.Binder.pid=99; c=new XposedModule.Chain(); if(!"original".equals(hook.call(c))||c.calls!=1)throw new AssertionError("other caller blocked");
 ConfigManager.enabled=false; android.os.Binder.pid=42; c=new XposedModule.Chain(); if(!"original".equals(hook.call(c))||c.calls!=1)throw new AssertionError("disabled still blocks");
} System.out.println("PASS: 14 query overloads, caller isolation and disabled passthrough");}}
'''
}
with tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp)
    for name,content in stubs.items():
        p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
    source=Path(__file__).resolve().parents[1]/'app/src/main/java/com/lijinmu/arashi/xposed/hooks/ControlHooks.java'
    subprocess.run(['javac','-d',str(root/'classes'),str(source),*map(str,root.rglob('*.java'))],check=True)
    subprocess.run(['java','-cp',str(root/'classes'),'Check'],check=True)
