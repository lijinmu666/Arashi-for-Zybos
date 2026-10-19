"""Exercise real TCP hook callbacks with small host-side Android/libxposed doubles."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'android/os/Bundle.java': '''package android.os;
import java.util.*;
public class Bundle extends HashMap<String,Object> {
 public void putString(String k,String v){put(k,v);} public void putInt(String k,int v){put(k,v);}
 public void putLong(String k,long v){put(k,v);} public String getString(String k){return (String)get(k);}
}''',
    'android/os/Binder.java': '''package android.os; public class Binder { public static long clearCallingIdentity(){return 0;} public static void restoreCallingIdentity(long l){} }''',
    'android/os/Process.java': '''package android.os; public class Process { public static int myPid(){return 42;} }''',
    'android/util/Log.java': '''package android.util; public class Log {
 public static int i(String t,String m){return 0;} public static int w(String t,String m){return 0;}
 public static int w(String t,String m,Throwable e){return 0;} }''',
    'android/net/Uri.java': '''package android.net; public class Uri {}''',
    'android/content/Intent.java': '''package android.content; import java.util.*; public class Intent {
 String action; Map<String,Object> extras=new HashMap<>(); public Intent(String a){action=a;}
 public String getAction(){return action;} public String getPackage(){return "launcher";}
 public Intent putExtra(String k,Object v){extras.put(k,v);return this;}
 public int getIntExtra(String k,int d){return (Integer)extras.getOrDefault(k,d);}
 public String getStringExtra(String k){return (String)extras.get(k);} }''',
    'android/content/ContentResolver.java': '''package android.content; import android.os.Bundle; import android.net.Uri; import java.util.*;
 public class ContentResolver {
 public static List<Bundle> records=Collections.synchronizedList(new ArrayList<>()); public static boolean fail;
 public Bundle call(Uri u,String m,String a,Bundle b){if(fail)throw new IllegalStateException("offline");records.add(b);return new Bundle();} }''',
    'android/content/Context.java': '''package android.content; public class Context {
 public ContentResolver getContentResolver(){return new ContentResolver();} }''',
    'android/content/ContextWrapper.java': '''package android.content; public class ContextWrapper extends Context {
 public void sendBroadcast(Intent i){} public void sendBroadcastAsUser(Intent i,Object u){} }''',
    'android/app/ContextImpl.java': '''package android.app; public class ContextImpl extends android.content.Context {
 public void sendBroadcast(android.content.Intent i){} }''',
    'android/app/Application.java': '''package android.app; public class Application extends android.content.ContextWrapper {
 public static String getProcessName(){return "com.zuoyebang.iot.zptcpservice";} }''',
    'android/app/ActivityThread.java': '''package android.app; public class ActivityThread {
 public static Application currentApplication(){return new Application();} }''',
    'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed; public class ConfigManager {
 public static boolean enabled=true; public static boolean isEnabled(String k){return enabled;} }''',
    'com/lijinmu/arashi/xposed/SettingsProvider.java': '''package com.lijinmu.arashi.xposed; public class SettingsProvider {
 public static final android.net.Uri URI=new android.net.Uri(); public static final String METHOD_ADD_TCP_RECORD="record"; }''',
    'com/lijinmu/arashi/tcp/TcpHistoryStore.java': '''package com.lijinmu.arashi.tcp; public class TcpHistoryStore {
 public static final String TIME="time",ERROR_TYPE="error_type",ERROR_CODE="error_code",SOURCE="source",DETAILS="details",STACK="stack",PROCESS="process",PID="pid"; }''',
    'io/github/libxposed/api/XposedModule.java': '''package io.github.libxposed.api; import java.lang.reflect.*; import java.util.*;
 public class XposedModule {
 public interface Interceptor {Object call(Chain c)throws Throwable;} public interface Next {Object run()throws Throwable;}
 public static class Chain {Object self;Object[] args;Next next;
 public Chain(Object s,Object[] a,Next n){self=s;args=a;next=n;}
 public Object getThisObject(){return self;} public Object getArg(int i){return args[i];} public Object proceed()throws Throwable{return next.run();}}
 public Map<Method,Interceptor> hooks=new HashMap<>(); public class Builder {Method m;Builder(Method m){this.m=m;}
 public void intercept(Interceptor i){hooks.put(m,i);}} public Builder hook(Method m){return new Builder(m);}
 public boolean deoptimize(Executable m){return true;} }''',
    'q7/a.java': '''package q7; public class a {public static String[] a={"com.zuoyebang.iot.pad.supervise","launcher"}; public static void a(int t,String c){} }''',
    'com/zuoyebang/iot/mod/tcp/TcpMessage.java': '''package com.zuoyebang.iot.mod.tcp; public class TcpMessage {}''',
    'com/zuoyebang/iot/zptcpservice/packet/receive/ReceiveLoginMessage.java': '''package com.zuoyebang.iot.zptcpservice.packet.receive;
 public class ReceiveLoginMessage extends com.zuoyebang.iot.mod.tcp.TcpMessage {
 private int status=1,halted=1,rop=1; private String code="200",msg="device disabled",token="TOKEN_SENTINEL",secret="SECRET_SENTINEL"; }''',
    'com/zuoyebang/iot/zptcpservice/interceptors/b.java': '''package com.zuoyebang.iot.zptcpservice.interceptors;
 public class b {public void a(com.zuoyebang.iot.mod.tcp.TcpMessage p){} }''',
    'com/zuoyebang/iot/zptcpservice/login/a.java': '''package com.zuoyebang.iot.zptcpservice.login; public class a {public Object a(){return null;} }''',
    'com/zuoyebang/iot/zptcpservice/login/LoginData.java': '''package com.zuoyebang.iot.zptcpservice.login;
 public class LoginData {private int halted=0,rop=0; private String token="HTTP_TOKEN_SENTINEL";}''',
    'com/zuoyebang/iot/zptcpservice/login/LoginResponse.java': '''package com.zuoyebang.iot.zptcpservice.login;
 public class LoginResponse {public LoginData getData(){return null;} }''',
    'Check.java': '''import android.content.*; import android.os.Bundle; import com.lijinmu.arashi.xposed.*;
import com.lijinmu.arashi.xposed.hooks.TcpLoginHooks; import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*; import java.util.concurrent.atomic.AtomicInteger;
public class Check {
 static XposedModule module=new XposedModule(); static AtomicInteger proceeded=new AtomicInteger();
 static Method sender; static Object call(Method m,Object self,Object[] args,XposedModule.Next next)throws Throwable {
   if(!module.hooks.containsKey(m))throw new AssertionError("missing hook: "+m);
   return module.hooks.get(m).call(new XposedModule.Chain(self,args,next)); }
 static void check(boolean value){if(!value)throw new AssertionError();}
 static void send(String code)throws Throwable {call(sender,null,new Object[]{1,code},()->{proceeded.incrementAndGet();return null;});}
 static Bundle last(){return ContentResolver.records.get(ContentResolver.records.size()-1);}
 public static void main(String[] args)throws Throwable {
 TcpLoginHooks.hookTcpService(module,Check.class.getClassLoader());
 sender=Class.forName("q7.a").getDeclaredMethod("a",int.class,String.class);
 Method broadcast=ContextWrapper.class.getDeclaredMethod("sendBroadcast",Intent.class);
 ContextWrapper context=new ContextWrapper();
 for(String code:new String[]{"403","405","10000","10001","unknown"})send(code);
 check(ContentResolver.records.size()==5 && proceeded.get()==0);
 check(last().getString("error_code").equals("unknown"));
 call(broadcast,context,new Object[]{new Intent("ordinary.action")},()->{proceeded.incrementAndGet();return null;});
 check(proceeded.get()==1 && ContentResolver.records.size()==5);
 // Fallback catches login.fail independently of obfuscated vendor helpers.
 call(broadcast,context,new Object[]{new Intent(TcpLoginPolicy.ACTION).putExtra("errorType",2).putExtra("errorCode","10000")},()->{throw new AssertionError("leaked");});
 check(ContentResolver.records.size()==6 && last().get("error_type").equals(2));
 ConfigManager.enabled=false;send("10001");
 call(broadcast,context,new Object[]{new Intent(TcpLoginPolicy.ACTION)},()->{proceeded.incrementAndGet();return null;});
 check(ContentResolver.records.size()==6 && proceeded.get()==3);ConfigManager.enabled=true;
 Class<?> packet=Class.forName("com.zuoyebang.iot.zptcpservice.packet.receive.ReceiveLoginMessage");
 Class<?> interceptor=Class.forName("com.zuoyebang.iot.zptcpservice.interceptors.b");
 Method receive=interceptor.getDeclaredMethod("a",Class.forName("com.zuoyebang.iot.mod.tcp.TcpMessage"));
 call(receive,interceptor.getConstructor().newInstance(),new Object[]{packet.getConstructor().newInstance()},()->{send("10001");return null;});
 check(last().getString("source").equals("TCP 登录响应"));
 String details=last().getString("details");check(details.contains("halted = 1") && details.contains("code = 200"));
 check(!details.contains("TOKEN_SENTINEL") && !details.contains("SECRET_SENTINEL"));
 Class<?> http=Class.forName("com.zuoyebang.iot.zptcpservice.login.a");
 Class<?> response=Class.forName("com.zuoyebang.iot.zptcpservice.login.LoginResponse");
 Object data=Class.forName("com.zuoyebang.iot.zptcpservice.login.LoginData").getConstructor().newInstance();
 call(http.getDeclaredMethod("a"),http.getConstructor().newInstance(),new Object[0],()->{
   call(response.getDeclaredMethod("getData"),response.getConstructor().newInstance(),new Object[0],()->data);
   send("10000");return null;
 });
 check(last().getString("source").equals("HTTP 登录响应"));
 check(last().getString("details").contains("rop = 0") && !last().getString("details").contains("HTTP_TOKEN_SENTINEL"));
 send("405");check(last().getString("source").equals("未识别触发路径"));
 // Concurrent callbacks must not inherit another thread's response context.
 AtomicInteger failed=new AtomicInteger();
 Thread other=new Thread(()->{try{send("403");if(!last().getString("source").equals("未识别触发路径"))failed.incrementAndGet();}catch(Throwable e){failed.incrementAndGet();}});
 call(receive,interceptor.getConstructor().newInstance(),new Object[]{packet.getConstructor().newInstance()},()->{other.start();other.join();return null;});
 check(failed.get()==0);
 ContentResolver.fail=true;send("10001");check(proceeded.get()==3);
 check(!HookConfig.defaultEnabled(HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL));
 System.out.println("TCP hooks: all codes blocked, unrelated broadcasts passed, disabled pass-through, response provenance, secret exclusion, thread isolation, persistence failure passed");
 }}''',
}
with tempfile.TemporaryDirectory(prefix='arashi-tcp-check-') as temp:
    folder = Path(temp)
    sources = []
    for name, content in STUBS.items():
        path = folder / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        sources.append(str(path))
    sources += [str(ROOT / 'app/src/main/java' / name) for name in (
        'com/lijinmu/arashi/xposed/HookConfig.java', 'com/lijinmu/arashi/xposed/Reflect.java',
        'com/lijinmu/arashi/xposed/TcpLoginPolicy.java', 'com/lijinmu/arashi/xposed/hooks/TcpLoginHooks.java')]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(folder / 'classes'), *sources], check=True)
    subprocess.run(['java', '-cp', str(folder / 'classes'), 'Check'], check=True)
