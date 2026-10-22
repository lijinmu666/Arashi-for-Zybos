"""Run actual Bluetooth hooks/drawable; AOSP fixture is identical on Android 12/14.
Host checks validate selection and draw/tint operations, not device pixel rendering.
"""
from pathlib import Path
import ast, re, shutil, subprocess, tempfile, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
node=next(n.value for n in ast.parse((ROOT/'tests/check_navigation_access.py').read_text()).body
          if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='STUBS' for t in n.targets))
base=ast.literal_eval(node)
stubs={k:base[k] for k in ['io/github/libxposed/api/XposedInterface.java','io/github/libxposed/api/XposedModule.java']}
# Check the drawable data against the original upstream vector, not a hand-drawn approximation.
xml=ET.parse(ROOT/'tests/fixtures/aosp_bluetooth_connected.xml').getroot()
paths=[p.attrib['{http://schemas.android.com/apk/res/android}pathData'] for p in xml.findall('path')]
source=(ROOT/'app/src/main/java/com/lijinmu/arashi/xposed/hooks/AospBluetoothDrawable.java').read_text()
assert re.findall(r'createPathFromPathData\("([^"]+)"\)',source)==paths
assert xml.attrib['{http://schemas.android.com/apk/res/android}width']=='17dp'
stubs.update({
'android/util/Log.java':'package android.util;public class Log {public static int i(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}}',
'android/util/DisplayMetrics.java':'package android.util;public class DisplayMetrics {public float density=2;}',
'android/content/res/Resources.java':'''package android.content.res;import android.graphics.drawable.Drawable;
public class Resources {
 public boolean fail;public int original;public String prefix="com.android.systemui";
 public static class Theme {}public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}
 public String getResourceName(int id){if(fail)throw new IllegalArgumentException();return prefix+":drawable/"+switch(id){case 12,14->"ic_bluetooth_connected_zyb";case 22->"ic_qs_bluetooth_icon";default->"ic_bluetooth";};}
 public Drawable getDrawable(int id){original++;return null;}public Drawable getDrawable(int id,Theme t){original++;return null;}
 public Drawable getDrawableForDensity(int id,int dpi){original++;return null;}
 public Drawable getDrawableForDensity(int id,int dpi,Theme t){original++;return null;}
}''',
'android/content/res/ColorStateList.java':'''package android.content.res;public class ColorStateList {
 final int color;public ColorStateList(int c){color=c;}public static ColorStateList valueOf(int c){return new ColorStateList(c);}
 public boolean isStateful(){return true;}public int getDefaultColor(){return color;}public int getColorForState(int[] state,int d){return state.length>0?color+1:color;}}
''',
'android/graphics/Rect.java':'package android.graphics;public class Rect {public int left,top,right,bottom;public int width(){return right-left;}public int height(){return bottom-top;}}',
'android/graphics/Path.java':'package android.graphics;public class Path {public String data;public Path(String s){data=s;}}',
'android/graphics/Paint.java':'''package android.graphics;public class Paint {public static int ANTI_ALIAS_FLAG=1;public int alpha=255;public ColorFilter filter;
 public Paint(int flags){}public void setColor(int c){}public void setAlpha(int a){alpha=a;}public int getAlpha(){return alpha;}public void setColorFilter(ColorFilter f){filter=f;}}''',
'android/graphics/Canvas.java':'''package android.graphics;public class Canvas {public int depth,draws;public float x,y,sx,sy;public boolean fail;
 public int save(){return ++depth;}public void translate(float x,float y){this.x=x;this.y=y;}public void scale(float x,float y){sx=x;sy=y;}
 public void drawPath(Path p,Paint paint){if(fail)throw new IllegalArgumentException();draws++;}public void restoreToCount(int d){depth=d-1;}}''',
'android/graphics/ColorFilter.java':'package android.graphics;public class ColorFilter {}',
'android/graphics/PorterDuff.java':'package android.graphics;public class PorterDuff {public enum Mode {SRC_IN,MULTIPLY}}',
'android/graphics/PorterDuffColorFilter.java':'package android.graphics;public class PorterDuffColorFilter extends ColorFilter {public int color;public PorterDuff.Mode mode;public PorterDuffColorFilter(int c,PorterDuff.Mode m){color=c;mode=m;}}',
'android/graphics/PixelFormat.java':'package android.graphics;public class PixelFormat {public static int TRANSLUCENT=-3;}',
'android/graphics/drawable/Drawable.java':'''package android.graphics.drawable;import android.graphics.*;import android.content.res.ColorStateList;
public abstract class Drawable {
 private Rect bounds=new Rect();private int[] state={};public Rect getBounds(){return bounds;}public void setBounds(int l,int t,int r,int b){bounds.left=l;bounds.top=t;bounds.right=r;bounds.bottom=b;}
 public abstract void draw(Canvas c);public abstract void setAlpha(int a);public int getAlpha(){return 255;}public abstract void setColorFilter(ColorFilter f);public abstract int getOpacity();
 public int getIntrinsicWidth(){return 0;}public int getIntrinsicHeight(){return 0;}public void invalidateSelf(){}
 public void setTintList(ColorStateList c){}public void setTintMode(PorterDuff.Mode m){}public boolean isStateful(){return false;}
 public int[] getState(){return state;}public void setState(int[] s){state=s;onStateChange(s);}protected boolean onStateChange(int[] s){return false;}
}''',
'androidx/core/graphics/PathParser.java':'package androidx.core.graphics;public class PathParser {public static android.graphics.Path createPathFromPathData(String s){return new android.graphics.Path(s);}}',
'com/lijinmu/arashi/xposed/ConfigManager.java':'''package com.lijinmu.arashi.xposed;public class ConfigManager {
 public static boolean enabled;public static boolean isEnabledCached(String k){if(!k.equals(HookConfig.KEY_AOSP_STATUS_ICONS))throw new AssertionError();return enabled;}}''',
'Check.java':'''package com.lijinmu.arashi.xposed.hooks;
import java.lang.reflect.*;import android.content.res.*;import android.graphics.*;import com.lijinmu.arashi.xposed.*;import io.github.libxposed.api.*;
public class Check {
 static XposedModule module=new XposedModule();static void check(boolean b){if(!b)throw new AssertionError();}
 static Object call(Method m,Object self,Object...args)throws Throwable{return module.hooks.get(m).intercept(new XposedInterface.Chain(self,args,a->m.invoke(self,a)));}
 static Paint paint(Object d)throws Exception {var f=AospBluetoothDrawable.class.getDeclaredField("paint");f.setAccessible(true);return (Paint)f.get(d);}
 public static void main(String[] args)throws Throwable {
  check(!HookConfig.defaultEnabled(HookConfig.KEY_AOSP_STATUS_ICONS));check(java.util.Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_AOSP_STATUS_ICONS));
  for(int mask=0;mask<8;mask++){
   var values=new java.util.HashMap<String,Object>();
   values.put(HookConfig.KEY_AOSP_BLUETOOTH,(mask&1)!=0);values.put(HookConfig.KEY_AOSP_WIFI,(mask&2)!=0);values.put(HookConfig.KEY_AOSP_BATTERY,(mask&4)!=0);
   check(HookConfig.statusBarIconsEnabled(values)==(mask!=0));
   values.put(HookConfig.KEY_AOSP_STATUS_ICONS,false);check(!HookConfig.statusBarIconsEnabled(values));
   values.put(HookConfig.KEY_AOSP_STATUS_ICONS,true);check(HookConfig.statusBarIconsEnabled(values));
  }
  check(!HookConfig.statusBarIconsEnabled(java.util.Collections.emptyMap()));
  for(String old:new String[]{HookConfig.KEY_AOSP_BLUETOOTH,HookConfig.KEY_AOSP_WIFI,HookConfig.KEY_AOSP_BATTERY})
   check(!java.util.Arrays.asList(HookConfig.keys()).contains(old));
  AospBluetoothHooks.hookSystemUI(module);check(module.hooks.size()==4);
  for(Method method:module.hooks.keySet()){
   Object[] params=method.getParameterCount()==1?new Object[]{12}:method.getName().equals("getDrawable")?new Object[]{12,null}:method.getParameterCount()==2?new Object[]{12,320}:new Object[]{12,320,null};
   Resources res=new Resources();ConfigManager.enabled=false;check(call(method,res,params)==null&&res.original==1);
   ConfigManager.enabled=true;
   for(int version:new int[]{12,14}){
    params[0]=version;var a=(AospBluetoothDrawable)call(method,res,params);var b=(AospBluetoothDrawable)call(method,res,params);
    check(a!=b&&a.getIntrinsicWidth()==34&&a.getIntrinsicHeight()==34);
    a.setAlpha(83);check(a.getAlpha()==83&&b.getAlpha()==255);
    a.setTintList(ColorStateList.valueOf(10));a.setState(new int[]{1});check(((PorterDuffColorFilter)paint(a).filter).color==11);
    check(((PorterDuffColorFilter)paint(b).filter).color==0xffffffff);
    var explicit=new ColorFilter();a.setColorFilter(explicit);a.setTintList(ColorStateList.valueOf(20));check(paint(a).filter==explicit);
    a.setColorFilter(null);a.setTintMode(PorterDuff.Mode.MULTIPLY);check(((PorterDuffColorFilter)paint(a).filter).mode==PorterDuff.Mode.MULTIPLY);
    Canvas c=new Canvas();a.setBounds(5,7,53,31);a.draw(c);check(c.draws==3&&c.x==5&&c.y==7&&c.sx==2&&c.sy==1&&c.depth==0);
    c.fail=true;try{a.draw(c);throw new AssertionError();}catch(IllegalArgumentException expected){}check(c.depth==0);
   }
   params[0]=22;check(call(method,res,params)==null); // Quick Settings
   params[0]=23;check(call(method,res,params)==null); // unconnected/other icon
   params[0]=12;res.prefix="other.app";check(call(method,res,params)==null); // same ID/name in another package
   res.prefix="com.android.systemui";res.fail=true;check(call(method,res,params)==null);res.fail=false;
   check(call(method,res,params) instanceof AospBluetoothDrawable); // reentry guard cleared after failure
   ConfigManager.enabled=false;check(call(method,res,params)==null);
  }
  ConfigManager.enabled=true;var density=Resources.class.getMethod("getDrawableForDensity",int.class,int.class);
  check(((AospBluetoothDrawable)call(density,new Resources(),14,480)).getIntrinsicWidth()==51);
  check(((AospBluetoothDrawable)call(density,new Resources(),14,0)).getIntrinsicWidth()==34);
  System.out.println("Bluetooth: 12/14 resource selection, off/unrelated/foreign/failure fallback, density, AOSP paths, tint, alpha and bounds passed");
 }
}'''
})
with tempfile.TemporaryDirectory(prefix='arashi-bluetooth-') as directory:
 temp=Path(directory)
 for name,source in stubs.items():
  p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
 sources=list(temp.rglob('*.java'))+[ROOT/'app/src/main/java/com/lijinmu/arashi/xposed'/n for n in ['HookConfig.java','hooks/AospBluetoothHooks.java','hooks/AospBluetoothDrawable.java']]
 compiler=[shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
 subprocess.run(compiler+['-d',str(temp/'classes')]+[str(p) for p in sources],check=True)
 subprocess.run(['java','-cp',str(temp/'classes'),'com.lijinmu.arashi.xposed.hooks.Check'],check=True)
