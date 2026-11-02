"""Exercise the real hook against a bounded model of the supplied Zybos 14 WMS.

Models readiness gates and the verified message 101 / 60000 ms vendor path.
This is a host regression check, not a device boot test.
"""
from pathlib import Path
import ast
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
node = next(n.value for n in ast.parse((ROOT / 'tests/check_navigation_access.py').read_text()).body
            if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'STUBS' for t in n.targets))
base = ast.literal_eval(node)
stubs = {key: base[key] for key in ['io/github/libxposed/api/XposedInterface.java',
                                  'io/github/libxposed/api/XposedModule.java', 'android/util/Log.java',
                                  'com/lijinmu/arashi/xposed/ConfigManager.java']}
stubs.update({
'android/os/Handler.java': '''package android.os;import java.util.*;public class Handler {
 public Map<Integer,Long> pending=new HashMap<>();
 public boolean hasMessages(int id){return pending.containsKey(id);}
 public void removeMessages(int id){pending.remove(id);}
 public boolean sendEmptyMessageDelayed(int id,long delay){pending.put(id,delay);return true;}}
''',
'android/os/SystemProperties.java': '''package android.os;public class SystemProperties {
 public static boolean fail;public static int writes;public static String value;
 public static void set(String key,String v){if(fail)throw new SecurityException();
 if(!key.equals("service.bootanim.exit"))throw new AssertionError(key);writes++;value=v;}}
''',
'com/android/server/wm/WindowManagerService.java': '''package com.android.server.wm;
 public class WindowManagerService {
 public boolean mDelayedExitAnimation,mBootAnimationStopped,mSystemBooted,mDisplayEnabled;
 public H mH=new H();public final Object mGlobalLock=new Object();
 public boolean windowsReady=true,policyReady=true,surfaceReady=true;public int originalCalls;
 public void performEnableScreen(){originalCalls++;synchronized(mGlobalLock){
 if(mDisplayEnabled||!mSystemBooted||!policyReady||!windowsReady)return;
 if(!mBootAnimationStopped){mBootAnimationStopped=true;mDelayedExitAnimation=true;
 mH.sendEmptyMessageDelayed(101,60000L);}
 if(!surfaceReady)return;mDisplayEnabled=true;}}
 public void enableScreenAfterBoot(){}public void performBootTimeout(){}
 public void showBootMessage(CharSequence c,boolean b){}
 public class H extends android.os.Handler {public void handleMessage(Object m){}}
 }
''',
'Check.java': '''import java.lang.reflect.*;
import io.github.libxposed.api.*;import com.lijinmu.arashi.xposed.*;
import com.lijinmu.arashi.xposed.hooks.*;import com.android.server.wm.*;
import android.os.SystemProperties;
public class Check {
 static XposedModule module=new XposedModule();
 static void check(boolean v){if(!v)throw new AssertionError();}
 static void call(WindowManagerService w)throws Throwable {
 Method method=WindowManagerService.class.getDeclaredMethod("performEnableScreen");
 module.hooks.get(method).intercept(new XposedInterface.Chain(w,new Object[0],args->{w.performEnableScreen();return null;}));}
 public static void main(String[] args)throws Throwable {
 BootAnimationHooks.hookSystemServer(module,Check.class.getClassLoader());
 check(module.hooks.size()==1);check(!HookConfig.defaultEnabled(HookConfig.KEY_BOOT_ANIMATION_NO_WAIT));
 ConfigManager.enabled=false;var disabled=new WindowManagerService();disabled.mSystemBooted=true;call(disabled);
 check(SystemProperties.writes==0&&disabled.mH.pending.get(101)==60000L);
 ConfigManager.enabled=true;
 for(int gate=0;gate<4;gate++){
 var w=new WindowManagerService();w.mSystemBooted=gate!=0;w.policyReady=gate!=1;
 w.windowsReady=gate!=2;w.surfaceReady=gate!=3;call(w);
 check(SystemProperties.writes==0&&!w.mDisplayEnabled&&w.originalCalls==1);
 if(gate==3)check(w.mH.hasMessages(101));
 w.mSystemBooted=w.policyReady=w.windowsReady=w.surfaceReady=true;call(w);
 check(SystemProperties.writes==1&&w.mDisplayEnabled&&!w.mH.hasMessages(101));
 call(w);check(SystemProperties.writes==1);SystemProperties.writes=0;}
 var failed=new WindowManagerService();failed.mSystemBooted=true;SystemProperties.fail=true;call(failed);
 check(failed.originalCalls==1&&failed.mH.pending.get(101)==60000L&&SystemProperties.writes==0);
 SystemProperties.fail=false;
 var other=new WindowManagerService();other.mSystemBooted=other.mDisplayEnabled=true;
 other.mBootAnimationStopped=other.mDelayedExitAnimation=true;other.mH.pending.put(77,123L);call(other);
 check(SystemProperties.writes==0&&other.mH.hasMessages(77));
 var originalError=WindowManagerService.class.getDeclaredMethod("performEnableScreen");
 try{module.hooks.get(originalError).intercept(new XposedInterface.Chain(other,new Object[0],a->{throw new IllegalStateException();}));
 throw new AssertionError();}catch(IllegalStateException expected){}
 // Missing vendor field: unsupported firmware must install no hooks.
 var unsupported=new XposedModule();ClassLoader absent=new ClassLoader(Check.class.getClassLoader()){
 public Class<?> loadClass(String name)throws ClassNotFoundException {
 if(name.equals("com.android.server.wm.WindowManagerService"))return Object.class;return super.loadClass(name);}};
 BootAnimationHooks.hookSystemServer(unsupported,absent);check(unsupported.hooks.isEmpty());
 System.out.println("Boot animation: disabled mode, readiness gates, delayed exit, idempotence, property failure fallback, unrelated messages and unsupported firmware passed");
 }}
''',
})
with tempfile.TemporaryDirectory(prefix='arashi-boot-animation-') as directory:
    temp = Path(directory)
    for name, source in stubs.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    files = list(temp.rglob('*.java')) + [ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed' / name
                                         for name in ['HookConfig.java', 'hooks/BootAnimationHooks.java']]
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(p) for p in files], check=True)
    subprocess.run(['java', '-cp', str(temp / 'classes'), 'Check'], check=True)
