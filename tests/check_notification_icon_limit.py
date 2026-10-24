"""Execute actual count-cap hooks on 12/14-shaped containers.

The fixtures model the verified cap branches and retain their own width/filtering
behavior, so these checks validate overrides/restoration rather than device pixels.
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
'android/content/res/Resources.java': '''package android.content.res;public class Resources {
 public static class NotFoundException extends RuntimeException {}
 public String getResourceName(int id){return switch(id){case 1->"com.android.systemui:id/notificationIcons";
 case 2->"com.android.systemui:id/left_aligned_notification_icon_container";
 case 3->"com.android.systemui:id/notification_shelf";case 4->"other.package:id/notificationIcons";
 default->throw new NotFoundException();};}}
''',
'android/view/ViewGroup.java': '''package android.view;public class ViewGroup {
 public int id=1,children=8,slots=20;public int getId(){return id;}public int getChildCount(){return children;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}}
''',
'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed;public class ConfigManager {
 public static boolean enabled;public static boolean isEnabledCached(String key){
  if(!key.equals(HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS))throw new AssertionError();return enabled;}}
''',
'Check.java': '''package com.lijinmu.arashi.xposed.hooks;
import java.lang.reflect.*;import com.lijinmu.arashi.xposed.*;import io.github.libxposed.api.*;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
public class Check {
 static XposedModule module=new XposedModule();static void check(boolean v){if(!v)throw new AssertionError();}
 static Object call(Method m,Object owner,Object...a)throws Throwable{
  m.setAccessible(true);return module.hooks.get(m).intercept(new XposedInterface.Chain(owner,a,p->m.invoke(owner,p)));}
 public static void main(String[] args)throws Throwable {
  int version=Integer.parseInt(args[0]);
  check(!HookConfig.defaultEnabled(HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS));
  check(java.util.Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS));
  NotificationIconLimitHooks.hookSystemUI(module,Check.class.getClassLoader());
  check(module.hooks.size()==(version==12?1:2));
  var view=new NotificationIconContainer();Method calculate=view.getClass().getDeclaredMethod(
   version==12?"calculateIconTranslations":"calculateIconXTranslations");
  ConfigManager.enabled=false;call(calculate,view);check(view.visible==4&&view.mIsStaticLayout);
  ConfigManager.enabled=true;
  for(int count:new int[]{0,1,4,5,8,20}){
   view.children=count;call(calculate,view);check(view.visible==count&&view.mIsStaticLayout);
  }
  view.children=8;view.slots=3;call(calculate,view);check(view.visible==3); // original width constraint
  view.slots=20;view.mSpeedBumpIndex=2;call(calculate,view);check(view.visible==2); // original filtering
  view.mSpeedBumpIndex=-1;
  for(int id:new int[]{1,2}){view.id=id;call(calculate,view);check(view.visible==8);}
  for(int id:new int[]{0,3,4,99}){view.id=id;call(calculate,view);check(view.visible==4);}
  view.id=1;view.mOnLockScreen=true;call(calculate,view);check(view.visible==(version==12?5:3));
  view.mOnLockScreen=false;view.mDozing=true;call(calculate,view);check(view.visible==4);
  view.mDozing=false;view.mInNotificationIconShelf=true;call(calculate,view);check(view.visible==4);
  view.mInNotificationIconShelf=false;view.mIsStaticLayout=false;call(calculate,view);check(view.visible==8&&!view.mIsStaticLayout);
  view.mIsStaticLayout=true;view.fail=true;
  try{call(calculate,view);throw new AssertionError();}catch(InvocationTargetException expected){}
  check(view.mIsStaticLayout);view.fail=false;
  if(version==14){
   Field max=view.getClass().getDeclaredField("mMaxStaticIcons");check(max.getInt(view)==4);
   Method measure=view.getClass().getDeclaredMethod("onMeasure",int.class,int.class);
   call(measure,view,100,20);check(view.measuredChildren==8&&view.seenCap==8&&max.getInt(view)==4);
   view.fail=true;try{call(measure,view,100,20);throw new AssertionError();}catch(InvocationTargetException expected){}
   check(max.getInt(view)==4);view.fail=false;
   max.setInt(view,6);view.children=2;call(measure,view,100,20);check(view.seenCap==6&&max.getInt(view)==6);
   max.setInt(view,4);view.children=8;
  }
  ConfigManager.enabled=false;call(calculate,view);check(view.visible==4&&view.mIsStaticLayout);
  System.out.println("Notification cap Android "+version+": count removal, measurement, width/filtering, status-bar-only guards, disabled mode and exception restoration passed");
 }
}
''',
})
for version in [12, 14]:
    # Branch shapes/field names were read from the supplied SystemUI APKs.
    cap = 'mMaxStaticIcons' if version == 14 else '4'
    lock = '3' if version == 14 else '5'
    name = 'calculateIconXTranslations' if version == 14 else 'calculateIconTranslations'
    container = '''package com.android.systemui.statusbar.phone;
public class NotificationIconContainer extends android.view.ViewGroup {
 public boolean mIsStaticLayout=true,mOnLockScreen,mDozing,mInNotificationIconShelf,fail;
 public int visible,seenCap,measuredChildren,mSpeedBumpIndex=-1;
 @FIELD@
 public void @NAME@(){int cap=mOnLockScreen?@LOCK@:mIsStaticLayout?@CAP@:getChildCount();
  seenCap=cap;if(fail)throw new IllegalStateException();int count=Math.min(getChildCount(),cap);
  if(mSpeedBumpIndex>=0)count=Math.min(count,mSpeedBumpIndex);visible=Math.min(count,slots);}
 @MEASURE@
 public void onLayout(boolean changed,int l,int t,int r,int b){}public void updateState(){}
}
'''.replace('@FIELD@', 'public int mMaxStaticIcons=4;' if version == 14 else '').replace(
        '@NAME@', name).replace('@LOCK@', lock).replace('@CAP@', cap).replace(
        '@MEASURE@', '''public void onMeasure(int width,int height){
 int cap=mOnLockScreen?3:mIsStaticLayout?mMaxStaticIcons:getChildCount();seenCap=cap;
 if(fail)throw new IllegalStateException();measuredChildren=Math.min(getChildCount(),cap);}''' if version == 14 else '')
    with tempfile.TemporaryDirectory(prefix=f'arashi-notification-icons-{version}-') as directory:
        temp = Path(directory)
        for filename, source in {**stubs, 'com/android/systemui/statusbar/phone/NotificationIconContainer.java': container}.items():
            path = temp / filename
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
        files = list(temp.rglob('*.java')) + [ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed' / name
                                            for name in ['HookConfig.java', 'Reflect.java', 'hooks/NotificationIconLimitHooks.java']]
        compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
        subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(p) for p in files], check=True)
        subprocess.run(['java', '-cp', str(temp / 'classes'), 'com.lijinmu.arashi.xposed.hooks.Check', str(version)], check=True)
