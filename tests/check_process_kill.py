"""Exercise process selection in a fake /proc tree; never signal real processes."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix="arashi-process-kill-") as directory:
    temp = Path(directory)
    source = ROOT / "app/src/main/java/com/lijinmu/arashi/tools/ScopedAppStopper.java"
    stub = temp / "RootShell.java"
    stub.write_text("""package com.lijinmu.arashi.tools;
final class RootShell {
 static boolean ok=true;static String output="ROOT_GRANTED";
 static class Result {boolean ok;String output;Result(boolean a,String b){ok=a;output=b;}}
 static Result run(String c){return new Result(ok,output);}
}""")
    check = temp / "Check.java"
    check.write_text("""package com.lijinmu.arashi.tools;
public class Check {public static void main(String[] args){
 if(!ScopedAppStopper.stop().ok)throw new AssertionError();
 RootShell.ok=false;RootShell.output="ROOT_GRANTED\\nAPP_KILL_FAILED";
 if(ScopedAppStopper.stop().ok||!ScopedAppStopper.stop().output.contains("APP_KILL_FAILED"))throw new AssertionError();
 System.out.print(ScopedAppStopper.buildCommand());
}}""")
    compiler = [shutil.which("javac")] if shutil.which("javac") else ["java", "com.sun.tools.javac.Main"]
    subprocess.run(compiler + ["-d", str(temp), str(stub), str(check), str(source)], check=True)
    command = subprocess.check_output(["java", "-cp", str(temp),
            "com.lijinmu.arashi.tools.Check"], text=True)
    assert "force-stop" not in command and "am kill" not in command
    assert "com.lijinmu.arashi" not in command and "self_pids" not in command and "pidof" not in command
    fake = temp / "proc"
    names = {
        101: "com.android.systemui",
        102: "com.android.systemui:service",
        103: "com.android.settings",
        104: "com.android.launcher3",
        105: "com.zuoyebang.iot.pad.launcher14",
        106: "com.android.packageinstaller",
        107: "com.android.captiveportallogin",
        108: "com.zuoyebang.iot.pad.supervise",
        109: "com.zuoyebang.iot.zptcpservice",
        201: "system_server",
        202: "com.android.systemui.fake",
        203: "unrelated",
        204: "com.lijinmu.arashi",
        205: "com.lijinmu.arashi:worker",
    }
    for pid, name in names.items():
        folder = fake / str(pid)
        folder.mkdir(parents=True)
        (folder / "cmdline").write_bytes(name.encode() + b"\0ignored argument")
    command = command.replace("/proc/", str(fake) + "/")
    # Shell functions intercept every signal, privilege check, and self-PID query.
    # No root or real /proc access is used by the test.
    prelude = """id() { echo "$FAKE_UID"; }
pidof() { return 1; }
kill() { echo "$2" >> "$KILL_LOG"; [ "$2" != "$FAIL_PID" ]; }
"""
    def run(uid="0", fail=""):
        log = temp / "signals"
        log.write_text("")
        env = dict(os.environ, FAKE_UID=uid, FAIL_PID=fail, KILL_LOG=str(log))
        result = subprocess.run(["sh", "-c", prelude + command], env=env,
                                capture_output=True, text=True)
        return result, set(log.read_text().split())
    result, killed = run()
    assert result.returncode == 0 and killed == {str(i) for i in range(101, 110)}
    result, killed = run("2000")
    assert result.returncode == 77 and not killed and "ROOT_REQUIRED" in result.stdout
    result, killed = run(fail="101")
    assert result.returncode != 0 and "APP_KILL_FAILED" in result.stdout
    for folder in fake.iterdir():
        (folder / "cmdline").unlink()
        folder.rmdir()
    result, killed = run()
    assert result.returncode == 0 and not killed
print("Fixed process kill: exact names, subprocesses, missing targets, root refusal and failures passed")
