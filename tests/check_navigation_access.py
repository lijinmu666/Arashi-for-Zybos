"""Exercise actual navigation hooks, including argument preservation and disabled mode."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/util/Log.java": """package android.util; public class Log {
 public static int i(String t,String m){return 0;} public static int w(String t,String m){return 0;}
 public static int w(String t,String m,Throwable e){return 0;} }""",
    "android/os/IBinder.java": "package android.os; public interface IBinder {}",
    "android/content/ContentResolver.java": "package android.content; public class ContentResolver {}",
    "android/app/Application.java": "package android.app; public class Application {}",
    "android/provider/Settings.java": """package android.provider; public class Settings {
 public static class Global { public static int getInt(android.content.ContentResolver r,String k){return 1;}
 public static int getInt(android.content.ContentResolver r,String k,int d){return 1;}
 public static int getInt(String r,String k,int d){return 99;} } }""",
    "com/arashi/xposed/ConfigManager.java": """package com.arashi.xposed; public class ConfigManager {
 public static boolean enabled; public static boolean isEnabledCached(String k){return enabled;} }""",
    "io/github/libxposed/api/XposedInterface.java": """package io.github.libxposed.api; import java.util.*;
 public interface XposedInterface {
 interface Hooker {Object intercept(Chain c)throws Throwable;}
 interface Next {Object run(Object[] args)throws Throwable;}
 class Chain {Object self;Object[] args;Next next;
 public Chain(Object s,Object[] a,Next n){self=s;args=a;next=n;}
 public Object getThisObject(){return self;} public Object getArg(int i){return args[i];}
 public List<Object> getArgs(){return Arrays.asList(args);}
 public Object proceed()throws Throwable{return next.run(args);}
 public Object proceed(Object[] a)throws Throwable{return next.run(a);} } }""",
    "io/github/libxposed/api/XposedModule.java": """package io.github.libxposed.api; import java.lang.reflect.*;import java.util.*;
 public class XposedModule { public Map<Method,XposedInterface.Hooker> hooks=new HashMap<>();
 public class Builder {Method m;Builder(Method m){this.m=m;} public void intercept(XposedInterface.Hooker h){hooks.put(m,h);}}
 public Builder hook(Method m){return new Builder(m);} public boolean deoptimize(Method m){return true;} }""",
    "com/android/server/statusbar/StatusBarManagerService.java": """package com.android.server.statusbar;
 public class StatusBarManagerService {
 public void disableLocked(int display,int user,int flags,android.os.IBinder token,String pkg,int which){}
 public void disableLocked(String other){} }""",
    "com/android/systemui/statusbar/CommandQueue.java": """package com.android.systemui.statusbar;
 public class CommandQueue {public void disable(int d,int a,int b){} public void disable(int d,int a,int b,boolean animate){} }""",
    "com/android/systemui/statusbar/policy/DeviceProvisionedControllerImpl.java": """package com.android.systemui.statusbar.policy;
 public class DeviceProvisionedControllerImpl { public boolean isDeviceProvisioned(){return false;}
 public boolean isCurrentUserSetup(){return false;} }""",
    "com/android/systemui/navigationbar/NavigationBar.java": """package com.android.systemui.navigationbar;
 public class NavigationBar {public boolean shouldDisableNavbarGestures(){return true;} }""",
    "com/android/launcher3/g5.java": "package com.android.launcher3; public class g5 {public static Boolean s=false,t=true;}",
    "com/android/launcher3/g6.java": "package com.android.launcher3; public class g6 {public static Boolean s=false,t=true;}",
    "com/zuoyebang/iot/pad/launcher/w0.java": """package com.zuoyebang.iot.pad.launcher;
 public class w0 {public static void a(android.app.Application a){} }""",
    "com/zuoyebang/iot/pad/launcher/ErrorActivity.java": """package com.zuoyebang.iot.pad.launcher;
 public class ErrorActivity {public void onCreate(Object state){} public void onResume(){} }""",
    "com/android/systemui/shared/recents/IOverviewProxy$Stub.java": """package com.android.systemui.shared.recents;
 public class IOverviewProxy$Stub {public boolean onTransact(int code,Object in,Object out,int flags){return true;} }""",
    "com/android/quickstep/TouchInteractionService.java": "package com.android.quickstep; public class TouchInteractionService {}",
    "com/android/launcher3/q0.java": "package com.android.launcher3; public class q0 {public void accept(Object a){} }",
    "com/android/launcher3/views/BaseDragLayer.java": "package com.android.launcher3.views; public class BaseDragLayer {public boolean onInterceptTouchEvent(Object e){return false;} }",
}
CHECK = r"""import java.lang.reflect.*;
import java.util.*;
import com.arashi.xposed.*;
import com.arashi.xposed.hooks.NavigationAccessHooks;
import io.github.libxposed.api.*;
public class NavigationCheck {
 static XposedModule module=new XposedModule();
 static Object call(Method m,Object[] args,XposedInterface.Next next)throws Throwable {
  if(!module.hooks.containsKey(m))throw new AssertionError("Missing hook: "+m);
  return module.hooks.get(m).intercept(new XposedInterface.Chain(null,args,next));
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
 static StackTraceElement frame(String c,String m){return new StackTraceElement(c,m,"test",1);}
 public static void main(String[] args)throws Throwable {
  ClassLoader loader=NavigationCheck.class.getClassLoader();
  NavigationAccessHooks.hookSystemServer(module,loader);
  NavigationAccessHooks.hookSystemUI(module,loader);
  NavigationAccessHooks.hookLauncher(module,loader,true);
  ConfigManager.enabled=true;
  Method legacyError=Class.forName("com.zuoyebang.iot.pad.launcher.ErrorActivity").getMethod("onResume");
  call(legacyError,new Object[]{},a->null);
  check(com.android.launcher3.g5.s&&!com.android.launcher3.g5.t);
  module=new XposedModule();
  NavigationAccessHooks.hookSystemServer(module,loader);
  NavigationAccessHooks.hookSystemUI(module,loader);
  NavigationAccessHooks.hookLauncher(module,loader,false);
  check(!HookConfig.defaultEnabled(HookConfig.KEY_KEEP_NAVIGATION));
  check(Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_KEEP_NAVIGATION));
  for(int bit=0;bit<32;bit++) {
   int f=1<<bit;
   check(NavigationAccessPolicy.disable1(f)==((f&0x03610000)!=0?0:f));
   check(NavigationAccessPolicy.disable2(f)==((f&5)!=0?0:f));
  }
  check(NavigationAccessPolicy.isNavigationUiCaller(new StackTraceElement[]{frame("com.android.systemui.statusbar.phone.StatusBar$1","onUserSetupChanged")}));
  check(!NavigationAccessPolicy.isNavigationUiCaller(new StackTraceElement[]{frame("com.android.systemui.keyguard.KeyguardViewMediator","onSystemReady")}));
  check(NavigationAccessPolicy.isNavigationKeyCaller(new StackTraceElement[]{frame("com.android.server.policy.PhoneWindowManager","launchHomeFromHotKey")}));
  check(!NavigationAccessPolicy.isNavigationKeyCaller(new StackTraceElement[]{frame("com.android.server.policy.PhoneWindowManager","cameraGesture")}));
  Object token=new android.os.IBinder(){};
  Method server=Class.forName("com.android.server.statusbar.StatusBarManagerService").getMethod("disableLocked",int.class,int.class,int.class,android.os.IBinder.class,String.class,int.class);
  Object[] input={2,10,-1,token,"vendor",1};
  ConfigManager.enabled=true;
  call(server,input,a->{check(a[0].equals(2)&&a[1].equals(10)&&a[3]==token&&a[4].equals("vendor")&&a[5].equals(1));check(a[2].equals(~0x03610000));return null;});
  check(input[2].equals(-1));
  input[5]=2;
  call(server,input,a->{check(a[2].equals(~5));return null;});
  input[5]=3;
  call(server,input,a->{check(a==input);return null;});
  ConfigManager.enabled=false;
  input[5]=1;
  call(server,input,a->{check(a==input&&a[2].equals(-1));return null;});
  Method command=Class.forName("com.android.systemui.statusbar.CommandQueue").getMethod("disable",int.class,int.class,int.class,boolean.class);
  ConfigManager.enabled=true;
  call(command,new Object[]{4,-1,-1,true},a->{check(a[0].equals(4)&&a[1].equals(~0x03610000)&&a[2].equals(~5)&&a[3].equals(true));return null;});
  Method setting=android.provider.Settings.Global.class.getMethod("getInt",android.content.ContentResolver.class,String.class,int.class);
  check(call(setting,new Object[]{null,"control_panelbar",7},a->{throw new AssertionError();}).equals(0));
  check(call(setting,new Object[]{null,"device_provisioned",7},a->8).equals(8));
  Method setup=Class.forName("com.android.systemui.statusbar.policy.DeviceProvisionedControllerImpl").getMethod("isDeviceProvisioned");
  check(call(setup,new Object[]{},a->false).equals(false)); // No global provisioning spoof
  Method error=Class.forName("com.zuoyebang.iot.pad.launcher.ErrorActivity").getMethod("onResume");
  call(error,new Object[]{},a->{com.android.launcher3.g5.s=false;com.android.launcher3.g6.s=false;return null;});
  check(com.android.launcher3.g6.s);
  check(!com.android.launcher3.g6.t);
  ConfigManager.enabled=false;
  call(error,new Object[]{},a->{com.android.launcher3.g5.s=false;com.android.launcher3.g6.s=false;return null;});
  check(!com.android.launcher3.g5.s&&!com.android.launcher3.g6.s);
  ConfigManager.enabled=true;
  Method accept=com.android.launcher3.q0.class.getMethod("accept",Object.class);
  call(accept,new Object[]{"unrelated"},a->{check(!com.android.launcher3.g6.s);return null;});
  call(accept,new Object[]{new com.android.quickstep.TouchInteractionService()},a->{check(com.android.launcher3.g6.s);return null;});
  System.out.println("Navigation access hook checks passed");
 }
}
"""

with tempfile.TemporaryDirectory(prefix="arashi-navigation-") as directory:
    tmp = Path(directory)
    for name, source in STUBS.items():
        target = tmp / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source)
    (tmp / "NavigationCheck.java").write_text(CHECK)
    real = ROOT / "app/src/main/java/com/arashi/xposed"
    sources = [*tmp.rglob("*.java"), real / "HookConfig.java",
               real / "NavigationAccessPolicy.java", real / "hooks/NavigationAccessHooks.java"]
    subprocess.run(["javac", "-d", str(tmp / "classes"), *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", str(tmp / "classes"), "NavigationCheck"], check=True)
