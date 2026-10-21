"""Exercise the actual hook against the distinct Android 12/14 sample interfaces."""
from pathlib import Path
import ast
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
tree = ast.parse((ROOT / 'tests/check_navigation_access.py').read_text())
node = next(n.value for n in tree.body if isinstance(n, ast.Assign)
            and any(isinstance(t, ast.Name) and t.id == 'STUBS' for t in n.targets))
base = ast.literal_eval(node)
stubs = {n: base[n] for n in ['io/github/libxposed/api/XposedInterface.java', 'io/github/libxposed/api/XposedModule.java']}
stubs.update({
'android/util/Log.java': 'package android.util; public class Log {public static int i(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}}',
'android/os/Bundle.java': 'package android.os;public class Bundle {}',
'android/R.java': 'package android; public class R {public static class style {public static int TextAppearance_DeviceDefault_Large=1,TextAppearance_DeviceDefault_Small=2;}}',
'android/util/DisplayMetrics.java': 'package android.util;public class DisplayMetrics {public float density=1;}',
'android/content/res/Resources.java': 'package android.content.res;public class Resources {public static boolean available=true;public int getIdentifier(String n,String t,String p){if(!available)return 0;return switch(n){case "ic_settings_delete"->1;case "ic_settings_force_stop"->2;case "force_stop"->3;case "uninstall_text"->4;case "disable_text"->5;case "enable_text"->6;case "ic_settings_disable"->7;case "ic_settings_enable"->8;default->0;};}public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}',
'android/content/Context.java': 'package android.content;public class Context {public android.content.res.Resources getResources(){return new android.content.res.Resources();}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}}',
'android/content/pm/PackageManager.java': 'package android.content.pm;public class PackageManager {}',
'android/content/pm/ApplicationInfo.java': 'package android.content.pm;public class ApplicationInfo {public static int FLAG_SYSTEM=1;public int flags,enabledSetting;public boolean enabled=true;public CharSequence loadLabel(PackageManager p){return "App";}public android.graphics.drawable.Drawable loadIcon(PackageManager p){return new android.graphics.drawable.Drawable();}}',
'android/content/pm/PackageInfo.java': 'package android.content.pm;public class PackageInfo {public String versionName="1.0";}',
'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable;public class Drawable {}',
'android/view/View.java': 'package android.view;public class View {public interface OnClickListener {void onClick(View v);}}',
'android/view/Gravity.java': 'package android.view;public class Gravity {public static int CENTER_HORIZONTAL=1,CENTER=2;}',
'android/widget/LinearLayout.java': 'package android.widget;public class LinearLayout extends android.view.View {public static int VERTICAL=1;public LinearLayout(android.content.Context c){}public void setOrientation(int v){}public void setGravity(int v){}public void setPadding(int a,int b,int c,int d){}public void addView(android.view.View v){}public void addView(android.view.View v,LayoutParams p){}public static class LayoutParams {public LayoutParams(int a,int b){}}}',
'android/widget/ImageView.java': 'package android.widget;public class ImageView extends android.view.View {public ImageView(android.content.Context c){}public void setImageDrawable(android.graphics.drawable.Drawable d){}}',
'android/widget/TextView.java': 'package android.widget;public class TextView extends android.view.View {public TextView(android.content.Context c){}public void setTextAppearance(int i){}public void setGravity(int i){}public void setPadding(int a,int b,int c,int d){}public void setText(CharSequence c){}}',
'androidx/preference/Preference.java': '''package androidx.preference;public class Preference {
 public String key;public int order=-100;public boolean visible=true;public void setKey(String k){key=k;}public void setOrder(int o){order=o;}
 public int getOrder(){return order;}public void setSelectable(boolean b){}public void setVisible(boolean b){visible=b;}
}''',
'androidx/preference/PreferenceScreen.java': '''package androidx.preference;import java.util.*;public class PreferenceScreen extends Preference {
 public Map<String,Preference> items=new HashMap<>();public boolean rejectAdd,rejectActions;
 public Preference findPreference(CharSequence k){return items.get(k.toString());}
 public boolean addPreference(Preference p){if(rejectAdd||(rejectActions&&"arashi_app_actions".equals(p.key)))return false;items.put(p.key,p);return true;}
 public boolean removePreference(Preference p){return items.remove(p.key)!=null;}
}''',
'com/android/settingslib/widget/LayoutPreference.java': 'package com.android.settingslib.widget;public class LayoutPreference extends androidx.preference.Preference {public LayoutPreference(android.content.Context c,android.view.View v){}}',
'com/android/settings/R.java': '''package com.android.settings;public class R {
 public static class drawable {public static int ic_settings_delete=1,ic_settings_force_stop=2,ic_settings_disable=7,ic_settings_enable=8;}
 public static class string {public static int force_stop=3,uninstall_text=4,disable_text=5,enable_text=6;}
}''',
'com/lijinmu/arashi/xposed/ConfigManager.java': 'package com.lijinmu.arashi.xposed;public class ConfigManager {public static boolean enabled=true;public static boolean isEnabled(String k){return enabled;}}',
'com/android/settings/applications/appinfo/AppInfoDashboardFragment.java': '''package com.android.settings.applications.appinfo;
public class AppInfoDashboardFragment {public Object mAppButtonsPreferenceController;public androidx.preference.PreferenceScreen screen=new androidx.preference.PreferenceScreen();
 public void onResume(){} public boolean refreshUi(){return true;} public void onCreatePreferences(android.os.Bundle b,String s){}public androidx.preference.PreferenceScreen getPreferenceScreen(){return screen;}
 public android.content.Context getContext(){return new android.content.Context();}
}''',
})
actions = '''package com.android.settingslib.widget;public class ActionButtonsPreference extends androidx.preference.Preference {
 public int text2,text3;public boolean enabled2,enabled3;public android.view.View.OnClickListener listener2,listener3;
 public ActionButtonsPreference(android.content.Context c){}
'''
for n in range(1, 5):
    actions += f'public void setButton{n}Visible(boolean b){{}}\n'
