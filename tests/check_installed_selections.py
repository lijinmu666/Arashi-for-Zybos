"""Verify pruning only confirmed missing packages, preserving UIDs and lookup failures."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'android/content/pm/ApplicationInfo.java': 'package android.content.pm; public class ApplicationInfo {}',
    'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager {
 public static class NameNotFoundException extends Exception {}
 public ApplicationInfo getApplicationInfo(String name,int flags)throws NameNotFoundException {
  if(flags!=0)throw new AssertionError("must not include uninstalled packages");
  if(name.equals("gone.app"))throw new NameNotFoundException();
  if(name.equals("private.app"))throw new SecurityException("denied");
  if(name.equals("busy.app"))throw new IllegalStateException("binder unavailable");
  return new ApplicationInfo();
 }
}''',
    'Check.java': '''import java.util.*;
import android.content.pm.PackageManager;
import com.lijinmu.arashi.settings.InstalledAppSelections;
public class Check {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var pm=new PackageManager();
  var raw=Set.of("present.app","gone.app","private.app","busy.app");
  check(InstalledAppSelections.findMissing(pm,raw,false).equals(Set.of("gone.app")));
  check(raw.size()==4);
  var prefixed=Set.of("pkg:present.app","pkg:gone.app","pkg:private.app","pkg:busy.app","uid:1000","uid:2000");
  check(InstalledAppSelections.findMissing(pm,prefixed,true).equals(Set.of("pkg:gone.app")));
  check(InstalledAppSelections.findMissing(pm,Set.of(),true).isEmpty());
  // A restored/reinstalled package no longer produces a miss.
  check(InstalledAppSelections.findMissing(new PackageManager(){
   public android.content.pm.ApplicationInfo getApplicationInfo(String n,int f){
    return new android.content.pm.ApplicationInfo();
   }},Set.of("pkg:gone.app"),true).isEmpty());
  System.out.println("Installed selections: missing-only pruning, UID preservation and error guards passed");
 }
}''',
}
with tempfile.TemporaryDirectory(prefix='arashi-selection-') as directory:
    tmp=Path(directory)
    for name,source in STUBS.items():
        p=tmp/name
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(source)
    compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
    subprocess.run([*compiler,'-d',str(tmp/'classes'),*map(str,tmp.rglob('*.java')),
                    str(ROOT/'app/src/main/java/com/lijinmu/arashi/settings/InstalledAppSelections.java')],check=True)
    subprocess.run(['java','-cp',str(tmp/'classes'),'Check'],check=True)
