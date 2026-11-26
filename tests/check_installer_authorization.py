"""Run the authorization interceptors against the 7.14 call sequence."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as directory:
    base = Path(directory)
    source = (ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed/hooks/InstallHooks.java').read_text()
    method = source[source.index('    private static void hookInstallerAuthorization'):source.index('    /**\n     * Use the PackageManager')]
    sources = {
        'Check.java': '''import java.lang.reflect.*;
public class Check {
static String TAG="test";
''' + method + '''
static void check(boolean ok) { if (!ok) throw new AssertionError(); }
public static void main(String[] args) throws Throwable {
XposedModule m=new XposedModule(); hookInstallerAuthorization(m, Check.class.getClassLoader());
Class<?> c=Class.forName("com.android.server.pm.PackageInstallerSession"); Object s=c.getConstructor().newInstance();
Method install=c.getMethod("handleInstall"), privileged=c.getMethod("isZybInstallerPrivileged"), installer=c.getMethod("getInstallerPackageName");
XposedModule.Action original=()-> {
check("third.party".equals(m.call(installer,s,()->"third.party")));
boolean p=(Boolean)m.call(privileged,s,()->false);
Object name=m.call(installer,s,()->"third.party");
check(p==ConfigManager.enabled); check(ConfigManager.enabled ? name==null : "third.party".equals(name));
// Subsequent verification reads keep the real installer identity.
check("third.party".equals(m.call(installer,s,()->"third.party"))); return null;
};
m.call(install,s,original); ConfigManager.enabled=true; m.call(install,s,original);
try { m.call(install,s,()->{m.call(privileged,s,()->false);throw new IllegalStateException();}); } catch(IllegalStateException expected) {}
check(!(Boolean)m.call(privileged,s,()->false));
check("third.party".equals(m.call(installer,s,()->"third.party")));
System.out.println("Installer authorization checks passed");
}}
class ConfigManager { static boolean enabled; static boolean isEnabled(String k){return enabled;} }
class HookConfig { static String KEY_DISABLE_INSTALLER_AUTH="auth"; }
class Reflect {static Method findMethodExact(Class<?> c,String n) throws Exception {return c.getDeclaredMethod(n);} }
class Log {static void i(String t,String m){} static void w(String t,String m){} }
class XposedModule {
interface Action {Object run() throws Throwable;} interface Interceptor {Object run(Chain c) throws Throwable;}
record Chain(Object getThisObject, Action original) {Object proceed() throws Throwable{return original.run();}}
java.util.Map<Method,Interceptor> hooks=new java.util.HashMap<>();
class Builder {Method method; Builder(Method m){method=m;} void intercept(Interceptor i){hooks.put(method,i);}}
void deoptimize(Method m){}
Builder hook(Method m){return new Builder(m);}
Object call(Method m,Object o,Action p) throws Throwable {return hooks.containsKey(m)?hooks.get(m).run(new Chain(o,p)):p.run();}
}
''',
        'com/android/server/pm/PackageInstallerSession.java': '''package com.android.server.pm;
public class PackageInstallerSession { public void handleInstall() {} public boolean isZybInstallerPrivileged(){return false;} public String getInstallerPackageName(){return "third.party";} }''',
    }
    for name, contents in sources.items():
        path=base/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(contents)
    subprocess.run(['javac','-d',str(base/'classes'),*map(str,base.rglob('*.java'))],check=True)
    subprocess.run(['java','-cp',str(base/'classes'),'Check'],check=True)
