"""Run real hooks against unmodified AOSP 12/14 GlobalActions source fixtures.

SystemUI signatures/caller names were checked in the supplied Zybos APKs.
Host tests exercise routing/lifecycle, not firmware UI rendering or power operations.
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
                                  'io/github/libxposed/api/XposedModule.java', 'android/util/Log.java']}
stubs.update({
'android/content/Context.java': 'package android.content;public class Context {}',
'android/util/Slog.java': 'package android.util;public class Slog {public static int d(String a,String b){return 0;}public static int i(String a,String b){return 0;}}',
'android/os/Handler.java': '''package android.os;import java.util.*;
public class Handler {public Set<Runnable> pending=new HashSet<>();public int removals;
 public boolean postDelayed(Runnable r,long delay){pending.add(r);return true;}
 public void removeCallbacks(Runnable r){pending.remove(r);removals++;}}
''',
'android/app/Dialog.java': '''package android.app;public class Dialog {
 public boolean showing=true;public int dismissals;public boolean isShowing(){return showing;}
 public void dismiss(){showing=false;dismissals++;}}
''',
'com/android/server/LocalServices.java': '''package com.android.server;public class LocalServices {
 public static Object service;public static <T>T getService(Class<T> type){return type.cast(service);}}
''',
'com/android/server/policy/WindowManagerPolicy.java': '''package com.android.server.policy;
public class WindowManagerPolicy {public interface WindowManagerFuncs {}}
''',
'com/android/server/policy/GlobalActionsProvider.java': '''package com.android.server.policy;
public interface GlobalActionsProvider {
 boolean isGlobalActionsDisabled();void showGlobalActions();void setGlobalActionsListener(GlobalActionsListener listener);
 interface GlobalActionsListener {void onGlobalActionsShown();void onGlobalActionsDismissed();void onGlobalActionsAvailableChanged(boolean value);}}
''',
'com/android/server/policy/LegacyGlobalActions.java': '''package com.android.server.policy;
public class LegacyGlobalActions {public static int shows;public static boolean fail,keyguard,provisioned;public static Runnable dismissed;
 public LegacyGlobalActions(android.content.Context c,WindowManagerPolicy.WindowManagerFuncs f,Runnable callback){dismissed=callback;}
 public void showDialog(boolean k,boolean p){shows++;keyguard=k;provisioned=p;if(fail)throw new IllegalStateException();}}
''',
'com/android/server/policy/PhoneWindowManager.java': 'package com.android.server.policy;public class PhoneWindowManager {public void showGlobalActionsInternal(){}}',
'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed;public class ConfigManager {
 public static boolean enabled;public static boolean isEnabledCached(String key){return enabled;}}
''',
'android/view/IWindowManager.java': 'package android.view;public interface IWindowManager {void showGlobalActions()throws Throwable;}',
'android/view/WindowManagerGlobal.java': '''package android.view;public class WindowManagerGlobal {
 public static IWindowManager service;public static IWindowManager getWindowManagerService(){return service;}}
''',
'com/android/systemui/animation/Expandable.java': 'package com.android.systemui.animation;public interface Expandable {}',
'com/android/systemui/globalactions/GlobalActionsImpl.java': '''package com.android.systemui.globalactions;
public class GlobalActionsImpl {public void showGlobalActions(Object manager){}}
''',
'com/android/systemui/globalactions/GlobalActionsDialogLite.java': '''package com.android.systemui.globalactions;
public class GlobalActionsDialogLite {public android.app.Dialog mDialog;public int shows;
 public void showOrHideDialog(boolean k,boolean p){shows++;}
 public void showOrHideDialog(boolean k,boolean p,com.android.systemui.animation.Expandable e){shows++;}}
''',
'com/android/systemui/qs/QSFooterViewController$2.java': 'package com.android.systemui.qs;public class QSFooterViewController$2 {public void onClick(Object view){}}',
'com/android/systemui/qs/footer/ui/viewmodel/FooterActionsViewModel$power$1.java': '''package com.android.systemui.qs.footer.ui.viewmodel;
public class FooterActionsViewModel$power$1 {public void invoke(Object view){}}
''',
'com/android/server/policy/Check.java': '''package com.android.server.policy;
import java.lang.reflect.*;import com.android.server.LocalServices;import android.view.*;
import com.lijinmu.arashi.xposed.*;import com.lijinmu.arashi.xposed.hooks.LegacyPowerMenuHooks;import io.github.libxposed.api.*;
public class Check {
 static XposedModule module=new XposedModule();static void check(boolean v){if(!v)throw new AssertionError();}
 static Object field(Object owner,String name)throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static Object call(Method method,Object owner,Object...args)throws Throwable {
  method.setAccessible(true);return module.hooks.get(method).intercept(new XposedInterface.Chain(owner,args,a->method.invoke(owner,a)));}
 public static class Provider implements GlobalActionsProvider {
  public boolean disabled;public int shows;public boolean isGlobalActionsDisabled(){return disabled;}
  public void showGlobalActions(){shows++;}public void setGlobalActionsListener(GlobalActionsListener l){}}
 public static void main(String[] version)throws Throwable {
  check(!HookConfig.defaultEnabled(HookConfig.KEY_LEGACY_POWER_MENU));
  check(java.util.Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_LEGACY_POWER_MENU));
  Provider provider=new Provider();LocalServices.service=provider;
  GlobalActions actions=new GlobalActions(new android.content.Context(),new WindowManagerPolicy.WindowManagerFuncs(){});
  actions.onGlobalActionsAvailableChanged(true);
  LegacyPowerMenuHooks.hookSystemServer(module,Check.class.getClassLoader());
  Method show=GlobalActions.class.getDeclaredMethod("showDialog",boolean.class,boolean.class);
  ConfigManager.enabled=false;call(show,actions,true,false);
  check(provider.shows==1&&LegacyGlobalActions.shows==0);
  var handler=(android.os.Handler)field(actions,"mHandler");check(handler.pending.size()==1);
  ConfigManager.enabled=true;call(show,actions,false,true);
  check(LegacyGlobalActions.shows==1&&!LegacyGlobalActions.keyguard&&LegacyGlobalActions.provisioned);
  check(provider.shows==1&&handler.pending.isEmpty()&&(boolean)field(actions,"mGlobalActionsAvailable"));
  check((boolean)field(actions,"mShowing"));LegacyGlobalActions.dismissed.run();check(!(boolean)field(actions,"mShowing"));
  call(show,actions,true,false);check(LegacyGlobalActions.keyguard&&!LegacyGlobalActions.provisioned);
  LegacyGlobalActions.fail=true;
  try{call(show,actions,true,true);throw new AssertionError();}catch(InvocationTargetException expected){}
  check((boolean)field(actions,"mGlobalActionsAvailable"));LegacyGlobalActions.fail=false;
  int count=LegacyGlobalActions.shows;provider.disabled=true;call(show,actions,true,true);check(count==LegacyGlobalActions.shows);
  provider.disabled=false;actions.onGlobalActionsAvailableChanged(false);call(show,actions,false,false);
  check(!(boolean)field(actions,"mGlobalActionsAvailable"));actions.onGlobalActionsAvailableChanged(true);
  ConfigManager.enabled=false;call(show,actions,false,true);check(provider.shows==2);

  LegacyPowerMenuHooks.hookSystemUI(module,Check.class.getClassLoader());ConfigManager.enabled=true;
  var ui=new com.android.systemui.globalactions.GlobalActionsDialogLite();
  Method uiShow=version[0].equals("12")?ui.getClass().getDeclaredMethod("showOrHideDialog",boolean.class,boolean.class):
   ui.getClass().getDeclaredMethod("showOrHideDialog",boolean.class,boolean.class,com.android.systemui.animation.Expandable.class);
  Object[] uiArgs=version[0].equals("12")?new Object[]{true,false}:new Object[]{true,false,null};
  int[] requests={0};WindowManagerGlobal.service=()->{requests[0]++;call(show,actions,true,false);};
  ui.mDialog=new android.app.Dialog();call(uiShow,ui,uiArgs);
  check(ui.shows==0&&requests[0]==1&&ui.mDialog.dismissals==1);
  call(uiShow,ui,uiArgs);check(ui.shows==0&&requests[0]==2); // repeated successful requests stay legacy
  ConfigManager.enabled=false;call(uiShow,ui,uiArgs);check(ui.shows==1&&requests[0]==2);
  ConfigManager.enabled=true;WindowManagerGlobal.service=null;call(uiShow,ui,uiArgs);check(ui.shows==2);
  WindowManagerGlobal.service=()->{throw new SecurityException();};call(uiShow,ui,uiArgs);check(ui.shows==3);

  // Missing framework hook: re-enter through the real framework-to-SystemUI wrapper.
  var impl=new com.android.systemui.globalactions.GlobalActionsImpl();Method systemCallback=impl.getClass().getDeclaredMethod("showGlobalActions",Object.class);
  WindowManagerGlobal.service=()->{requests[0]++;
   module.hooks.get(systemCallback).intercept(new XposedInterface.Chain(impl,new Object[]{null},a->call(uiShow,ui,uiArgs)));};
  int before=requests[0];call(uiShow,ui,uiArgs);check(requests[0]==before+1&&ui.shows==4); // no Binder loop
  try{module.hooks.get(systemCallback).intercept(new XposedInterface.Chain(impl,new Object[]{null},a->{throw new IllegalStateException();}));
   throw new AssertionError();}catch(IllegalStateException expected){}
  WindowManagerGlobal.service=()->{requests[0]++;call(show,actions,false,true);};
  call(uiShow,ui,uiArgs);check(requests[0]==before+2&&ui.shows==4); // wrapper reset even after error
  // The original UI error must propagate once; it must not be caught and retried by our hook.
  WindowManagerGlobal.service=null;int[] originals={0};
  try{module.hooks.get(uiShow).intercept(new XposedInterface.Chain(ui,uiArgs,a->{originals[0]++;throw new IllegalArgumentException();}));
   throw new AssertionError();}catch(IllegalArgumentException expected){}check(originals[0]==1);
  System.out.println("Legacy power Android "+version[0]+": AOSP routing, policy/args/lifecycle, availability restoration, timeout cancellation, footer redirect and failure/no-loop fallback passed");
 }
}
''',
})
for version in [12, 14]:
    with tempfile.TemporaryDirectory(prefix=f'arashi-legacy-power-{version}-') as directory:
        temp = Path(directory)
        sources = dict(stubs)
        sources['com/android/server/policy/GlobalActions.java'] = (
            ROOT / f'tests/fixtures/global_actions_android{version}.java.txt').read_text()
        for name, source in sources.items():
            path = temp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
        files = list(temp.rglob('*.java')) + [ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed' / name
                                            for name in ['HookConfig.java', 'Reflect.java', 'hooks/LegacyPowerMenuHooks.java']]
        compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
        subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(p) for p in files], check=True)
        subprocess.run(['java', '-cp', str(temp / 'classes'), 'com.android.server.policy.Check', str(version)], check=True)