for n in [2, 3]:
    actions += f'''public void setButton{n}Icon(int i){{}}
 public void setButton{n}Text(int i){{text{n}=i;}}
 public void setButton{n}Enabled(boolean b){{enabled{n}=b;}}
 public void setButton{n}OnClickListener(android.view.View.OnClickListener l){{listener{n}=l;}}
'''
stubs['com/android/settingslib/widget/ActionButtonsPreference.java'] = actions + '}'
for modern in [False, True]:
    name = 'ZybAppStoreDetailHeaderPreference' if modern else 'AppStoreDetailHeaderPreference'
    package = 'com.android.settings.zyb.widget' if modern else 'com.android.settings.widget'
    uninstall = 'enableUninstall' if modern else 'bEnableInUnstall'
    stop = 'enableForceStop' if modern else 'bEnableForceStop'
    set_uninstall = 'setUninstallButtonStatus' if modern else 'setInUnstallButton'
    set_stop = 'setForceStopButtonStatus' if modern else 'setForceStopButton'
    stubs[package.replace('.', '/') + '/' + name + '.java'] = f'''package {package};public class {name} extends androidx.preference.Preference {{
 public boolean {uninstall}=true,{stop}=false;public android.view.View.OnClickListener uninstallListener,forceStopListener;
 public void {set_uninstall}(boolean b){{{uninstall}=b;}}public void {set_stop}(boolean b){{{stop}=b;}}
 public void refreshHeader(String version,Object entry){{}}
}}'''
stubs['Check.java'] = '''import java.lang.reflect.*;
import android.content.pm.*;import android.view.*;import androidx.preference.*;import com.android.settingslib.widget.*;
import com.android.settings.applications.appinfo.*;import com.lijinmu.arashi.xposed.*;import com.lijinmu.arashi.xposed.hooks.*;import io.github.libxposed.api.*;
public class Check {
 static XposedModule module=new XposedModule();static int uninstalls,stops;
 public static class Entry {public ApplicationInfo info=new ApplicationInfo();public String label="Target";}
 public static class Controller {public Object mButtonsPref;public Entry mAppEntry=new Entry();public PackageInfo mPackageInfo=new PackageInfo();private boolean isDisabledUntilUsed(){return mAppEntry.info.enabledSetting==4;}}
 static void check(boolean b){if(!b)throw new AssertionError();}
 static Object call(Method m,Object self,Object[] args)throws Throwable {
  XposedInterface.Next original=a->m.invoke(self,a);
  return module.hooks.containsKey(m)?module.hooks.get(m).intercept(new XposedInterface.Chain(self,args,original)):original.run(args);
 }
 static Method create;
 static void run(boolean modern)throws Throwable {
  String name=modern?"com.android.settings.zyb.widget.ZybAppStoreDetailHeaderPreference":"com.android.settings.widget.AppStoreDetailHeaderPreference";
  Class<?> type=Class.forName(name);Preference header=(Preference)type.getConstructor().newInstance();header.setKey("app_store_detail_header");
  Controller controller=new Controller();controller.mButtonsPref=header;
  type.getField("uninstallListener").set(header,(View.OnClickListener)v->uninstalls++);
  type.getField("forceStopListener").set(header,(View.OnClickListener)v->stops++);
  AppInfoDashboardFragment page=new AppInfoDashboardFragment();page.mAppButtonsPreferenceController=controller;page.screen.addPreference(header);
  ConfigManager.enabled=false;call(create,page,new Object[]{null,null});check(page.screen.items.size()==1&&header.visible);
  ConfigManager.enabled=true;call(create,page,new Object[]{null,null});
  ActionButtonsPreference actions=(ActionButtonsPreference)page.screen.items.get("arashi_app_actions");
  check(actions!=null&&!header.visible&&actions.text2==4&&actions.enabled2&&!actions.enabled3);
  call(create,page,new Object[]{null,null});check(page.screen.items.size()==3);
  int old=uninstalls;actions.listener2.onClick(new View());check(uninstalls==old+1);
  old=stops;actions.listener3.onClick(new View());check(stops==old);
  Method set=type.getMethod(modern?"setForceStopButtonStatus":"setForceStopButton",boolean.class);
  check(module.hooks.containsKey(set));call(set,header,new Object[]{true});check(actions.enabled3);
  actions.listener3.onClick(new View());check(stops==old+1);
  controller.mAppEntry.info.flags=1;
  Method refresh=type.getMethod("refreshHeader",String.class,Object.class);
  call(refresh,header,new Object[]{"1.0",controller.mAppEntry});check(actions.text2==5);
  controller.mAppEntry.info.enabled=false;call(refresh,header,new Object[]{"1.0",controller.mAppEntry});check(actions.text2==6);
  controller.mAppEntry.info.enabled=true;controller.mAppEntry.info.enabledSetting=4;
  call(refresh,header,new Object[]{"1.0",controller.mAppEntry});check(actions.text2==6);
  Method disable=type.getMethod(modern?"setUninstallButtonStatus":"setInUnstallButton",boolean.class);
  call(disable,header,new Object[]{false});check(!actions.enabled2);old=uninstalls;actions.listener2.onClick(new View());check(uninstalls==old);
  controller.mPackageInfo=null;call(refresh,header,new Object[]{"",null});
  check(header.visible&&page.screen.items.size()==1);old=stops;actions.listener3.onClick(new View());check(stops==old);
  controller.mPackageInfo=new PackageInfo();page.screen.rejectAdd=true;
  call(create,page,new Object[]{null,null});check(header.visible&&page.screen.items.size()==1);
  page.screen.rejectAdd=false;page.screen.rejectActions=true;
  call(create,page,new Object[]{null,null});check(header.visible&&page.screen.items.size()==1);page.screen.rejectActions=false;
  com.android.settings.R.string.force_stop=0;android.content.res.Resources.available=false;
  call(create,page,new Object[]{null,null});check(header.visible&&page.screen.items.size()==1);
  com.android.settings.R.string.force_stop=3;android.content.res.Resources.available=true;
 }
 public static void main(String[] args)throws Throwable {
  check(!HookConfig.defaultEnabled(HookConfig.KEY_AOSP_APP_DETAILS));
  check(java.util.Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_AOSP_APP_DETAILS));
  AppDetailsHooks.hookSettings(module,Check.class.getClassLoader());
  create=AppInfoDashboardFragment.class.getMethod("onCreatePreferences",android.os.Bundle.class,String.class);
  check(module.hooks.containsKey(create));
  // Android 12 retains these resources but has removed their generated R fields.
  com.android.settings.R.drawable.ic_settings_force_stop=0;com.android.settings.R.drawable.ic_settings_disable=0;com.android.settings.R.drawable.ic_settings_enable=0;
  com.android.settings.R.string.uninstall_text=0;run(false);
  com.android.settings.R.drawable.ic_settings_force_stop=2;com.android.settings.R.drawable.ic_settings_disable=7;com.android.settings.R.drawable.ic_settings_enable=8;
  com.android.settings.R.string.uninstall_text=4;run(true);
  AppInfoDashboardFragment late=new AppInfoDashboardFragment();Controller lateController=new Controller();
  var lateHeader=new com.android.settings.widget.AppStoreDetailHeaderPreference();lateHeader.setKey("app_store_detail_header");
  lateHeader.uninstallListener=v->{};lateHeader.forceStopListener=v->{};late.screen.addPreference(lateHeader);late.mAppButtonsPreferenceController=lateController;
  call(create,late,new Object[]{null,null});check(late.screen.items.size()==1);
  lateController.mButtonsPref=lateHeader;call(AppInfoDashboardFragment.class.getMethod("onResume"),late,new Object[]{});
  check(late.screen.items.containsKey("arashi_app_actions")&&!lateHeader.visible);
  AppInfoDashboardFragment unrelated=new AppInfoDashboardFragment();call(create,unrelated,new Object[]{null,null});check(unrelated.screen.items.isEmpty());
  System.out.println("App details: Android 12/14 callbacks, state changes, gating and fallback passed");
 }
}'''
with tempfile.TemporaryDirectory() as directory:
    temp = Path(directory)
    for name, content in stubs.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    sources = list(temp.rglob('*.java')) + [ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed' / name for name in ['Reflect.java', 'HookConfig.java', 'hooks/AppDetailsHooks.java']]
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(p) for p in sources], check=True)
    subprocess.run(['java', '-cp', str(temp / 'classes'), 'Check'], check=True)
