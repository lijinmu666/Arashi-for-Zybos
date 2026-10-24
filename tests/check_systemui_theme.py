"""Execute the real hook callbacks against host resource and WebView-free QS fixtures.

Checks resource ownership, 12/14 state signatures, drawable isolation and contrast;
does not claim to verify device rendering or lockscreen authentication.
"""
from pathlib import Path
import ast
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
node = next(n.value for n in ast.parse((ROOT / 'tests/check_navigation_access.py').read_text()).body
            if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'STUBS' for t in n.targets))
base = ast.literal_eval(node)
stubs = {key: base[key] for key in ['io/github/libxposed/api/XposedInterface.java',
                                  'io/github/libxposed/api/XposedModule.java', 'android/util/Log.java']}
stubs.update({
'com/lijinmu/arashi/xposed/hooks/SystemUiQsHooks.java': "package com.lijinmu.arashi.xposed.hooks; public class SystemUiQsHooks {static boolean enabled(){return com.lijinmu.arashi.xposed.ConfigManager.qs;}static void restoreBrightness(android.view.View root){}static int neutral(android.content.res.Resources r,boolean f){return f?0xffeeeeee:0xff222222;}}",
'android/R.java': '''package android;public class R {
 public static class attr {public static final int state_enabled=1,state_pressed=2,state_selected=3;}
 public static class id {public static final int progress=4;}
 public static class color {public static final int system_accent1_100=1001,system_accent1_600=1002;}}
''',
'android/util/TypedValue.java': 'package android.util;public class TypedValue {}',
'android/content/res/ColorStateList.java': '''package android.content.res;
public class ColorStateList {
 public int[][] states;public int[] colors;public ColorStateList(int[][] s,int[] c){states=s;colors=c;}
 public static ColorStateList valueOf(int color){return new ColorStateList(new int[][]{{}},new int[]{color});}
 public int getDefaultColor(){return colors[colors.length-1];}
 public int getColorForState(int[] current,int fallback){outer:for(int i=0;i<states.length;i++){
  for(int s:states[i]){boolean present=false;for(int c:current)if(c==Math.abs(s))present=true;
   if(s>0&&!present||s<0&&present)continue outer;}return colors[i];}return fallback;}}
''',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;import android.content.res.Resources;
public abstract class Drawable {public abstract Drawable copy();public boolean fail;
 public Drawable mutate(){return this;}public ConstantState getConstantState(){return new ConstantState(){
  public Drawable newDrawable(Resources r){if(fail)throw new IllegalStateException();return copy();}};}
 public abstract static class ConstantState {public abstract Drawable newDrawable(Resources r);}}
''',
'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable;
public class GradientDrawable extends Drawable {public int color;public int radius=18,width=80;
 public GradientDrawable(int c){color=c;}public void setColors(int[] c){color=c[0];}
 public Drawable copy(){var d=new GradientDrawable(color);d.radius=radius;d.width=width;return d;}}
''',
'android/graphics/drawable/DrawableWrapper.java': '''package android.graphics.drawable;
public class DrawableWrapper extends Drawable {Drawable child;public DrawableWrapper(Drawable d){child=d;}
 public Drawable getDrawable(){return child;}public Drawable copy(){return new DrawableWrapper(child.copy());}}
''',
'android/graphics/drawable/LayerDrawable.java': '''package android.graphics.drawable;
public class LayerDrawable extends Drawable {public Drawable progress;public LayerDrawable(Drawable p){progress=p;}
 public Drawable findDrawableByLayerId(int id){return id==android.R.id.progress?progress:null;}
 public Drawable copy(){return new LayerDrawable(progress.copy());}}
''',
'android/graphics/drawable/StateListDrawable.java': '''package android.graphics.drawable;
public class StateListDrawable extends Drawable {public int[][] states;public Drawable[] children;
 public StateListDrawable(int[][] s,Drawable[] c){states=s;children=c;}public int getStateCount(){return states.length;}
 public int[] getStateSet(int i){return states[i];}public Drawable getStateDrawable(int i){return children[i];}
 public Drawable copy(){Drawable[] c=new Drawable[children.length];for(int i=0;i<c.length;i++)c[i]=children[i].copy();return new StateListDrawable(states,c);}}
''',
'android/content/res/Resources.java': '''package android.content.res;import java.util.*;import android.graphics.drawable.*;
public class Resources {public static class Theme {}public static class NotFoundException extends RuntimeException {}
 public String owner="com.android.systemui";public Map<Integer,String> names=new HashMap<>();public Drawable drawable;
 public String getResourceName(int id){if(!names.containsKey(id))throw new NotFoundException();return owner+":"+names.get(id);}
 public int getIdentifier(String name,String type,String pkg){if(name.equals("slider"))return 200;return Integer.parseInt(name.substring(name.length()-1))+100;}
 public Configuration config=new Configuration();public int light=0xff245638,dark=0xffabc9ad;
 public Configuration getConfiguration(){return config;}public boolean getBoolean(int id){return false;}
 public int getColor(int id){if(id==android.R.color.system_accent1_100)return dark;if(id==android.R.color.system_accent1_600)return light;return 0xff123456;}public int getColor(int id,Theme t){return getColor(id);}
 public ColorStateList getColorStateList(int id,Theme t){return ColorStateList.valueOf(0xff123456);}
 public Drawable getDrawable(int id){return drawable;}public Drawable getDrawableForDensity(int id,int density,Theme t){return drawable;}
 public Drawable loadDrawable(android.util.TypedValue v,int id,int density,Theme t){return drawable;}}
''',
'android/view/View.java': '''package android.view;import android.content.res.Resources;import java.util.*;
public class View {public Resources resources=new Resources();public Map<Integer,View> children=new HashMap<>();
 public android.graphics.drawable.Drawable background;public android.graphics.drawable.Drawable getBackground(){return background;}
 public void setBackground(android.graphics.drawable.Drawable d){background=d;}
 public Resources getResources(){return resources;}public View findViewById(int id){return children.get(id);}}
''',
'android/widget/ImageView.java': 'package android.widget;public class ImageView extends android.view.View {}',
'android/widget/TextView.java': '''package android.widget;import android.content.res.ColorStateList;
public class TextView extends android.view.View {public ColorStateList colors=ColorStateList.valueOf(0xffdddddd);
 public ColorStateList getTextColors(){return colors;}public void setTextColor(ColorStateList c){colors=c;}}
''',
'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed;public class ConfigManager {
 public static boolean enabled,monet,qs;public static int color=0xff245638;public static boolean isEnabledCached(String k){return HookConfig.KEY_AOSP_QS.equals(k)?qs:HookConfig.KEY_SYSTEMUI_MONET.equals(k)?monet:enabled;}
}
''',
'com/android/systemui/qs/tileimpl/QSTileViewImpl.java': '''package com.android.systemui.qs.tileimpl;
public class QSTileViewImpl extends android.view.View {
 public int refreshes;public QSIconViewImpl icon=new QSIconViewImpl();public QSIconViewImpl getIcon(){return icon;}
 public int getBackgroundColorForState(int s){return 10;}public int getBackgroundColorForState(int s,boolean disabled){return 10;}
 public int getLabelColorForState(int s){return 11;}public int getLabelColorForState(int s,boolean disabled){return 11;}
 public int getSecondaryLabelColorForState(int s){return 12;}public int getSecondaryLabelColorForState(int s,boolean disabled){return 12;}
 public void handleStateChanged(Object s){refreshes++;}}
''',
'com/android/systemui/qs/tileimpl/QSIconViewImpl.java': '''package com.android.systemui.qs.tileimpl;
public class QSIconViewImpl {public int mState=2;public static class State {public int state=2;public boolean disabledByPolicy;}
 public void setIcon(android.widget.ImageView v,State s,boolean animate){}public void setIcon(State s,boolean a){}}
''',
'com/android/keyguard/KeyguardPasswordViewZyb.java': '''package com.android.keyguard;
public class KeyguardPasswordViewZyb extends android.view.View {public void onFinishInflate(){}}
''',
'Check.java': '''package com.lijinmu.arashi.xposed.hooks;
import android.content.res.*;import android.graphics.drawable.*;import java.lang.reflect.*;
import com.lijinmu.arashi.xposed.*;import io.github.libxposed.api.*;
public class Check {
 static XposedModule module=new XposedModule();static void check(boolean b){if(!b)throw new AssertionError();}
 static Object call(Method m,Object self,Object...a)throws Throwable{
  m.setAccessible(true);return module.hooks.get(m).intercept(new XposedInterface.Chain(self,a,p->m.invoke(self,p)));}
 static int fill(Drawable d){return ((GradientDrawable)d).color;}
 public static void main(String[] ignored)throws Throwable {
  check(SystemUiThemeColors.parse(" #c8B6ff ")==0xffc8b6ff);
  for(String s:new String[]{"", "#abc", "#12345678", "0x123456", "#GG0000"})check(SystemUiThemeColors.parse(s)==null);
  check(SystemUiThemeColors.parse(null)==null);check(SystemUiThemeColors.opaque(0x123456)==0xff123456);
  check(SystemUiThemeColors.format(0xff00ab12).equals("#00AB12"));
  for(int r=0;r<256;r+=17)for(int g=0;g<256;g+=17)for(int b=0;b<256;b+=17){
   int c=0xff000000|(r<<16)|(g<<8)|b;int fg=SystemUiThemeColors.foreground(c);
   double l=.2126*linear(r)+.7152*linear(g)+.0722*linear(b);
   check((fg==0xff000000?(l+.05)/.05:1.05/(l+.05))>=4.5);
  }
  check(!HookConfig.defaultEnabled(HookConfig.KEY_SYSTEMUI_MONET));
  SystemUiMonetHooks.hookSystemUI(module,Check.class.getClassLoader());
  SystemUiThemeHooks.hookSystemUI(module,Check.class.getClassLoader());
  Resources res=new Resources();res.names.put(1,"color/qs_selected_background");res.names.put(2,"color/qs_tile_text_and_icon_color");
  Method color=Resources.class.getMethod("getColor",int.class);
  ConfigManager.monet=false;check((int)call(color,res,1)==0xff123456);
  ConfigManager.monet=true;check((int)call(color,res,1)==ConfigManager.color);check((int)call(color,res,2)==0xff123456);
  res.owner="another.package";check((int)call(color,res,1)==0xff123456);res.owner="com.android.systemui";
  check((int)call(color,res,999)==0xff123456);
  for(var m:module.hooks.keySet())if(m.getDeclaringClass().getSimpleName().equals("QSTileViewImpl")&&m.getName().startsWith("get")){
   Object tile=m.getDeclaringClass().getConstructor().newInstance();boolean two=m.getParameterCount()==2;
   int active=(int)(two?call(m,tile,2,false):call(m,tile,2));
   check(active==(m.getName().startsWith("getBackground")?ConfigManager.color:SystemUiThemeColors.foreground(ConfigManager.color)));
   check((int)(two?call(m,tile,1,false):call(m,tile,1))<20);
   if(two)check((int)call(m,tile,2,true)<20);
  }
  for(String kind:new String[]{"keyboard","dot_bg"}){
   int selected=kind.equals("keyboard")?android.R.attr.state_pressed:android.R.attr.state_selected;
   var original=new StateListDrawable(new int[][]{{selected},{}},new Drawable[]{new GradientDrawable(30),new GradientDrawable(40)});
   res.drawable=original;res.names.put(3,"drawable/zyb_keyguard_password_"+kind);
   Method load=Resources.class.getMethod("loadDrawable",android.util.TypedValue.class,int.class,int.class,Resources.Theme.class);
   var copy=(StateListDrawable)call(load,res,new android.util.TypedValue(),3,320,null);
   check(copy!=original&&fill(copy.children[0])==ConfigManager.color&&fill(copy.children[1])==40);
   check(fill(original.children[0])==30&&((GradientDrawable)copy.children[0]).radius==18);
   ConfigManager.monet=false;check(call(load,res,new android.util.TypedValue(),3,320,null)==original);ConfigManager.monet=true;
   original.fail=true;check(call(load,res,new android.util.TypedValue(),3,320,null)==original);original.fail=false;
   check(call(load,res,new android.util.TypedValue(),3,320,null)!=original);
  }
  var progress=new GradientDrawable(50);res.drawable=new LayerDrawable(new DrawableWrapper(progress));
  res.names.put(4,"drawable/zzz_brightness_progress_drawable");
  var brightness=(LayerDrawable)call(Resources.class.getMethod("getDrawable",int.class),res,4);
  check(fill(((DrawableWrapper)brightness.progress).getDrawable())==ConfigManager.color&&progress.color==50);
  var icon=new com.android.systemui.qs.tileimpl.QSIconViewImpl();var state=new com.android.systemui.qs.tileimpl.QSIconViewImpl.State();
  Method iconMethod=icon.getClass().getDeclaredMethod("setIcon",android.widget.ImageView.class,state.getClass(),boolean.class);
  Object[] args={new android.widget.ImageView(),state,true};
  var callback=module.hooks.get(iconMethod);
  check((int)callback.intercept(new XposedInterface.Chain(icon,args,p->call(color,res,2)))==SystemUiThemeColors.foreground(ConfigManager.color));
  state.disabledByPolicy=true;check((int)callback.intercept(new XposedInterface.Chain(icon,args,p->call(color,res,2)))==0xff123456);
  state.disabledByPolicy=false;
  try{callback.intercept(new XposedInterface.Chain(icon,args,p->{throw new IllegalStateException();}));}catch(IllegalStateException expected){}
  check((int)call(color,res,2)==0xff123456);
  var password=new com.android.keyguard.KeyguardPasswordViewZyb();var key=new android.widget.TextView();key.resources=password.resources;password.children.put(100,key);
  key.background=new StateListDrawable(new int[][]{{android.R.attr.state_pressed},{}},
    new Drawable[]{new GradientDrawable(30),new GradientDrawable(40)});
  call(password.getClass().getMethod("onFinishInflate"),password);
  check(key.colors.getColorForState(new int[]{1,2},0)==SystemUiThemeColors.foreground(ConfigManager.color));
  check(key.colors.getColorForState(new int[]{1},0)==0xffdddddd);
  check(fill(((StateListDrawable)key.background).children[0])==ConfigManager.color);
  check(!HookConfig.defaultEnabled(HookConfig.KEY_SYSTEMUI_MONET));
  ConfigManager.enabled=false;ConfigManager.monet=true;
  res.names.put(5,"bool/flag_monet");
  Method flag=Resources.class.getMethod("getBoolean",int.class);
  check((boolean)call(flag,res,5));res.owner="android";check(!(boolean)call(flag,res,5));res.owner="com.android.systemui";
  check(!(boolean)call(flag,res,999));
  check((int)call(color,res,1)==res.light);
  res.config.uiMode=Configuration.UI_MODE_NIGHT_YES;
  check((int)call(color,res,1)==res.dark);
  ConfigManager.enabled=true;check((int)call(color,res,1)==res.dark);ConfigManager.enabled=false;
  var nativeController=new com.android.systemui.theme.ThemeOverlayController();
  Method start=nativeController.getClass().getDeclaredMethod("start");
  call(start,nativeController);check(nativeController.startedWithMonet&&nativeController.mContext.callbacks.size()==1);
  var tile=new com.android.systemui.qs.tileimpl.QSTileViewImpl();
  Method stateChange=tile.getClass().getDeclaredMethod("handleStateChanged",Object.class);
  call(stateChange,tile,state);check(tile.refreshes==1);
  class DerivedTile extends com.android.systemui.qs.tileimpl.QSTileViewImpl {}
  var derivedTile=new DerivedTile();call(stateChange,derivedTile,state);
  var brightnessView=new com.android.systemui.settings.brightness.BrightnessSliderView();
  var slider=new android.widget.SeekBar();var rawProgress=new GradientDrawable(50);
  slider.progress=new LayerDrawable(new DrawableWrapper(rawProgress));brightnessView.children.put(200,slider);
  call(brightnessView.getClass().getMethod("onFinishInflate"),brightnessView);
  check(fill(((DrawableWrapper)((LayerDrawable)slider.progress).progress).getDrawable())==brightnessView.resources.light);
  call(password.getClass().getMethod("onFinishInflate"),password);
  brightnessView.resources.light=0xff934a24;password.resources.light=0xff934a24;res.dark=0xffe8bd95;
  nativeController.mContext.callbacks.get(0).onConfigurationChanged(new Configuration());
  check(tile.refreshes==2&&tile.icon.mState==-1);check(derivedTile.refreshes==2&&derivedTile.icon.mState==-1);
  check(fill(((DrawableWrapper)((LayerDrawable)slider.progress).progress).getDrawable())==0xff934a24&&rawProgress.color==50);
  check(fill(((StateListDrawable)key.background).children[0])==0xff934a24);
  check((int)call(color,res,1)==res.dark);
  var failedController=new com.android.systemui.theme.ThemeOverlayController();
  module.hooks.get(start).intercept(new XposedInterface.Chain(failedController,new Object[]{},p->{throw new SecurityException("OMS unavailable");}));
  check(!failedController.mIsMonetEnabled);
  ConfigManager.monet=false;check(!(boolean)call(flag,res,5));check((int)call(color,res,1)==0xff123456);
  var disabledController=new com.android.systemui.theme.ThemeOverlayController();call(start,disabledController);
  check(!disabledController.startedWithMonet&&disabledController.mContext.callbacks.isEmpty());
  disabledController.mIsMonetEnabled=true;call(start,disabledController);check(disabledController.startedWithMonet);
  ConfigManager.qs=true;ConfigManager.enabled=false;ConfigManager.monet=false;
  check((int)call(color,res,1)==res.dark);
  res.names.put(6,"color/qs_default_background");check((int)call(color,res,6)==0xff222222);
  res.owner="other";check((int)call(color,res,6)==0xff123456);res.owner="com.android.systemui";
  Method background=tile.getClass().getDeclaredMethod("getBackgroundColorForState",int.class,boolean.class);
  check((int)call(background,tile,1,false)==0xff222222);
  check((int)call(background,tile,2,false)==tile.resources.light);
  check((int)call(background,tile,2,true)==10);check((int)call(background,tile,0,false)==10);
  ConfigManager.enabled=true;check((int)call(background,tile,2,false)==tile.resources.light);ConfigManager.enabled=false;
  int previous=tile.refreshes;call(stateChange,tile,state);SystemUiThemeHooks.refreshDynamicColors();check(tile.refreshes==previous+2);
  ConfigManager.qs=false;check((int)call(color,res,6)==0xff123456);check((int)call(background,tile,1,false)==10);
  System.out.println("AOSP QS colors: active/inactive resources, removed manual override ignored, policy and configuration refresh passed");
  System.out.println("Monet: scoped flag enablement, native start, failure containment, light/dark palette, removed manual override ignored and existing-view refresh passed");
  System.out.println("SystemUI theme: RGB validation, 4096 contrast checks, 12/14 QS state variants, scoped icon color, selector cloning, XML load path and disabled fallback passed");
 }
 static double linear(int c){double x=c/255.;return x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
}
''',
})

stubs.update({
'android/content/res/Configuration.java': 'package android.content.res;public class Configuration {public int uiMode;public static final int UI_MODE_NIGHT_MASK=48,UI_MODE_NIGHT_YES=32;}',
'android/content/ComponentCallbacks.java': 'package android.content;public interface ComponentCallbacks {void onConfigurationChanged(android.content.res.Configuration c);void onLowMemory();}',
'android/content/Context.java': 'package android.content;public class Context {public java.util.List<ComponentCallbacks> callbacks=new java.util.ArrayList<>();public Context getApplicationContext(){return this;}public void registerComponentCallbacks(ComponentCallbacks c){callbacks.add(c);}}',
'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java': 'package android.os;public class Handler {public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}',
'android/widget/SeekBar.java': 'package android.widget;public class SeekBar extends android.view.View {public android.graphics.drawable.Drawable progress;public android.graphics.drawable.Drawable getProgressDrawable(){return progress;}public void setProgressDrawable(android.graphics.drawable.Drawable d){progress=d;}}',
'com/android/systemui/settings/brightness/BrightnessSliderView.java': 'package com.android.systemui.settings.brightness;public class BrightnessSliderView extends android.view.View {public void onFinishInflate(){}}',
'com/android/systemui/theme/ThemeOverlayController.java': 'package com.android.systemui.theme;public class ThemeOverlayController {public android.content.Context mContext=new android.content.Context();public boolean mIsMonetEnabled,startedWithMonet;public void start(){startedWithMonet=mIsMonetEnabled;}public void reevaluateSystemTheme(boolean force){}public void updateThemeOverlays(){}}',
})

with tempfile.TemporaryDirectory(prefix='arashi-systemui-theme-') as directory:
    temp = Path(directory)
    for name, source in stubs.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    sources = list(temp.rglob('*.java')) + [ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed' / name
                                          for name in ['HookConfig.java', 'Reflect.java',
                                                       'SystemUiThemeColors.java', 'hooks/SystemUiThemeHooks.java', 'hooks/SystemUiMonetHooks.java']]
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(p) for p in sources], check=True)
    subprocess.run(['java', '-cp', str(temp / 'classes'), 'com.lijinmu.arashi.xposed.hooks.Check'], check=True)
