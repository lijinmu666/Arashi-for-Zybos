"""Compile the actual hooks and exercise enabled/disabled interception without Android."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
# Application ID is case-sensitive and differs from the original Java namespace.
for path in (
    'app/src/main/java/com/lijinmu/arashi/xposed/HookEntry.java',
    'app/src/main/java/com/lijinmu/arashi/xposed/HookScopes.java',
    'app/src/main/java/com/lijinmu/arashi/xposed/SettingsProvider.java',
):
    source = (root / path).read_text()
    assert '"JC.CODE.V3"' in source, path
    assert '"com.jc.code.v3"' not in source, path
scope = (root / 'app/src/main/resources/META-INF/xposed/scope.list').read_text().splitlines()
assert scope.count('JC.CODE.V3') == 1
assert 'com.jc.code.v3' not in scope
hook = (root / 'app/src/main/java/com/lijinmu/arashi/xposed/hooks/JcCodeHooks.java').read_text()
assert '"com.jc.code.v3.MainActivity"' in hook
assert 'wfQA' not in hook and 'gunmugunmugunmu' not in hook
fixtures = {
    'android/util/Log.java': '''package android.util;
public class Log { public static int i(String t,String m){return 0;}
public static int e(String t,String m,Throwable e){throw new AssertionError(m,e);} }''',
    'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed;
public class ConfigManager { public static boolean enabled;
public static boolean isEnabled(String key){return enabled;} }''',
    'io/github/libxposed/api/XposedModule.java': '''package io.github.libxposed.api;
import java.lang.reflect.Method; import java.util.*;
public class XposedModule {
public interface Chain { Object proceed() throws Throwable; }
public interface Interceptor { Object run(Chain c) throws Throwable; }
public final Map<Method,Interceptor> hooks=new HashMap<>();
public class Builder { Method m; Builder(Method m){this.m=m;}
public void intercept(Interceptor i){hooks.put(m,i);} }
public Builder hook(Method m){return new Builder(m);}
public void deoptimize(Method m){} }''',
    'com/jc/code/v3/MainActivity.java': '''package com.jc.code.v3;
public class MainActivity { public String NU(){return "VPN";}
public String Ab(){return "Hook";} public boolean CBEvIj(){return false;}
public void HBzZAD(boolean a,boolean b,boolean c){} public void HPA(){}
public void h0(boolean b){} public class qGgY { public void run(){} } }''',
    'o/CrDc.java': '''package o; public class CrDc {
public static String mjFkc(String q){throw new AssertionError("Network must not run");} }''',
    'Check.java': '''import io.github.libxposed.api.XposedModule;
import com.lijinmu.arashi.xposed.*; import com.lijinmu.arashi.xposed.hooks.*;
public class Check {
public static void main(String[] args) throws Throwable {
if(HookConfig.defaultEnabled(HookConfig.KEY_JC_LOCAL_MODE)) throw new AssertionError("default on");
XposedModule m=new XposedModule(); JcCodeHooks.hook(m, Check.class.getClassLoader());
if(m.hooks.size()!=4) throw new AssertionError("missing hooks");
ConfigManager.enabled=true;
for(var e:m.hooks.entrySet()) {
Object v=e.getValue().run(()->{throw new AssertionError("original check/network called");});
switch(e.getKey().getName()) {
case "NU": case "Ab": if(v!=null) throw new AssertionError(); break;
case "CBEvIj": if(!Boolean.TRUE.equals(v)) throw new AssertionError(); break;
case "mjFkc": System.out.println(v); break;
}
}
ConfigManager.enabled=false;
for(var interceptor:m.hooks.values()) {
Object sentinel=new Object();
if(interceptor.run(()->sentinel)!=sentinel) throw new AssertionError("disabled hook changed result");
}
System.out.println("enabled/disabled hook checks passed");
} }'''
}
with tempfile.TemporaryDirectory() as temp:
    folder = Path(temp)
    sources = []
    for name, content in fixtures.items():
        p = folder / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)
        sources.append(str(p))
    base = root / 'app/src/main/java/com/lijinmu/arashi'
    sources += [str(base / name) for name in (
        'xposed/HookConfig.java', 'xposed/hooks/JcCodeHooks.java', 'xposed/hooks/JcCodePolicy.java')]
    subprocess.run(['javac', '-d', temp, *sources], check=True)
    result = subprocess.run(['java', '-cp', temp, 'Check'], check=True, capture_output=True, text=True)
    import json
    status = json.loads(result.stdout.splitlines()[0])
    assert status['enabled'] and not status['banned'] and not status['popup_on']
    assert status['force_min'] == '0' and status['latest'] == '0' and not status['block_pkgs']
    print(result.stdout.strip())
