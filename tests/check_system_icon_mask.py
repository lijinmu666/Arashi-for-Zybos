"""Exercise the real framework hooks, including native drawing and mask restoration."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
namespace = {'__file__': str(ROOT / 'tests/check_ios_icon.py')}
exec((ROOT / 'tests/check_ios_icon.py').read_text().split('with tempfile.TemporaryDirectory', 1)[0], namespace)
graphics = dict(namespace['STUBS'])
graphics.update({
    'RectF': '''public float left,top,right,bottom;public RectF(Rect r){this(r.left,r.top,r.right,r.bottom);}
 public RectF(RectF r){this(r.left,r.top,r.right,r.bottom);}
 public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}''',
    'Matrix': '''public enum ScaleToFit{FILL}public RectF target;
 public void setRectToRect(RectF a,RectF b,ScaleToFit f){target=new RectF(b);}''',
    'Path': '''public boolean ios;public int revision;public RectF box=new RectF(0,0,100,100);
 public Path(){}public Path(Path p){set(p);}public void set(Path p){ios=p.ios;box=new RectF(p.box);revision++;}
 public void moveTo(float x,float y){}public void lineTo(float x,float y){}
 public void cubicTo(float a,float b,float c,float d,float e,float f){ios=true;}
 public void close(){}public void transform(Matrix m){box=new RectF(m.target);revision++;}''',
    'Outline': 'public Path path;public void setPath(Path p){path=new Path(p);}',
    'Region': 'public boolean empty,ios;public int clears;public void setEmpty(){empty=true;clears++;}',
    'Canvas': namespace['STUBS']['Canvas'] + ' public Path lastPath;',
})
adaptive = '''package android.graphics.drawable;import android.graphics.*;
public class AdaptiveIconDrawable extends Drawable {
 private final Path mMask=new Path(),mMaskScaleOnly=new Path();private final Region mTransparentRegion=new Region();
 private Drawable bg,fg;public int nativeDraws;public static int api;
 public AdaptiveIconDrawable(Drawable b,Drawable f){bg=b;fg=f;}
 public Drawable getBackground(){return bg;}public Drawable getForeground(){return fg;}public static float getExtraInsetFraction(){return .25f;}
 private void updateMaskBoundsInternal(Rect b){mMask.ios=false;mMask.box=new RectF(b);mMask.revision++;
  mMaskScaleOnly.ios=false;mMaskScaleOnly.box=new RectF(0,0,b.width(),b.height());mMaskScaleOnly.revision++;mTransparentRegion.setEmpty();}
 public void draw(Canvas c){nativeDraws++;c.lastPath=new Path(mMaskScaleOnly);}
 public Path getIconMask(){return mMask;}public void getOutline(Outline o){o.setPath(mMask);}
 public Region getSafeZone(){Path p=api>=14?getIconMask():mMask;Region r=new Region();r.ios=p.ios;return r;}
 public Region getTransparentRegion(){if(mTransparentRegion.empty){mTransparentRegion.ios=mMask.ios;mTransparentRegion.empty=false;}return mTransparentRegion;}
}'''
module = '''package io.github.libxposed.api;import java.lang.reflect.*;import java.util.*;
public class XposedModule {
 public final Map<Method,Interceptor> hooks=new HashMap<>();
 public interface Chain {Object getThisObject();Object getArg(int i);Object proceed()throws Throwable;}
 public interface Interceptor {Object intercept(Chain c)throws Throwable;}
 public Hook hook(Method method){method.setAccessible(true);return new Hook(method);}
 public class Hook {private Method method;Hook(Method m){method=m;}public void intercept(Interceptor i){if(hooks.put(method,i)!=null)throw new AssertionError("duplicate hook");}}
}'''
check = '''package com.lijinmu.arashi.xposed.hooks;import java.lang.reflect.*;import android.graphics.*;
import android.graphics.drawable.*;import com.lijinmu.arashi.xposed.*;import io.github.libxposed.api.XposedModule;
public class MaskCheck {
 static XposedModule module=new XposedModule();static void check(boolean b){if(!b)throw new AssertionError();}
 static class Layer extends Drawable {public void draw(Canvas c){}}
 static Object call(AdaptiveIconDrawable icon,String name,Class<?>[] types,Object... args)throws Throwable {
  Method method=AdaptiveIconDrawable.class.getDeclaredMethod(name,types);method.setAccessible(true);
  XposedModule.Chain chain=new XposedModule.Chain(){public Object getThisObject(){return icon;}public Object getArg(int i){return args[i];}
   public Object proceed()throws Throwable {try{return method.invoke(icon,args);}catch(InvocationTargetException e){throw e.getCause();}}};
  var hook=module.hooks.get(method);return hook==null?chain.proceed():hook.intercept(chain);
 }
 static Path mask(AdaptiveIconDrawable icon)throws Throwable{return (Path)call(icon,"getIconMask",new Class<?>[0]);}
 static void box(Path p,float l,float t,float r,float b){check(p.box.left==l&&p.box.top==t&&p.box.right==r&&p.box.bottom==b);}
 static void bounds(AdaptiveIconDrawable icon,int w,int h)throws Throwable {
  Rect b=new Rect(13,17,13+w,17+h);icon.setBounds(b);call(icon,"updateMaskBoundsInternal",new Class<?>[]{Rect.class},b);
 }
 static void draw(AdaptiveIconDrawable icon,Canvas c)throws Throwable{call(icon,"draw",new Class<?>[]{Canvas.class},c);}
 static void run(int api)throws Throwable {
  AdaptiveIconDrawable.api=api;ConfigManager.enabled=false;Layer bg=new Layer(),fg=new Layer();
  bg.setBounds(new Rect(1,2,3,4));fg.setBounds(new Rect(5,6,7,8));AdaptiveIconDrawable icon=new AdaptiveIconDrawable(bg,fg);
  check(!mask(icon).ios);ConfigManager.enabled=true;check(mask(icon).ios);box(mask(icon),0,0,100,100);
  ConfigManager.enabled=false;check(!mask(icon).ios);box(mask(icon),0,0,100,100);
  for(int[] size:new int[][]{{72,72},{31,45},{192,192}}){
   ConfigManager.enabled=true;bounds(icon,size[0],size[1]);Path path=mask(icon);check(path.ios);box(path,13,17,13+size[0],17+size[1]);
   Canvas canvas=new Canvas();int count=icon.nativeDraws;draw(icon,canvas);check(icon.nativeDraws==count+1&&canvas.lastPath.ios);
   box(canvas.lastPath,0,0,size[0],size[1]);int revision=path.revision;draw(icon,canvas);check(path.revision==revision);
   Outline outline=new Outline();call(icon,"getOutline",new Class<?>[]{Outline.class},outline);check(outline.path.ios);box(outline.path,13,17,13+size[0],17+size[1]);
   check(((Region)call(icon,"getSafeZone",new Class<?>[0])).ios);
   Region region=(Region)call(icon,"getTransparentRegion",new Class<?>[0]);check(region.ios);
   ConfigManager.enabled=false;draw(icon,canvas);check(!canvas.lastPath.ios);box(canvas.lastPath,0,0,size[0],size[1]);
   check(!mask(icon).ios);box(mask(icon),13,17,13+size[0],17+size[1]);
   check(!((Region)call(icon,"getTransparentRegion",new Class<?>[0])).ios);
   check(bg.getBounds().left==1&&bg.getBounds().right==3&&fg.getBounds().left==5&&fg.getBounds().right==7);
  }
  // Resize while disabling: the new Android-generated original must replace any saved old shape.
  ConfigManager.enabled=true;bounds(icon,72,72);ConfigManager.enabled=false;bounds(icon,48,56);
  check(!mask(icon).ios);box(mask(icon),13,17,61,73);
 }
 public static void main(String[] args)throws Throwable {
  check(!HookConfig.defaultEnabled(HookConfig.KEY_IOS_SYSTEM_ICON_MASK));
  check(java.util.Collections.frequency(java.util.Arrays.asList(HookConfig.keys()),HookConfig.KEY_IOS_SYSTEM_ICON_MASK)==1);
  SystemIconMaskHooks.hook(module);check(module.hooks.size()==6);SystemIconMaskHooks.hook(module);check(module.hooks.size()==6);
  run(12);run(14);System.out.println("System mask draw/outline/region, shared geometry, resize and restore checks passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='arashi-system-mask-') as directory:
    temp = Path(directory)
    stubs = {f'android/graphics/{name}.java': f'package android.graphics;public class {name} {{{body}}}'
             for name, body in graphics.items()}
    stubs.update({
        'android/graphics/drawable/Drawable.java': namespace['DRAWABLE'],
        'android/graphics/drawable/AdaptiveIconDrawable.java': adaptive,
        'android/util/Log.java': 'package android.util;public class Log {public static int i(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}}',
        'com/lijinmu/arashi/xposed/ConfigManager.java': 'package com.lijinmu.arashi.xposed;public class ConfigManager {public static boolean enabled;public static boolean isEnabledCached(String k){return enabled;}}',
        'com/lijinmu/arashi/logs/HookLogBridge.java': 'package com.lijinmu.arashi.logs;public class HookLogBridge {public static void error(String message,Throwable e){}}',
        'io/github/libxposed/api/XposedModule.java': module,
        'com/lijinmu/arashi/xposed/hooks/MaskCheck.java': check,
    })
    for name, content in stubs.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    sources = list(temp.rglob('*.java')) + [ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed' / path
        for path in ['HookConfig.java', 'hooks/IosIconDrawable.java', 'hooks/SystemIconMaskHooks.java']]
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(path) for path in sources], check=True)
    subprocess.run(['java', '-cp', str(temp / 'classes'), 'com.lijinmu.arashi.xposed.hooks.MaskCheck'], check=True)
