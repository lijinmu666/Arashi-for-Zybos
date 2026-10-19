"""Run the actual uninstall prompt lifecycle on Android 12/14 API doubles."""
from pathlib import Path
import ast
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
# Reuse only literal platform doubles, without executing the notification test suite.
module = ast.parse((ROOT / 'tests/check_tcp_notifications.py').read_text())
base = next(n.value for n in module.body if isinstance(n, ast.Assign)
            and any(isinstance(t, ast.Name) and t.id == 'STUBS' for t in n.targets))
stubs = ast.literal_eval(base)
stubs.pop('Check.java')
stubs.update({
'android/os/PowerManager.java': '''package android.os; public class PowerManager {public boolean interactive=true;public boolean isInteractive(){return interactive;}}''',
'android/os/ResultReceiver.java': '''package android.os; public class ResultReceiver {public java.util.List<Integer> results=new java.util.ArrayList<>();public void send(int code,Bundle b){results.add(code);}}''',
'android/content/BroadcastReceiver.java': '''package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}''',
'android/content/IntentFilter.java': '''package android.content; public class IntentFilter {public IntentFilter(String a){}}''',
'android/content/Context.java': '''package android.content; public class Context {
 public static final int MODE_PRIVATE=0,RECEIVER_NOT_EXPORTED=4;
 public SharedPreferences prefs=new SharedPreferences();public android.os.PowerManager power=new android.os.PowerManager();
 public BroadcastReceiver registered; public int removed;
 public <T>T getSystemService(Class<T> c){return c.cast(power);}
 public SharedPreferences getSharedPreferences(String n,int m){return prefs;}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){registered=r;return null;}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){return registerReceiver(r,f);}
 public void unregisterReceiver(BroadcastReceiver r){registered=null;removed++;}
}''',
'android/content/Intent.java': '''package android.content; public class Intent {
 public static final String ACTION_SCREEN_OFF="screen_off";String action; public android.os.ResultReceiver receiver;
 public Intent(){} public Intent(String a){action=a;}public String getAction(){return action;}
 public <T>T getParcelableExtra(String k,Class<T> c){return c.cast(receiver);}
 @SuppressWarnings("unchecked") public <T>T getParcelableExtra(String k){return (T)receiver;}
 public String getStringExtra(String k){return null;}public String[] getStringArrayExtra(String k){return null;}
 public int getIntExtra(String k,int d){return d;}public long getLongExtra(String k,long d){return d;}
}''',
'android/app/Activity.java': '''package android.app; public class Activity extends android.content.Context {}''',
'androidx/activity/OnBackPressedCallback.java': '''package androidx.activity; public abstract class OnBackPressedCallback {public OnBackPressedCallback(boolean b){}public abstract void handleOnBackPressed();}''',
'androidx/activity/ComponentActivity.java': '''package androidx.activity; public class ComponentActivity extends android.app.Activity {
 public android.content.Intent intent=new android.content.Intent();public boolean finished;public int wakeups;
 protected void onCreate(android.os.Bundle b){}protected void onDestroy(){} public void finish(){finished=true;}
 public android.content.Intent getIntent(){return intent;} public void setShowWhenLocked(boolean b){}
 public void setTurnScreenOn(boolean b){if(b)wakeups++;}
 public Dispatcher getOnBackPressedDispatcher(){return new Dispatcher();}
 public static class Dispatcher {public void addCallback(Object a,OnBackPressedCallback c){}}
}''',
'android/os/SystemClock.java': '''package android.os; public class SystemClock {public static long elapsedRealtime(){return 100;}}''',
'com/arashi/ui/MiuixPages.java': '''package com.arashi.ui; public class MiuixPages {
 public static int renders;public static void renderUninstallPrompt(com.arashi.UninstallPromptActivity a,String t,int uid,String[] pkgs,long deadline){renders++;}}''',
'com/arashi/uninstall/UninstallConfig.java': '''package com.arashi.uninstall; public class UninstallConfig {public static String PREF_NAME="uninstall",ENABLED="enabled";}''',
'Check.java': '''package com.arashi; import android.os.*;import android.content.*;import com.arashi.ui.MiuixPages;
public class Check {
 static void start(UninstallPromptActivity a){a.onCreate(null);} static void destroy(UninstallPromptActivity a){a.onDestroy();}
 static void check(boolean b){if(!b)throw new AssertionError();}
 static UninstallPromptActivity prompt(boolean on,boolean enabled){UninstallPromptActivity a=new UninstallPromptActivity();a.power.interactive=on;
 a.prefs.putBoolean("enabled",enabled);a.intent.receiver=new ResultReceiver();return a;}
 static void result(UninstallPromptActivity a,int r){check(a.intent.receiver.results.size()==1&&a.intent.receiver.results.get(0)==r);}
 public static void main(String[] args){for(int sdk:new int[]{31,34}){
 Build.VERSION.SDK_INT=sdk;MiuixPages.renders=0;
 UninstallPromptActivity off=prompt(false,true);start(off);result(off,4);check(off.finished&&off.wakeups==0&&MiuixPages.renders==0);
 destroy(off);result(off,4);check(off.registered==null);
 UninstallPromptActivity shown=prompt(true,true);start(shown);check(MiuixPages.renders==1&&shown.intent.receiver.results.isEmpty());
 shown.power.interactive=false;shown.registered.onReceive(shown,new Intent(Intent.ACTION_SCREEN_OFF));
 result(shown,4);check(shown.finished);shown.decide(true);destroy(shown);result(shown,4);check(shown.removed==1);
 UninstallPromptActivity race=prompt(true,true);start(race);race.power.interactive=false;race.decide(true);result(race,4);destroy(race);
 UninstallPromptActivity disabled=prompt(false,false);start(disabled);result(disabled,1);check(disabled.registered==null);destroy(disabled);
 UninstallPromptActivity allow=prompt(true,true);start(allow);allow.decide(true);destroy(allow);result(allow,1);
 UninstallPromptActivity timeout=prompt(true,true);start(timeout);timeout.timeout();destroy(timeout);result(timeout,2);
 UninstallPromptActivity offTimeout=prompt(true,true);start(offTimeout);offTimeout.power.interactive=false;offTimeout.timeout();destroy(offTimeout);result(offTimeout,4);
 }
 System.out.println("Android 12/14 screen-off uninstall prompt checks passed");}
}''',
})
stubs['android/os/Bundle.java'] = stubs['android/os/Bundle.java'].replace('{\n', '{\n public static final Bundle EMPTY=new Bundle();\n',1)
# Drop unrelated doubles whose API methods are deliberately absent from this lifecycle environment.
for name in ['android/app/PendingIntent.java', 'android/app/Notification.java', 'android/app/NotificationManager.java']:
    stubs.pop(name)
with tempfile.TemporaryDirectory(prefix='arashi-uninstall-screen-') as directory:
    tmp=Path(directory)
    for name,source in stubs.items():
        target=tmp/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source)
    sources=[ROOT/'app/src/main/java/com/arashi/UninstallPromptActivity.java',
             ROOT/'app/src/main/java/com/arashi/uninstall/UninstallScreenState.java']
    subprocess.run(['javac','-d',str(tmp/'classes'),*map(str,tmp.rglob('*.java')),*map(str,sources)],check=True)
    subprocess.run(['java','-cp',str(tmp/'classes'),'com.arashi.Check'],check=True)
