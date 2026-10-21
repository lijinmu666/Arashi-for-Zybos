"""Exercise full-tree inflation with constructor contracts recorded from Settings 12/14.

Fixtures contain public signatures/resource IDs only, not sample APK code. This checks
real hook execution, nested inflation isolation, native controller setup, profile UID,
missing-class fallback, and disposal; it does not replace device testing.
"""
from pathlib import Path
import json, re, shutil, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[1]
ns = {'__file__': str(ROOT/'tests/check_app_details.py')}
exec((ROOT/'tests/check_app_details.py').read_text().split('with tempfile.TemporaryDirectory()')[0], ns)
BASE = ns['stubs']
PREFIX = 'com.android.settings.applications.appinfo.'

def params(descriptor):
    values = re.findall(r'L[^;]+;|[IZ]', descriptor.split(')')[0][1:])
    return [v[1:-1].replace('/', '.') if v.startswith('L') else {'I':'int','Z':'boolean'}[v] for v in values]

for version in (12,14):
    fixture = json.loads((ROOT/f'tests/fixtures/app_info_{version}.json').read_text())
    stubs = dict(BASE)
    stubs.pop('Check.java')
    stubs['android/util/AttributeSet.java'] = 'package android.util;public interface AttributeSet {}'
    stubs['android/content/pm/PackageInfo.java'] = '''package android.content.pm;public class PackageInfo {
      public String packageName="target.app",versionName="1";public ApplicationInfo applicationInfo=new ApplicationInfo();}'''
    identifiers = ''.join(f'case "{k}"-> {v};' for k,v in fixture['resources'].items())
    stubs['android/content/res/Resources.java'] = f'''package android.content.res;public class Resources {{
      public int getIdentifier(String n,String t,String p){{return switch(t+"/"+n){{{identifiers}default->0;}};}}
      public android.util.DisplayMetrics getDisplayMetrics(){{return new android.util.DisplayMetrics();}}}}'''
    stubs['androidx/preference/Preference.java'] = '''package androidx.preference;public class Preference {
      public String key;public int order;public boolean visible=true;public Preference(){}public Preference(android.content.Context c){}
      public void setKey(String k){key=k;}public void setOrder(int o){order=o;}public int getOrder(){return order;}
      public void setTitle(int id){}public void setSelectable(boolean b){}public void setVisible(boolean b){visible=b;}
      public void setCopyingEnabled(boolean b){}
    }'''
    stubs['androidx/preference/PreferenceScreen.java'] = '''package androidx.preference;import java.util.*;public class PreferenceScreen extends Preference {
      public Map<String,Preference> items=new LinkedHashMap<>();public PreferenceScreen(){}public PreferenceScreen(android.content.Context c){}
      public Preference findPreference(CharSequence key){for(var p:items.values()){
        if(key.toString().equals(p.key))return p;if(p instanceof PreferenceScreen){var q=((PreferenceScreen)p).findPreference(key);if(q!=null)return q;}}return null;}
      public boolean addPreference(Preference p){items.put(p.key,p);return true;}
      public boolean removePreference(Preference p){return items.remove(p.key)!=null;}
    }'''
    stubs['androidx/preference/PreferenceCategory.java'] = 'package androidx.preference;public class PreferenceCategory extends PreferenceScreen {public PreferenceCategory(android.content.Context c){}}'
    stubs['androidx/preference/SwitchPreference.java'] = 'package androidx.preference;public class SwitchPreference extends Preference {public SwitchPreference(android.content.Context c){}}'
    stubs['com/android/settingslib/core/lifecycle/Lifecycle.java'] = 'package com.android.settingslib.core.lifecycle;public class Lifecycle {}'
    stubs['model/NativeController.java'] = '''package model;import java.util.*;public class NativeController {
      public String key,pkg;public Object parent,owner,entry;public List<?> children;public static int uid;public static String failKey;
      public void setPackageName(String p){if(key.equals(failKey))throw new IllegalStateException("controller setup failed");pkg=p;}public void setPackage(String p){pkg=p;}
      public void setParentFragment(com.android.settings.applications.appinfo.AppInfoDashboardFragment p){parent=p;p.mCallbacks.add(this);}
      public void initLifeCycleOwner(com.android.settings.applications.appinfo.AppInfoDashboardFragment p){owner=p;}
      public void setChildren(List<?> l){children=l;}public void setAppEntry(Object e){entry=e;}
    }'''
    default_keys = dict(zip(['Home','Browser','Phone','Emergency','Sms'], ['default_home','default_browser','default_phone_app','default_emergency_app','default_sms_app']))
    for name, api in fixture['classes'].items():
        if not name.startswith('com.android.settings') or name.endswith(('AppInfoDashboardFragment','AppInfoPreferenceControllerBase')): continue
        package, cls = name.rsplit('.',1)
        widget = cls.endswith('Preference')
        parent = 'androidx.preference.Preference' if widget else 'model.NativeController'
        constructors = ''
        for descriptor in api['constructors']:
            types = params(descriptor)
            args = ','.join(f'{t} a{i}' for i,t in enumerate(types))
            body = ''
            if not widget:
                if len(types)==2 and types[1]=='java.lang.String': body = 'key=a1;pkg=a1;'
                if cls=='AppBatteryPreferenceController':body='key="battery";uid=a3;'
                if cls=='AppMemoryPreferenceController':body='key="memory";'
                if cls.startswith('Default'):
                    word=cls.removeprefix('Default').removesuffix('ShortcutPreferenceController');body=f'key="{default_keys[word]}";pkg=a1;'
            constructors+=f'public {cls}({args}){{{body}}}'
        stubs[name.replace('.','/')+'.java'] = f'package {package};public class {cls} extends {parent} {{{constructors}}}'
    typed = 'ZybAppButtonsPreferenceController' if version==14 else 'AppButtonsPreferenceController'
    stubs[PREFIX.replace('.','/')+'AppInfoDashboardFragment.java'] = f'''package {PREFIX[:-1]};import java.util.*;public class AppInfoDashboardFragment {{
      public {typed} mAppButtonsPreferenceController=new {typed}();
      public android.content.pm.PackageInfo mPackageInfo=new android.content.pm.PackageInfo();public Object entry=new Object();
      public List<Object> mCallbacks=new ArrayList<>(List.of("original"));
      public androidx.preference.PreferenceScreen screen;public android.content.Context context=new android.content.Context();
      public AppInfoDashboardFragment(){{mPackageInfo.applicationInfo.uid=1012345;}}
      public Object getAppEntry(){{return entry;}}public android.content.Context getContext(){{return context;}}
      public com.android.settingslib.core.lifecycle.Lifecycle getSettingsLifecycle(){{return new com.android.settingslib.core.lifecycle.Lifecycle();}}
      public List<Object> createPreferenceControllers(android.content.Context c){{return new ArrayList<>(List.of(mAppButtonsPreferenceController));}}
      public int getPreferenceScreenResId(){{return 99;}}public void onDestroy(){{}}
      public void onCreatePreferences(android.os.Bundle b,String key){{try {{screen=(androidx.preference.PreferenceScreen)model.Host.invoke(
        androidx.preference.PreferenceManager.class.getMethod("inflateFromResource",android.content.Context.class,int.class,androidx.preference.PreferenceScreen.class),
        new androidx.preference.PreferenceManager(),context,model.Host.xml(this),null);}}catch(Throwable e){{throw new RuntimeException(e);}}}}
    }}'''
    stubs['com/android/settings/applications/appinfo/AppDetailTitlePreferenceController.java'] = 'package com.android.settings.applications.appinfo;public class AppDetailTitlePreferenceController {}'
    stubs['com/android/settings/core/PreferenceControllerListHelper.java'] = '''package com.android.settings.core;import java.util.*;public class PreferenceControllerListHelper {
      public static List<Object> getPreferenceControllersFromXml(android.content.Context c,int xml){return List.of(new com.android.settings.applications.appinfo.AppDetailTitlePreferenceController());}
    }'''
    stubs['com/android/settings/dashboard/DashboardFragment.java'] = 'package com.android.settings.dashboard;public class DashboardFragment {public void onAttach(android.content.Context c){}}'
    stubs['androidx/preference/PreferenceManager.java'] = '''package androidx.preference;public class PreferenceManager {
      public PreferenceScreen inflateFromResource(android.content.Context c,int id,PreferenceScreen root){
        var s=new PreferenceScreen();var title=new Preference(c);title.setKey("app_store_title");s.addPreference(title);
        return s;}
    }'''
    stubs['model/Host.java'] = '''package model;import java.lang.reflect.*;import io.github.libxposed.api.*;public class Host {
      public static XposedModule module=new XposedModule();
      public static Object invoke(Method m,Object o,Object...args)throws Throwable {var h=module.hooks.get(m);return h==null?m.invoke(o,args):h.intercept(new XposedInterface.Chain(o,args,a->m.invoke(o,a)));}
      public static int xml(Object f)throws Throwable{return (Integer)invoke(f.getClass().getMethod("getPreferenceScreenResId"),f);}
    }'''
    stubs['Check.java'] = '''import java.lang.reflect.*;import java.util.*;import model.*;import androidx.preference.*;
      import com.android.settings.applications.appinfo.*;import com.lijinmu.arashi.xposed.*;import com.lijinmu.arashi.xposed.hooks.*;
      public class Check {
        static void check(boolean b){if(!b)throw new AssertionError();}
        public static void main(String[] args)throws Throwable{
          AppInfoPageHooks.hookSettings(Host.module,Check.class.getClassLoader());
          var create=AppInfoDashboardFragment.class.getMethod("createPreferenceControllers",android.content.Context.class);
          var inflate=AppInfoDashboardFragment.class.getMethod("onCreatePreferences",android.os.Bundle.class,String.class);
          var off=new AppInfoDashboardFragment();ConfigManager.enabled=false;
          check(((List<?>)Host.invoke(create,off,off.context)).size()==1&&Host.xml(off)==99);
          var page=new AppInfoDashboardFragment();ConfigManager.enabled=true;
          var controllers=(List<?>)Host.invoke(create,page,page.context);
          check(controllers.size()>25&&Host.xml(page)!=99&&NativeController.uid==1012345);
          var metadata=com.android.settings.core.PreferenceControllerListHelper.class.getMethod("getPreferenceControllersFromXml",android.content.Context.class,int.class);
          check(((List<?>)Host.invoke(metadata,null,page.context,Host.xml(page))).isEmpty());
          check(((List<?>)Host.invoke(metadata,null,page.context,99)).size()==1);
          Map<String,NativeController> byKey=new HashMap<>();for(var c:controllers)if(c instanceof NativeController)byKey.put(((NativeController)c).key,(NativeController)c);
          for(String key:List.of("data_settings","app_info_store","system_alert_window","write_settings_apps")){
            check(byKey.get(key).parent==page);
          }
          check(byKey.get("app_installer").children.contains(byKey.get("app_info_store")));
          check(byKey.get("app_hibernation_info").children.contains(byKey.get("hibernation_switch")));
          Host.invoke(inflate,page,null,null);
          for(String key:List.of("data_settings","battery","preferred_settings","app_info_store","system_alert_window","permission_settings","default_browser")){
            check(page.screen.findPreference(key)!=null);
          }
          check(!page.screen.findPreference("app_store_detail_header").visible);
          check(!page.screen.findPreference("app_store_title").visible);
          check(page.screen.findPreference("app_version").order==9999);
          var size=page.screen.items.size();Host.invoke(inflate,page,null,null);check(page.screen.items.size()==size);
          // A subsequent unrelated preference inflation must not inherit the app-info plan.
          var foreign=(PreferenceScreen)Host.invoke(PreferenceManager.class.getMethod("inflateFromResource",android.content.Context.class,int.class,PreferenceScreen.class),new PreferenceManager(),page.context,Host.xml(page),null);
          check(foreign.findPreference("battery")==null);
          Host.invoke(AppInfoDashboardFragment.class.getMethod("onDestroy"),page);check(Host.xml(page)==99);
          var failedSetup=new AppInfoDashboardFragment();NativeController.failKey="app_info_store";
          check(((List<?>)Host.invoke(create,failedSetup,failedSetup.context)).size()==1&&Host.xml(failedSetup)==99);
          check(failedSetup.mCallbacks.equals(List.of("original")));NativeController.failKey=null;
          var broken=new AppInfoDashboardFragment();broken.mPackageInfo=null;
          check(((List<?>)Host.invoke(create,broken,broken.context)).size()==1&&Host.xml(broken)==99);
          System.out.println("Full app info: recorded sample constructors, native setup, profile UID, complete tree, isolation, repeat, fallback and disposal passed");
        }
      }'''
    with tempfile.TemporaryDirectory(prefix=f'arashi-full-info-{version}-') as directory:
        temp=Path(directory)
        for name,content in stubs.items():
            path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
        sources=list(temp.rglob('*.java'))+[ROOT/'app/src/main/java/com/lijinmu/arashi/xposed'/n for n in ['Reflect.java','HookConfig.java','hooks/AppDetailsHooks.java','hooks/AppInfoPageHooks.java']]
        compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        subprocess.run(compiler+['-d',str(temp/'classes')]+[str(p) for p in sources],check=True)
        subprocess.run(['java','-cp',str(temp/'classes'),'Check'],check=True)
        print(f'Settings {version} profile passed',flush=True)
