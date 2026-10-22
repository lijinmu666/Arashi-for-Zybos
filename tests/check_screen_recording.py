"""Exercise real audio-source hooks, including an inlined Android 12 intent helper."""
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
names = ['android/util/Log.java', 'io/github/libxposed/api/XposedInterface.java',
         'io/github/libxposed/api/XposedModule.java']
stubs = {name: base[name] for name in names}
stubs['android/util/Log.java'] = stubs['android/util/Log.java'].replace(
    'public static int i(', 'public static int e(String t,String m,Throwable e){return 0;} public static int i(')
stubs.update({
'android/os/Bundle.java': 'package android.os; public class Bundle {}',
'android/content/res/Resources.java': 'package android.content.res; public class Resources {public int getIdentifier(String a,String b,String c){return 1;}}',
'android/content/Context.java': 'package android.content; public class Context {public android.content.res.Resources getResources(){return new android.content.res.Resources();}public String getPackageName(){return "com.android.systemui";}}',
'android/content/ComponentName.java': 'package android.content; public class ComponentName {String name;public ComponentName(String n){name=n;}public String getClassName(){return name;}}',
'android/content/Intent.java': '''package android.content;import java.util.*;
public class Intent {String action;ComponentName component;Map<String,Integer> extras=new HashMap<>();
 public Intent setAction(String s){action=s;return this;}public String getAction(){return action;}
 public Intent setComponent(ComponentName c){component=c;return this;}public ComponentName getComponent(){return component;}
 public Intent putExtra(String k,int v){extras.put(k,v);return this;}public int getIntExtra(String k,int d){return extras.getOrDefault(k,d);}
}''',
'android/view/View.java': 'package android.view;public class View {public static final int VISIBLE=0;public void setVisibility(int v){}}',
'android/app/Activity.java': 'package android.app;public class Activity extends android.content.Context {public android.view.View findViewById(int id){return new android.view.View();}}',
'android/app/Dialog.java': 'package android.app;public class Dialog {public android.content.Context getContext(){return new android.content.Context();}public android.view.View findViewById(int id){return new android.view.View();}}',
'android/app/PendingIntent.java': 'package android.app;public class PendingIntent {public static PendingIntent getForegroundService(android.content.Context c,int code,android.content.Intent i,int flags){return new PendingIntent();}}',
'android/widget/Switch.java': 'package android.widget;public class Switch {public boolean checked=true;public boolean isChecked(){return checked;}}',
'android/widget/Spinner.java': '''package android.widget;public class Spinner {
 public Object[] items=com.android.systemui.screenrecord.ScreenRecordingAudioSource.values();public int selected=1;
 public Object getSelectedItem(){return items[selected];}public int getCount(){return items.length;}
 public Object getItemAtPosition(int i){return items[i];}public void setSelection(int i){selected=i;}}''',
'com/lijinmu/arashi/xposed/ConfigManager.java': 'package com.lijinmu.arashi.xposed;public class ConfigManager {public static boolean enabled=true;public static boolean isEnabled(String s){return enabled;}}',
'com/lijinmu/arashi/xposed/Reflect.java': '''package com.lijinmu.arashi.xposed;import java.lang.reflect.*;
public class Reflect {
 public static Method findMethodExact(Class<?> t,String n,Class<?>... args){try{return t.getDeclaredMethod(n,args);}catch(Exception e){return null;}}
 public static Object getObjectField(Object o,String n){try{return o.getClass().getField(n).get(o);}catch(Exception e){return null;}}
}''',
'com/android/systemui/screenrecord/ScreenRecordingAudioSource.java': 'package com.android.systemui.screenrecord;public enum ScreenRecordingAudioSource {NONE,INTERNAL,MIC,MIC_AND_INTERNAL}',
'com/android/systemui/screenrecord/RecordingService.java': '''package com.android.systemui.screenrecord;public class RecordingService {
 public static android.content.Intent getStartIntent(android.content.Context c,int result,int source,boolean taps){return null;}
 public int onStartCommand(android.content.Intent i,int flags,int id){return 1;}
}''',
'com/android/systemui/screenrecord/ScreenMediaRecorder.java': '''package com.android.systemui.screenrecord;public class ScreenMediaRecorder {
 public Object mAudioSource=ScreenRecordingAudioSource.INTERNAL;
 public void prepare(){} public void start(){} public void end(){} public Object save(){return null;}
}''',
'Check.java': '''import java.lang.reflect.*;
import android.content.*;import android.app.*;import com.android.systemui.screenrecord.*;
import com.lijinmu.arashi.xposed.*;import com.lijinmu.arashi.xposed.hooks.*;import io.github.libxposed.api.*;
public class Check {
 static XposedModule module=new XposedModule();
 static void check(boolean b){if(!b)throw new AssertionError();}
 static Object invoke(Method m,Object self,Object[] args,XposedInterface.Next next)throws Throwable {
  check(module.hooks.containsKey(m));return module.hooks.get(m).intercept(new XposedInterface.Chain(self,args,next));
 }
 static Intent intent(){return new Intent().setAction("com.android.systemui.screenrecord.START")
  .setComponent(new ComponentName("com.android.systemui.screenrecord.RecordingService")).putExtra("extra_useAudio",3).putExtra("extra_resultCode",-1);}
 static Method pending,request;
 static void deliver(Intent i)throws Throwable {
  invoke(pending,null,new Object[]{new Context(),2,i,201326592},args->{
   check((Integer)args[1]==2&&(Integer)args[3]==201326592);
   check(((Intent)args[2]).getIntExtra("extra_resultCode",99)==-1);return null;});
 }
 public static void main(String[] args)throws Throwable {
  ScreenRecordingHooks.hookSystemUI(module,Check.class.getClassLoader());
  pending=PendingIntent.class.getMethod("getForegroundService",Context.class,int.class,Intent.class,int.class);
  request=ScreenRecordDialog.class.getMethod("requestScreenCapture");
  var dialog=new ScreenRecordDialog();
  for(int mode=1;mode<=3;mode++){
   dialog.mOptions.selected=mode;dialog.mAudioSwitch.checked=true;
   Intent i=intent();
   // Deliberately bypass getStartIntent: model an optimized/inlined Android 12 caller.
   invoke(request,dialog,new Object[]{},a->{deliver(i);return null;});
   check(i.getIntExtra("extra_useAudio",-1)==mode);
  }
  dialog.mAudioSwitch.checked=false;Intent silent=intent();
  invoke(request,dialog,new Object[]{},a->{deliver(silent);return null;});
  check(silent.getIntExtra("extra_useAudio",-1)==0);
  dialog.mAudioSwitch.checked=true;dialog.mOptions.selected=1;
  Intent unrelated=intent().setComponent(new ComponentName("unrelated.Service"));
  invoke(request,dialog,new Object[]{},a->{deliver(unrelated);return null;});
  check(unrelated.getIntExtra("extra_useAudio",-1)==3);
  ConfigManager.enabled=false;Intent off=intent();
  invoke(request,dialog,new Object[]{},a->{deliver(off);return null;});
  check(off.getIntExtra("extra_useAudio",-1)==3);ConfigManager.enabled=true;
  try{invoke(request,dialog,new Object[]{},a->{throw new IllegalStateException("request");});throw new AssertionError();}
  catch(IllegalStateException expected){}
  Intent later=intent();deliver(later);check(later.getIntExtra("extra_useAudio",-1)==3);
  var recorder=new ScreenMediaRecorder();Method save=ScreenMediaRecorder.class.getMethod("save");
  Object result=new Object();check(invoke(save,recorder,new Object[]{},a->result)==result);
  var failure=new IllegalStateException("backend");
  try{invoke(save,recorder,new Object[]{},a->{throw failure;});throw new AssertionError();}
  catch(IllegalStateException expected){check(expected==failure);}
  System.out.println("Screen recording source boundary and failure propagation passed: "+args[0]);
 }
}''',
})
compiler = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
for parent in ['android.app.Activity', 'android.app.Dialog']:
    with tempfile.TemporaryDirectory(prefix='arashi-record-') as directory:
        tmp = Path(directory)
        current = dict(stubs)
        current['com/android/systemui/screenrecord/ScreenRecordDialog.java'] = (
            'package com.android.systemui.screenrecord;public class ScreenRecordDialog extends ' + parent + ''' {
 public android.widget.Switch mAudioSwitch=new android.widget.Switch();
 public android.widget.Spinner mOptions=new android.widget.Spinner();
 public void onCreate(android.os.Bundle b){} public void requestScreenCapture(){}
}''')
        for name, text in current.items():
            p = tmp / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text)
        subprocess.run([*compiler, '-d', str(tmp/'classes'), *map(str, tmp.rglob('*.java')),
                        str(ROOT/'app/src/main/java/com/lijinmu/arashi/xposed/HookConfig.java'),
                        str(ROOT/'app/src/main/java/com/lijinmu/arashi/xposed/hooks/ScreenRecordingHooks.java')], check=True)
        subprocess.run(['java', '-cp', str(tmp/'classes'), 'Check', parent], check=True)
