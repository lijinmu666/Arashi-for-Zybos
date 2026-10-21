"""Check sidebar launch routing and both notification restoration strategies against real hook code."""
from pathlib import Path
import subprocess, shutil, tempfile
ROOT = Path(__file__).resolve().parents[1]
ns = {'__file__': str(ROOT / 'tests/check_app_details.py')}
exec((ROOT / 'tests/check_app_details.py').read_text().split('with tempfile.TemporaryDirectory()')[0], ns)
stubs = ns['stubs']
stubs.update({
'androidx/preference/PreferenceViewHolder.java':'package androidx.preference;public class PreferenceViewHolder {}',
'android/util/AttributeSet.java':'package android.util;public interface AttributeSet {}',
'android/widget/Switch.java':'package android.widget;public class Switch extends android.view.View {}',
'android/content/Intent.java':'''package android.content;import java.util.*;public class Intent {
 public String component;public int flags;public Map<String,Object> extras=new HashMap<>();public Intent(){}public Intent(Intent i){component=i.component;flags=i.flags;extras.putAll(i.extras);}
 public String getStringExtra(String k){return (String)extras.get(k);}public void removeExtra(String k){extras.remove(k);}public Intent setClassName(String p,String n){component=n;return this;}
}''',
'com/lijinmu/arashi/xposed/ConfigManager.java':'''package com.lijinmu.arashi.xposed;public class ConfigManager {
 public static boolean enabled;public static boolean isEnabled(String k){return k.equals(HookConfig.KEY_AOSP_APP_DETAILS)&&enabled;}}''',
'com/android/settings/applications/InstalledAppDetailsTop.java':'package com.android.settings.applications;public class InstalledAppDetailsTop {}',
'com/android/settings/Settings.java':'package com.android.settings;public class Settings {public static class AppNotificationSettingsActivity {}}',
'com/android/settings/notification/NotificationBackend.java':'package com.android.settings.notification;public class NotificationBackend {}',
'com/android/settingslib/widget/OnMainSwitchChangeListener.java':'package com.android.settingslib.widget;public interface OnMainSwitchChangeListener {void onSwitchChanged(android.widget.Switch s,boolean enabled);}',
'com/android/settings/widget/SettingsMainSwitchPreference.java':'''package com.android.settings.widget;
public class SettingsMainSwitchPreference extends androidx.preference.Preference {
 public boolean checked,shown;public Bar mMainSwitchBar=new Bar();public void onBindViewHolder(androidx.preference.PreferenceViewHolder h){}
 public static class Bar {public boolean enabled;public Object admin;public void setEnabled(boolean b){enabled=b;}public void setDisabledByAdmin(Object a){admin=a;}}public java.util.List<com.android.settingslib.widget.OnMainSwitchChangeListener> listeners=new java.util.ArrayList<>();
 public SettingsMainSwitchPreference(android.content.Context c){}public void setTitle(CharSequence s){}
 public void setChecked(boolean b){checked=b;for(var l:listeners)l.onSwitchChanged(new android.widget.Switch(),b);}
 public void show(){shown=true;}public void addOnSwitchChangeListener(com.android.settingslib.widget.OnMainSwitchChangeListener l){listeners.add(l);}
}''',
'com/android/settings/notification/app/NotificationSettings.java':'''package com.android.settings.notification.app;public class NotificationSettings {
 public static class DependentFieldListener {}public DependentFieldListener mDependentFieldListener=new DependentFieldListener();
 public com.android.settings.notification.NotificationBackend mBackend=new com.android.settings.notification.NotificationBackend();public java.util.List<Object> mControllers;
}''',
'com/android/settings/notification/app/AppNotificationSettings.java':'''package com.android.settings.notification.app;import java.util.*;
public class AppNotificationSettings extends NotificationSettings {
 public boolean modern=true;public androidx.preference.PreferenceScreen screen=new androidx.preference.PreferenceScreen();
 public android.content.Context getContext(){return new android.content.Context();}public androidx.preference.PreferenceScreen getPreferenceScreen(){return screen;}
 public List<Object> createPreferenceControllers(android.content.Context c){mControllers=new ArrayList<>();mControllers.add(new HeaderPreferenceController(this));
  if(modern){mControllers.add(new ZybBlockPreferenceController());mControllers.add(new ZybAllowSoundPreferenceController());}
  else {mControllers.add(new BlockPreferenceController(c,mDependentFieldListener,mBackend));}return new ArrayList<>(mControllers);}
 public int getPreferenceScreenResId(){return modern?99:88;}
}''',
'com/android/settings/notification/app/ZybBlockPreferenceController.java':'package com.android.settings.notification.app;public class ZybBlockPreferenceController {}',
'com/android/settings/notification/app/ZybAllowSoundPreferenceController.java':'package com.android.settings.notification.app;public class ZybAllowSoundPreferenceController {}',
'com/android/settings/notification/app/BlockPreferenceController.java':'''package com.android.settings.notification.app;
public class BlockPreferenceController {public android.content.Context mContext;public HeaderPreferenceController.Row mAppRow=new HeaderPreferenceController.Row();
 public Object mChannel,mChannelGroup,mAdmin;public boolean allowed=true;public int writes;
 public BlockPreferenceController(android.content.Context c,NotificationSettings.DependentFieldListener l,com.android.settings.notification.NotificationBackend b){mContext=c;}
 public void updateState(androidx.preference.Preference p){}public boolean isAppBlockable(){return allowed;}public boolean isChannelBlockable(){return allowed;}
 public boolean isChannelGroupBlockable(){return allowed;}public String getSwitchBarText(){return "Allow notifications";}
 public void onValueClick(android.widget.Switch s,Boolean b){writes++;mAppRow.banned=!b;}
}''',
'com/android/settings/notification/app/HeaderPreferenceController.java':'''package com.android.settings.notification.app;
public class HeaderPreferenceController {public AppNotificationSettings mFragment;public Row mAppRow=new Row();public androidx.preference.Preference backing;
 public static class Row {public boolean banned;public android.graphics.drawable.Drawable icon=new android.graphics.drawable.Drawable();}
 public HeaderPreferenceController(AppNotificationSettings p){mFragment=p;}public CharSequence getLabel(){return "Target";}
 public void displayPreference(androidx.preference.PreferenceScreen s){backing=s.findPreference("pref_notice_header");if(backing==null)throw new AssertionError("missing typed backing header");}
 public void updateState(androidx.preference.Preference p){}
}''',
'com/android/settings/zyb/widget/AppNotificationHeaderPreference.java':'package com.android.settings.zyb.widget;public class AppNotificationHeaderPreference extends androidx.preference.Preference {public AppNotificationHeaderPreference(android.content.Context c){}}',
'com/android/settingslib/widget/LayoutPreference.java':'''package com.android.settingslib.widget;public class LayoutPreference extends androidx.preference.Preference {
 public android.widget.TextView title=new android.widget.TextView(new android.content.Context());public android.widget.ImageView icon=new android.widget.ImageView(new android.content.Context());
 public LayoutPreference(android.content.Context c,android.view.View v){}public LayoutPreference(android.content.Context c,int i){}
 public android.view.View findViewById(int id){return id==21?title:icon;}
}''',
})
stubs['androidx/preference/Preference.java'] = stubs['androidx/preference/Preference.java'].replace('public String key;', 'public boolean isVisible(){return visible;}public boolean enabled=true;public void setEnabled(boolean b){enabled=b;}public PreferenceScreen parent;public Object getParent(){return parent;}public String key;')
stubs['androidx/preference/PreferenceScreen.java'] = stubs['androidx/preference/PreferenceScreen.java'].replace('items.put(p.key,p);','items.put(p.key,p);p.parent=this;')
stubs['com/android/settings/R.java'] = stubs['com/android/settings/R.java'].replace('public class R {','public class R {public static class layout {public static int settings_entity_header=20;}public static class id {public static int entity_header_title=21,entity_header_icon=22;}public static class xml {public static int app_notification_settings=23;}')
for name in ['com.android.settings.core.ZybSubSettingLauncher', 'com.android.settings.zyb.ZybSubSettingLauncher', 'com.android.settings.core.SubSettingLauncher']:
    package, cls = name.rsplit('.',1)
    stubs[name.replace('.','/')+'.java'] = f'package {package};public class {cls} {{public android.content.Intent intent=new android.content.Intent();public android.content.Intent toIntent(){{return intent;}}public void launch(){{}}}}'
