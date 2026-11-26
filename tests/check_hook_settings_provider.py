"""Execute the Provider's Hook settings response with every public switch."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
ACTUAL = ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed'
source = (ACTUAL / 'SettingsProvider.java').read_text()
start = source.index('        Bundle result = new Bundle();', source.index(
    'SharedPreferences prefs = context.getSharedPreferences(HookConfig.PREF_NAME'))
end = source.index('        return result;', start) + len('        return result;')
response = source[start:end]
with tempfile.TemporaryDirectory(prefix='arashi-provider-check-') as directory:
    base = Path(directory)
    prop = base / 'android/os/SystemProperties.java'
    prop.parent.mkdir(parents=True)
    prop.write_text('''package android.os;
public class SystemProperties {
 public static String get(String key) { return System.getProperty("test.os.version"); }
}''')
    check = base / 'ProviderCheck.java'
    check.write_text('''import java.util.*;
import com.lijinmu.arashi.xposed.*;
public class ProviderCheck {
 static class Prefs {
  Map<String,Object> values = new HashMap<>();
  Map<String,?> getAll() { return values; }
  boolean getBoolean(String key, boolean fallback) {
   return values.containsKey(key) ? (Boolean)values.get(key) : fallback;
  }
  @SuppressWarnings("unchecked")
  Set<String> getStringSet(String key, Set<String> fallback) {
   return (Set<String>)values.getOrDefault(key, fallback);
  }
 }
 static class Bundle extends HashMap<String,Object> {
  void putBoolean(String key, boolean value) { put(key,value); }
  void putStringArrayList(String key, ArrayList<String> value) { put(key,value); }
 }
 static Bundle read(Prefs prefs) {
''' + response + '''
 }
 static void check(boolean ok) { if (!ok) throw new AssertionError(); }
 public static void main(String[] args) {
  Prefs prefs = new Prefs();
  Bundle defaults = read(prefs);
  for (String key : HookConfig.keys())
   check(Boolean.valueOf(HookConfig.defaultEnabled(key)).equals(defaults.get(key)));
  for (boolean enabled : new boolean[]{true,false}) {
   for (String key : HookConfig.keys()) prefs.values.put(key,enabled);
   Bundle bundle = read(prefs);
   for (String key : HookConfig.keys())
    check(Boolean.valueOf(enabled).equals(bundle.get(key)));
  }
  prefs.values.clear();
  prefs.values.put(HookConfig.KEY_AOSP_WIFI,true);
  check(Boolean.TRUE.equals(read(prefs).get(HookConfig.KEY_AOSP_STATUS_ICONS)));
  prefs.values.put(HookConfig.KEY_AOSP_STATUS_ICONS,false);
  check(Boolean.FALSE.equals(read(prefs).get(HookConfig.KEY_AOSP_STATUS_ICONS)));
  prefs.values.put(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST,Set.of("example.app"));
  check(read(prefs).get(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST).equals(List.of("example.app")));
  System.out.println("Provider switches, explicit overrides, defaults, legacy icons and whitelist passed");
 }
}''')
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', directory, str(prop), str(check),
        str(ACTUAL / 'HookConfig.java'), str(ACTUAL / 'BehaviorCompatibility.java')], check=True)
    for version in ('V7.10.0', 'V7.13.0', 'V7.14.0', 'V9.1.2', 'unknown'):
        subprocess.run(['java', '-Dtest.os.version=' + version, '-cp', directory,
                        'ProviderCheck'], check=True)
