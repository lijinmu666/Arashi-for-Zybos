"""Run the real notification builder against host Android doubles (no device UI)."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/Manifest.java': '''package android; public class Manifest {public static class permission {public static String POST_NOTIFICATIONS="post";}}''',
'android/R.java': '''package android; public class R {public static class drawable {public static int ic_dialog_info=1;}}''',
'android/os/Build.java': '''package android.os; public class Build {public static class VERSION {public static int SDK_INT=34;}}''',
'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {public static int PERMISSION_GRANTED=0;}''',
'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences {
 java.util.Map<String,Boolean> values=new java.util.HashMap<>(); public boolean getBoolean(String k,boolean d){return values.getOrDefault(k,d);}
 public SharedPreferences edit(){return this;} public SharedPreferences putBoolean(String k,boolean v){values.put(k,v);return this;} public void apply(){} }''',
'android/content/Context.java': '''package android.content; public class Context {
 public static final int MODE_PRIVATE=0; public android.app.NotificationManager manager=new android.app.NotificationManager();
 public SharedPreferences prefs=new SharedPreferences(); public int permission=-1;
 public <T>T getSystemService(Class<T> c){return c.cast(manager);} public int checkSelfPermission(String p){return permission;}
 public SharedPreferences getSharedPreferences(String n,int m){return prefs;} public String getPackageName(){return "com.lijinmu.arashi";} }''',
'android/app/Activity.java': '''package android.app; public class Activity extends android.content.Context {
 public int requests; public void requestPermissions(String[] s,int r){requests++;} public void startActivity(android.content.Intent i){} }''',
'android/content/Intent.java': '''package android.content; public class Intent {
 public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_CLEAR_TOP=2;
 public Class<?> target; public int flags;
 public Intent(Context c,Class<?> t){target=t;} public Intent(String a){}
 public Intent addFlags(int f){flags|=f;return this;} public Intent putExtra(String k,String v){return this;} }''',
'android/app/PendingIntent.java': '''package android.app; public class PendingIntent {
 public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;
 public android.content.Intent intent; public int flags;
 public static PendingIntent getActivity(android.content.Context c,int r,android.content.Intent i,int f){
 PendingIntent p=new PendingIntent();p.intent=i;p.flags=f;return p;} }''',
'android/app/NotificationChannel.java': '''package android.app; public class NotificationChannel {
 public String id; public int importance; public Object sound,attributes;
 public NotificationChannel(String i,String n,int v){id=i;importance=v;}
 public void setSound(Object s,Object a){sound=s;attributes=a;} }''',
'android/app/NotificationManager.java': '''package android.app; import java.util.*; public class NotificationManager {
 public static final int IMPORTANCE_DEFAULT=3,IMPORTANCE_HIGH=4; public boolean enabled=true,fail; public Map<String,Notification> sent=new LinkedHashMap<>();
 public Map<String,NotificationChannel> channels=new LinkedHashMap<>();
 public boolean areNotificationsEnabled(){return enabled;}
 public void createNotificationChannel(NotificationChannel c){channels.putIfAbsent(c.id,c);}
 public void notify(String t,int i,Notification n){if(fail)throw new SecurityException();sent.put(t,n);} }''',
'android/app/Notification.java': '''package android.app; public class Notification {
 public static final Object AUDIO_ATTRIBUTES_DEFAULT=new Object();
 public static final int VISIBILITY_PRIVATE=0,PRIORITY_HIGH=1; public static final String CATEGORY_MESSAGE="msg"; public String category; public int priority; public String channel,text; public PendingIntent click; public boolean autoCancel;
 public static class BigTextStyle {public BigTextStyle bigText(String s){return this;}}
 public static class Builder {Notification n=new Notification(); public Builder(android.content.Context c,String channel){n.channel=channel;}
 public Builder setSmallIcon(int i){return this;} public Builder setContentTitle(String s){return this;}
 public Builder setContentText(String s){n.text=s;return this;}
 public Builder setCategory(String s){n.category=s;return this;} public Builder setPriority(int p){n.priority=p;return this;} public Builder setStyle(BigTextStyle s){return this;}
 public Builder setContentIntent(PendingIntent p){n.click=p;return this;} public Builder setAutoCancel(boolean b){n.autoCancel=b;return this;}
 public Builder setVisibility(int v){return this;} public Notification build(){return n;} } }''',
'android/provider/Settings.java': '''package android.provider; public class Settings {public static class System {public static final Object DEFAULT_NOTIFICATION_URI=new Object();} public static String ACTION_APP_NOTIFICATION_SETTINGS="settings",EXTRA_APP_PACKAGE="package";}''',
'android/util/Log.java': '''package android.util; public class Log {public static int w(String t,String m,Throwable e){return 0;}}''',
'com/lijinmu/arashi/tcp/TcpHistoryActivity.java': '''package com.lijinmu.arashi.tcp; public class TcpHistoryActivity extends android.app.Activity {}''',
 'android/os/Bundle.java': '''package android.os; public class Bundle extends java.util.HashMap<String,Object> {
 public String getString(String k){return (String)get(k);} public boolean getBoolean(String k,boolean d){return (Boolean)getOrDefault(k,d);}
 public int getInt(String k,int d){return (Integer)getOrDefault(k,d);} }''',
 'com/lijinmu/arashi/uninstall/UninstallHistoryActivity.java': '''package com.lijinmu.arashi.uninstall; public class UninstallHistoryActivity extends android.app.Activity {}''',
 'com/lijinmu/arashi/uninstall/UninstallHistoryStore.java': '''package com.lijinmu.arashi.uninstall; public class UninstallHistoryStore {
 public static String REASON="reason",ALLOWED="allowed",CALLER_NAME="name",CALLER_PACKAGE="pkg",CALLER_UID="uid"; }''',
'Check.java': '''import android.app.*; import android.content.*; import com.lijinmu.arashi.tcp.*; import com.lijinmu.arashi.notifications.*;
public class Check {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
 Activity a=new Activity();
 TcpBlockNotifications.post(a,1,"10001");TcpBlockNotifications.post(a,2,"10001");
 check(a.manager.sent.size()==2);
 Notification n=a.manager.sent.get("tcp-block-1");
 check(n.text.equals("ZpTcpService 试图发送 login.fail (10001) 广播，已被阻止。点按查看详情信息"));
 check(n.autoCancel&&n.click.intent.target==TcpHistoryActivity.class);
 check(n.channel.equals("tcp_login_fail_blocked_heads_up_v2"));
 check(n.category.equals(Notification.CATEGORY_MESSAGE)&&n.priority==Notification.PRIORITY_HIGH);
 for(NotificationChannel c:a.manager.channels.values()){
  check(c.importance==NotificationManager.IMPORTANCE_HIGH);
  check(c.sound==android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
  check(c.attributes==Notification.AUDIO_ATTRIBUTES_DEFAULT);
 }
 // A user's later channel choice must survive subsequent posts.
 a.manager.channels.get(n.channel).sound=null;
 TcpBlockNotifications.post(a,2,"10001");
 check(a.manager.channels.get(n.channel).sound==null);
 check((n.click.flags&PendingIntent.FLAG_IMMUTABLE)!=0);
 check((n.click.intent.flags&Intent.FLAG_ACTIVITY_CLEAR_TOP)!=0);
 TcpBlockNotifications.post(a,3,null);check(a.manager.sent.get("tcp-block-3").text.contains("未知错误码"));
 a.manager.enabled=false;TcpBlockNotifications.post(a,4,"403");check(a.manager.sent.size()==3);
 a.manager.enabled=true;a.manager.fail=true;TcpBlockNotifications.post(a,5,"403");check(a.manager.sent.size()==3);
 android.os.Build.VERSION.SDK_INT=32;AppNotifications.requestPermissionIfNeeded(a);check(a.requests==0);
 android.os.Build.VERSION.SDK_INT=34;AppNotifications.requestPermissionIfNeeded(a);check(a.requests==1);
 AppNotifications.requestPermissionIfNeeded(a);check(a.requests==1);
 Activity granted=new Activity();granted.permission=0;AppNotifications.requestPermissionIfNeeded(granted);check(granted.requests==0);

 check(UninstallNoticePolicy.key("黑名单",true)==null);
 check(UninstallNoticePolicy.key("白名单",false)==null);
 check(UninstallNoticePolicy.key("超时默认拒绝",true)==null);
 Activity events=new Activity();
 android.os.Bundle data=new android.os.Bundle();data.put("name","桌面");data.put("uid",1000);
 data.put("reason","黑名单");data.put("allowed",false);
 UninstallNotifications.post(events,1,data);
 Notification denied=events.manager.sent.get("uninstall-1");
 check(denied.text.equals("桌面(1000) 试图卸载应用，由于其已被添加至黑名单，已拒绝本次卸载。点按查看详情信息"));
 check(denied.click.intent.target==com.lijinmu.arashi.uninstall.UninstallHistoryActivity.class);
 NotificationChannel eventChannel=events.manager.channels.get(denied.channel);
 check(eventChannel.importance==NotificationManager.IMPORTANCE_HIGH);
 check(eventChannel.sound==android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
 check(denied.channel.endsWith("_heads_up_v2"));
 check(denied.category.equals(Notification.CATEGORY_MESSAGE)&&denied.priority==Notification.PRIORITY_HIGH);
 events.prefs.putBoolean(NotificationPreferences.BLACKLIST,false);
 UninstallNotifications.post(events,2,data);check(events.manager.sent.size()==1);
 data.put("reason","白名单");data.put("allowed",true);UninstallNotifications.post(events,3,data);
 check(events.manager.sent.get("uninstall-3").text.contains("已放行本次卸载"));
 events.prefs.putBoolean(NotificationPreferences.WHITELIST,false);
 UninstallNotifications.post(events,4,data);check(events.manager.sent.size()==2);
 data.put("reason","超时默认拒绝");data.put("allowed",false);UninstallNotifications.post(events,5,data);
 check(events.manager.sent.get("uninstall-5").text.contains("倒计时已自动拒绝"));
 events.prefs.putBoolean(NotificationPreferences.TIMEOUT,false);
 UninstallNotifications.post(events,6,data);check(events.manager.sent.size()==3);
 // Decisions that do not match the actual outcome must never generate misleading notifications.
 data.put("allowed",true);UninstallNotifications.post(events,7,data);
 data.put("reason","弹窗允许");UninstallNotifications.post(events,8,data);
 data.put("reason","验证未启用");UninstallNotifications.post(events,9,data);
 data.put("reason","设备未使用");UninstallNotifications.post(events,10,data);
 check(events.manager.sent.size()==3);
 TcpBlockNotifications.post(events,11,"10001");check(events.manager.sent.size()==4);
 events.prefs.putBoolean(NotificationPreferences.TCP,false);
 TcpBlockNotifications.post(events,12,"10001");check(events.manager.sent.size()==4);
 check(UninstallNoticePolicy.key("设备息屏",true)==null);
 data.put("reason","设备息屏");data.put("allowed",false);
 UninstallNotifications.post(events,13,data);
 check(events.manager.sent.get("uninstall-13").text.equals("桌面(1000) 试图卸载应用，由于设备未使用，已拒绝本次卸载。点按查看详情信息"));
 events.prefs.putBoolean(NotificationPreferences.SCREEN_OFF,false);
 UninstallNotifications.post(events,14,data);check(events.manager.sent.size()==5);
 check(!NotificationPreferences.uninstallEnabled(events));
 System.out.println("TCP and uninstall notification checks passed");
 }
}''',
}
with tempfile.TemporaryDirectory(prefix='arashi-tcp-notify-') as directory:
    tmp=Path(directory)
    for name,source in STUBS.items():
        target=tmp/name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(source)
    real=ROOT/'app/src/main/java/com/lijinmu/arashi/tcp/TcpBlockNotifications.java'
    subprocess.run(['javac','-d',str(tmp/'classes'),*map(str,tmp.rglob('*.java')),str(real),*map(str,(ROOT/'app/src/main/java/com/lijinmu/arashi/notifications').glob('*.java'))],check=True)
    subprocess.run(['java','-cp',str(tmp/'classes'),'Check'],check=True)