stubs['Check.java'] = '''import java.lang.reflect.*;import java.util.*;import android.content.*;import androidx.preference.*;
import com.lijinmu.arashi.xposed.*;import com.lijinmu.arashi.xposed.hooks.*;import com.android.settings.notification.app.*;import com.android.settings.widget.*;import io.github.libxposed.api.*;
public class Check {
 static XposedModule module=new XposedModule();static void check(boolean b){if(!b)throw new AssertionError();}
 static Object invoke(Method m,Object o,Object... args)throws Throwable {var h=module.hooks.get(m);check(h!=null);return h.intercept(new XposedInterface.Chain(o,args,a->m.invoke(o,a)));}
 public static void main(String[] args)throws Throwable {
  var loader=Check.class.getClassLoader();SettingsPageRoutes.hookSettings(module,loader);AppNotificationHooks.hookSettings(module,loader);
  check(!HookConfig.defaultEnabled(HookConfig.KEY_AOSP_APP_DETAILS));
  var keys=java.util.Arrays.asList(HookConfig.keys());check(!keys.contains("hook_aosp_app_notifications"));
  check(java.util.Collections.frequency(keys,HookConfig.KEY_AOSP_APP_DETAILS)==1);
  for(String cls:new String[]{"com.android.settings.core.ZybSubSettingLauncher","com.android.settings.zyb.ZybSubSettingLauncher","com.android.settings.core.SubSettingLauncher"}){
   var c=Class.forName(cls);Object launch=c.getConstructor().newInstance();Intent original=(Intent)c.getField("intent").get(launch);
   original.component="vendor";original.flags=123;Object arguments=new Object();original.extras.put(":settings:show_fragment_args",arguments);
   original.extras.put(":settings:show_zyb_index",12);original.extras.put("uid",1012345);
   original.extras.put(":settings:show_fragment","com.android.settings.applications.appinfo.AppInfoDashboardFragment");
   Method method=c.getMethod("toIntent");ConfigManager.enabled=false;check(invoke(method,launch)==original);
   ConfigManager.enabled=true;Intent routed=(Intent)invoke(method,launch);check(routed!=original&&routed.component.endsWith("InstalledAppDetailsTop"));
   check(routed.flags==123&&routed.extras.get(":settings:show_fragment_args")==arguments&&(Integer)routed.extras.get("uid")==1012345);
   check(!routed.extras.containsKey(":settings:show_zyb_index")&&original.extras.containsKey(":settings:show_zyb_index")&&original.component.equals("vendor"));
   original.extras.put(":settings:show_fragment","com.android.settings.notification.app.AppNotificationSettings");
   ConfigManager.enabled=false;check(invoke(method,launch)==original);ConfigManager.enabled=true;
   check(((Intent)invoke(method,launch)).component.endsWith("AppNotificationSettingsActivity"));
   original.extras.put(":settings:show_fragment","unrelated.Fragment");check(invoke(method,launch)==original);
  }
  Method create=AppNotificationSettings.class.getMethod("createPreferenceControllers",Context.class),xml=AppNotificationSettings.class.getMethod("getPreferenceScreenResId");
  ConfigManager.enabled=false;var off=new AppNotificationSettings();invoke(create,off,new Context());check((Integer)invoke(xml,off)==99&&off.mControllers.get(1) instanceof ZybBlockPreferenceController);
  ConfigManager.enabled=true;
  com.android.settings.R.layout.settings_entity_header=0;var unsupported=new AppNotificationSettings();
  invoke(create,unsupported,new Context());check((Integer)invoke(xml,unsupported)==99&&unsupported.mControllers.get(1) instanceof ZybBlockPreferenceController);
  com.android.settings.R.layout.settings_entity_header=20;
  for(boolean modern:new boolean[]{true,false}){
   var page=new AppNotificationSettings();page.modern=modern;List<?> restored=(List<?>)invoke(create,page,new Context());
   check(restored==page.mControllers&&restored.size()==2&&restored.get(1) instanceof BlockPreferenceController);
   check((Integer)invoke(xml,page)==(modern?23:88));
   var header=(HeaderPreferenceController)restored.get(0);Preference oldHeader=new Preference();oldHeader.setKey("pref_notice_header");
   if(!modern)page.screen.addPreference(oldHeader);
   if(modern){var nativeHeader=new com.android.settingslib.widget.LayoutPreference(new Context(),20);nativeHeader.setKey("pref_app_header");page.screen.addPreference(nativeHeader);}
   invoke(HeaderPreferenceController.class.getMethod("displayPreference",PreferenceScreen.class),header,page.screen);
   check(page.screen.findPreference("pref_app_header")!=null&&!page.screen.findPreference("pref_notice_header").visible);
   if(!modern){
    var block=(BlockPreferenceController)restored.get(1);Preference old=new Preference();old.setKey("sw_notify");page.screen.addPreference(old);
    Method update=BlockPreferenceController.class.getMethod("updateState",Preference.class);invoke(update,block,old);
    var nativeSwitch=(SettingsMainSwitchPreference)page.screen.findPreference("arashi_notification_switch");
    check(nativeSwitch!=null&&!old.visible&&nativeSwitch.checked&&nativeSwitch.enabled&&nativeSwitch.shown&&block.writes==0);
    nativeSwitch.listeners.get(0).onSwitchChanged(new android.widget.Switch(),false);check(block.writes==1&&block.mAppRow.banned);
    block.allowed=false;block.mAdmin=new Object();invoke(update,block,old);check(!nativeSwitch.enabled&&!nativeSwitch.checked&&block.writes==1);
    invoke(SettingsMainSwitchPreference.class.getMethod("onBindViewHolder",PreferenceViewHolder.class),nativeSwitch,new PreferenceViewHolder());
    check(!nativeSwitch.mMainSwitchBar.enabled&&nativeSwitch.mMainSwitchBar.admin==block.mAdmin);
    invoke(update,block,old);check(page.screen.items.size()==4&&nativeSwitch.listeners.size()==1);
    // A later incompatible native state restores the original switch instead of leaving a stale control.
    block.mChannel=new Object();invoke(update,block,old);check(old.visible&&page.screen.findPreference("arashi_notification_switch")==null);
    block.mChannel=null;page.screen.rejectActions=false;

   }
  }
  System.out.println("Merged switch, sidebar routes, notification layouts/controllers and legacy callbacks passed");
 }
}'''
with tempfile.TemporaryDirectory() as directory:
    temp=Path(directory)
    for name,content in stubs.items():
        path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content)
    sources=list(temp.rglob('*.java'))+[ROOT/'app/src/main/java/com/lijinmu/arashi/xposed'/p for p in ['Reflect.java','HookConfig.java','hooks/AppDetailsHooks.java','hooks/SettingsPageRoutes.java','hooks/AppNotificationHooks.java']]
    compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
    subprocess.run(compiler+['-d',str(temp/'classes')]+[str(p) for p in sources],check=True)
    subprocess.run(['java','-cp',str(temp/'classes'),'Check'],check=True)
