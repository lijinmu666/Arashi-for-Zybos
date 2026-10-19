"""Exercise the real environment JSON merge, shell argument, root failure and readback checks."""
from pathlib import Path
import hashlib
import subprocess
import sys
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SHA256 = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
CHECK = r'''package com.arashi.tools;
import org.json.*;
import java.nio.charset.StandardCharsets;
public class EnvCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  ZybosEnvConfig defaults=ZybosEnvConfig.parse("");
  check(defaults.environment.equals("PRODUCTION")&&!defaults.showDebugTools&&!defaults.tips);
  check(new JSONObject(defaults.json).getJSONArray("tcpServerConfigs").length()==0);
  for(String env:new String[]{"PRODUCTION","TEST","MOCK"}) {
   JSONObject source=new JSONObject(ZybosEnvConfig.DEFAULT_JSON);
   source.getJSONArray("tcpServerConfigs").put(new JSONObject().put("host","server.invalid").put("port",123));
   // This stays data even when passed through su -c, including quotes and shell substitutions.
   source.put("futureField","a'$(printf INJECTED) `printf INJECTED`;\\\"中文");
   String updated=ZybosEnvConfig.update(source.toString(),env,true,true);
   JSONObject result=new JSONObject(updated);
   check(result.getString("env").equals(env)&&result.getBoolean("isShowDebugTools")&&result.getBoolean("isTips"));
   check(result.getJSONArray("tcpServerConfigs").getJSONObject(0).getInt("port")==123);
   check(result.getString("futureField").equals(source.getString("futureField")));
   ProcessBuilder shell=new ProcessBuilder("sh","-c",
    "setprop() { printf '%s' \"$2\" > \"$ARASHI_ENV_TEST_CAPTURE\"; }; getprop() { cat \"$ARASHI_ENV_TEST_CAPTURE\"; }; "
    +ZybosEnvConfig.writeCommand(updated));
   shell.environment().put("ARASHI_ENV_TEST_CAPTURE",args[0]);
   Process process=shell.start();String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
   check(process.waitFor()==0&&output.equals(updated));
   RootShell.reply=updated;RootShell.ok=true;
   ZybosEnvController controller=new ZybosEnvController();
   check(controller.applyConfig(source.toString(),env,true,true).json.equals(updated));
   check(RootShell.command.equals(ZybosEnvConfig.writeCommand(updated)));
  }
  try{ZybosEnvConfig.update(defaults.json,"UNKNOWN",false,false);throw new AssertionError();}
  catch(IllegalArgumentException expected){}
  try{ZybosEnvConfig.parse("broken");throw new AssertionError();}catch(JSONException expected){}
  ZybosEnvController controller=new ZybosEnvController();
  RootShell.reply=ZybosEnvConfig.DEFAULT_JSON;RootShell.ok=true;
  check(controller.resetConfig().json.equals(ZybosEnvConfig.DEFAULT_JSON));
  check(RootShell.command.equals("setprop persist.sys.env_config '"+ZybosEnvConfig.DEFAULT_JSON+"' && getprop persist.sys.env_config"));
  controller.readConfig();check(RootShell.command.equals("getprop persist.sys.env_config"));
  check(controller.readFormattedProperty().contains("\n"));
  RootShell.reply="";check(controller.readFormattedProperty().equals("属性为空"));
  RootShell.reply="broken";check(controller.readFormattedProperty().contains("broken"));
  RootShell.ok=false;RootShell.reply="permission denied";
  try{controller.resetConfig();throw new AssertionError();}catch(IllegalStateException expected){check(expected.getMessage().contains("permission denied"));}
  RootShell.ok=true;RootShell.reply="different";
  try{controller.resetConfig();throw new AssertionError();}catch(IllegalStateException expected){check(expected.getMessage().contains("不一致"));}
  System.out.println("Zybos environment merge, shell quoting, default reset and readback checks passed");
 }
}'''
STUBS = {
 'com/arashi/ui/BasePage.java': 'package com.arashi.ui; public class BasePage {protected void populate(){}}',
 'com/arashi/ui/MiuixPages.java': 'package com.arashi.ui; public class MiuixPages {public static void renderZybosEnvController(com.arashi.tools.ZybosEnvController a){}}',
 'com/arashi/tools/RootShell.java': '''package com.arashi.tools; class RootShell {
 static String reply,command;static boolean ok;static class Result{boolean ok;String output;Result(boolean b,String s){ok=b;output=s;}}
 static Result run(String s){command=s;return new Result(ok,reply);} }''',
 'com/arashi/tools/EnvCheck.java': CHECK,
}
with tempfile.TemporaryDirectory(prefix='arashi-env-') as directory:
    tmp=Path(directory)
    jar=Path(sys.argv[1]) if len(sys.argv)>1 else tmp/'json.jar'
    if not jar.exists():
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=30) as response:
            jar.write_bytes(response.read())
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==SHA256, 'Unexpected JSON test dependency'
    for name,source in STUBS.items():
        target=tmp/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source)
    real=ROOT/'app/src/main/java/com/arashi/tools'
    subprocess.run(['javac','-cp',str(jar),'-d',str(tmp/'classes'),*map(str,tmp.rglob('*.java')),
                    str(real/'ZybosEnvConfig.java'),str(real/'ZybosEnvController.java')],check=True)
    subprocess.run(['java','-cp',str(tmp/'classes')+':'+str(jar),'com.arashi.tools.EnvCheck',str(tmp/'capture')],check=True)
