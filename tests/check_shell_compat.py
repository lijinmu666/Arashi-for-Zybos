"""Run stripped-firmware command handling against session helpers; no Android device."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ARASHI = (ROOT / 'app').is_dir()
pkg = 'com.lijinmu.arashi.xposed.hooks' if ARASHI else 'com.lijinmu.zygiskarashi.install'
production = ROOT / ('app/src/main/java' if ARASHI else 'src/main/java') / Path(pkg.replace('.', '/')) / 'ShellInstallCompat.java'

with tempfile.TemporaryDirectory() as directory:
    base = Path(directory)
    files = {
        pkg.replace('.', '/') + '/ShellInstallCompat.java': production.read_text(),
        'android/content/pm/PackageInstaller.java': '''package android.content.pm;
public class PackageInstaller { public static class SessionParams {
public int installFlags,mode,installLocation,installReason; public long sizeBytes=-1;
public String appPackageName,abiOverride; public boolean isStaged,isMultiPackage;
public SessionParams(int mode){this.mode=mode;}
}}''',
        'Check.java': '''import java.io.*; import java.util.*;
import android.content.pm.PackageInstaller.SessionParams;
import ''' + pkg + '''.ShellInstallCompat;
public class Check {
static void check(boolean ok){if(!ok)throw new AssertionError();}
public static class FD {public long getStatSize(){return 100;}public void close(){closed++;} static int closed;}
// Deliberately has no runInstall/runStreamingInstall/runInstallCreate/runInstallCommit methods.
public static class Shell {
String[] args;int cursor,created,writes,commits,abandoned;boolean writeFail,writeThrow,commitFail,missingFile;
SessionParams params;String installer;int user;List<String> names=new ArrayList<>();StringWriter output=new StringWriter(),error=new StringWriter();
Shell(String...args){this.args=args;}
public String getNextOption(){if(cursor==args.length)return null;String a=args[cursor];if(a.equals("--")){cursor++;return null;}return a.startsWith("-")&&!a.equals("-")?args[cursor++]:null;}
public String getNextArg(){return cursor<args.length?args[cursor++]:null;}
public PrintWriter getOutPrintWriter(){return new PrintWriter(output);}
public PrintWriter getErrPrintWriter(){return new PrintWriter(error);}
public FD openFileForSystem(String p,String mode){return missingFile?null:new FD();}
public int doCreateSession(SessionParams p,String installer,int user){created++;this.params=p;this.installer=installer;this.user=user;return 42;}
public int doWriteSplit(int id,String path,long size,String name,boolean log){check(id==42);writes++;names.add(name);if(writeThrow)throw new IllegalStateException("write broke");return writeFail?1:0;}
public int doCommitSession(int id,boolean log){commits++;if(commitFail)return 1;return 0;}
public int doAbandonSession(int id,boolean log){abandoned++;return 0;}
}
public static void main(String[] args){
Shell s=new Shell("-r","-t","-g","--user","0","/data/local/tmp/app.apk");
check(ShellInstallCompat.run(s,"install")==0);check(s.created==1&&s.writes==1&&s.commits==1&&s.abandoned==0);
check(s.params.installFlags==(2|4|256)&&s.user==0&&s.params.sizeBytes==100&&FD.closed==1);
s=new Shell("-S","2048","-");check(ShellInstallCompat.run(s,"install")==0&&s.params.sizeBytes==2048);
s=new Shell("-S","2048");check(ShellInstallCompat.run(s,"install-streaming")==0&&s.writes==1);
s=new Shell("a.apk","b.apk");check(ShellInstallCompat.run(s,"install")==0&&s.params.sizeBytes==200&&s.writes==2&&new HashSet<>(s.names).size()==2);
s=new Shell("-R","-d","-p","target","-i","installer","--user","current","a.apk");
check(ShellInstallCompat.run(s,"install")==0&&s.params.mode==2&&s.params.installFlags==128&&s.user==-2&&s.installer.equals("installer"));
s=new Shell("--multi-package","--staged");check(ShellInstallCompat.run(s,"install-create")==0&&s.created==1&&s.writes==0&&s.commits==0&&s.params.isMultiPackage&&s.params.isStaged&&s.output.toString().contains("[42]"));
s=new Shell("42");check(ShellInstallCompat.run(s,"install-commit")==0&&s.commits==1&&s.created==0);
for(String[] bad:new String[][]{{"-S","0"},{"-S","-1"},{"-S","x"},{"--unknown"},{"--user"},{"-"},{"--multi-package","a.apk"},{"-S","10","-","b.apk"}}){
s=new Shell(bad);check(ShellInstallCompat.run(s,"install")==1&&s.created==0&&!s.error.toString().isEmpty());}
s=new Shell("a.apk");s.missingFile=true;check(ShellInstallCompat.run(s,"install")==1&&s.created==0);
s=new Shell("a.apk");s.writeFail=true;check(ShellInstallCompat.run(s,"install")==1&&s.created==1&&s.writes==1&&s.commits==0&&s.abandoned==1);
s=new Shell("a.apk");s.writeThrow=true;check(ShellInstallCompat.run(s,"install")==1&&s.abandoned==1&&s.error.toString().contains("write broke"));
s=new Shell("a.apk");s.commitFail=true;check(ShellInstallCompat.run(s,"install")==1&&s.commits==1&&s.abandoned==1);
s=new Shell("42","extra");check(ShellInstallCompat.run(s,"install-commit")==1&&s.commits==0);
s=new Shell("a.apk");check(ShellInstallCompat.run(s,"install-incremental")==1&&s.created==0&&s.error.toString().contains("--no-incremental"));
check(!ShellInstallCompat.supports("list")&&ShellInstallCompat.supports("install-create"));
System.out.println("PASS: stripped-firmware file/stdin/split/create/commit, flags, errors and cleanup");
}}
''',
    }
    for name, contents in files.items():
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents)
    subprocess.run(['javac', '--release', '8', '-d', str(base / 'classes'), *map(str, base.rglob('*.java'))], check=True)
    subprocess.run(['java', '-cp', str(base / 'classes'), 'Check'], check=True)
