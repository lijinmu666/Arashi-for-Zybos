"""Exercise the actual Shell/blacklist interceptors with small host framework doubles.

No APK or Android native library is built. Only this Java test harness is compiled.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed/hooks/InstallHooks.java').read_text()
dispatch = source[source.index('    private static void hookPackageManagerShellCommand('):
                  source.index('    private static void hookZybosNoArg(')]
blacklist = source[source.index('    private static void hookInstallPackageHelper('):
                   source.index('    private static void hookZybosServiceCtor(')]
clear = source[source.index('    @SuppressWarnings("unchecked")'):
               source.index('    private static void clearListField(')]
reflect = (ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed/Reflect.java').read_text()
reflect = reflect.replace('package com.lijinmu.arashi.xposed;', '')

with tempfile.TemporaryDirectory() as directory:
    base = Path(directory)
    files = {
        'Check.java': '''import java.lang.reflect.*;
import java.io.PrintWriter;
import java.util.*;
import android.util.ArraySet;
import com.android.server.pm.*;
public class Check {
static String TAG="test";
''' + dispatch + blacklist + clear + '''
static void check(boolean ok) {if(!ok)throw new AssertionError();}
public static void main(String[] args) throws Throwable {
XposedModule m=new XposedModule(); ClassLoader cl=Check.class.getClassLoader();
hookPackageManagerShellCommand(m,cl); hookShellSessionCreation(m,cl);
PackageManagerShellCommand shell=new PackageManagerShellCommand();
Method command=shell.getClass().getMethod("onCommand",String.class);
String[] commands={"install","install-streaming","install-incremental","install-commit","install-create"};
int[] original={0};
XposedModule.Action fallback=()->{original[0]++;return -1;};
ConfigManager.shell=true;
for(int i=0;i<commands.length;i++) {
shell.calls=0;original[0]=0;
Object result=m.call(command,shell,new Object[]{commands[i]},fallback);
check(result.equals(10+i));check(shell.calls==1&&original[0]==0);
}
// Real handler exceptions remain visible and never retry the original dispatcher.
shell.throwing=true;shell.calls=0;original[0]=0;
check(m.call(command,shell,new Object[]{"install"},fallback).equals(1));
check(shell.calls==1&&original[0]==0&&shell.errors.toString().contains("handler failed after consuming input"));
shell.throwing=false;
original[0]=0;ConfigManager.shell=false;
check(m.call(command,shell,new Object[]{"install"},fallback).equals(-1)&&original[0]==1);
original[0]=0;ConfigManager.shell=true;
check(m.call(command,shell,new Object[]{"list"},fallback).equals(-1)&&original[0]==1);

PackageInstallerService service=new PackageInstallerService();
Method guard=service.getClass().getDeclaredMethod("enforceZybInstallerAllowed",int.class,int.class,String.class,String.class);
int[] guarded={0}; XposedModule.Action enforce=()->{guarded[0]++;return null;};
Object[] shellArgs={2000,321,"shell",null};
m.call(guard,service,shellArgs,enforce);check(guarded[0]==0);
m.call(guard,service,new Object[]{0,321,"root",null},enforce);check(guarded[0]==1);
m.call(guard,service,new Object[]{12345,321,"app",null},enforce);check(guarded[0]==2);
ConfigManager.shell=false;m.call(guard,service,shellArgs,enforce);check(guarded[0]==3);

// Probe both actual vendor owners, irrespective of Android SDK version.
hookInstallPackageHelper(m,cl);hookLegacyPackageManagerBlacklist(m,cl);
for(Class<?> owner:new Class<?>[]{InstallPackageHelper.class,PackageManagerService.class}) {
Field field=owner.getField("mZybBlacklistApp");
@SuppressWarnings("unchecked") Set<String> values=(Set<String>)field.get(null);
Constructor<?> ctor=owner.getConstructor();values.add("blocked");ConfigManager.blacklist=true;
m.call(ctor,null,new Object[0],()->ctor.newInstance());check(values.isEmpty());
values.add("blocked");ConfigManager.blacklist=false;
m.call(ctor,null,new Object[0],()->ctor.newInstance());check(values.contains("blocked"));
values.clear();
}
System.out.println("Shell handlers, errors, configuration, session UID and blacklist layout checks passed");
}}
class ConfigManager {
static boolean shell,blacklist;
static boolean isEnabled(String key){return key.equals("shell")?shell:blacklist;}
}
class HookConfig {static String KEY_SHELL_INSTALL="shell",KEY_CLEAR_BLACKLIST="blacklist";}
class Log {static void i(String t,String s){}static void w(String t,String s){}static void w(String t,String s,Throwable e){}}
class XposedModule {
interface Action {Object run()throws Throwable;}
interface Interceptor {Object run(Chain chain)throws Throwable;}
record Chain(Object getThisObject,Object[] args,Action original){
Object getArg(int i){return args[i];}Object proceed()throws Throwable{return original.run();}}
Map<Executable,Interceptor> hooks=new HashMap<>();
class Builder {Executable target;Builder(Executable e){target=e;}void intercept(Interceptor i){hooks.put(target,i);}}
Builder hook(Executable e){return new Builder(e);}void deoptimize(Method method){}
Object call(Executable e,Object o,Object[] args,Action original)throws Throwable {
return hooks.containsKey(e)?hooks.get(e).run(new Chain(o,args,original)):original.run();}
}
''',
        'Reflect.java': reflect,
        'android/util/ArraySet.java': '''package android.util;
public class ArraySet<E> extends java.util.HashSet<E>{}''',
        'com/android/server/pm/PackageManagerShellCommand.java': '''package com.android.server.pm;
public class PackageManagerShellCommand {
public int calls;public boolean throwing;public java.io.StringWriter errors=new java.io.StringWriter();
public int onCommand(String s){return -1;}
public java.io.PrintWriter getErrPrintWriter(){return new java.io.PrintWriter(errors);}
private int run(int value){calls++;if(throwing)throw new IllegalStateException("handler failed after consuming input");return value;}
private int runInstall(){return run(10);}private int runStreamingInstall(){return run(11);}
private int runIncrementalInstall(){return run(12);}private int runInstallCommit(){return run(13);}
private int runInstallCreate(){return run(14);}
}''',
        'com/android/server/pm/PackageInstallerService.java': '''package com.android.server.pm;
public class PackageInstallerService {
private void enforceZybInstallerAllowed(int uid,int pid,String process,String installer){}
private int createSessionInternal(){return 0;}
}''',
        'com/android/server/pm/PackageManagerService.java': '''package com.android.server.pm;
public class PackageManagerService {
public static final android.util.ArraySet<String> mZybBlacklistApp=new android.util.ArraySet<>();
public void onShellCommand(){}
}''',
        'com/android/server/pm/InstallPackageHelper.java': '''package com.android.server.pm;
public class InstallPackageHelper {
public static final android.util.ArraySet<String> mZybBlacklistApp=new android.util.ArraySet<>();
}''',
    }
    for name, contents in files.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
    subprocess.run(['javac', '-d', str(base / 'classes'),
                    *map(str, base.rglob('*.java'))], check=True)
    subprocess.run(['java', '-cp', str(base / 'classes'), 'Check'], check=True)
