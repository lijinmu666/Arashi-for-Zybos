"""Host regression checks for real icon draw calls; not a pixel/rendering test."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'Rect': '''public int left,top,right,bottom;
 public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public Rect(Rect r){this(r.left,r.top,r.right,r.bottom);}
 public int width(){return right-left;} public int height(){return bottom-top;}
 public boolean isEmpty(){return width()<=0||height()<=0;}''',
    'RectF': '''public RectF(Rect r){} public RectF(float l,float t,float r,float b){}''',
    'Path': '''public Path(){} public Path(Path p){} public void moveTo(float x,float y){}
 public void lineTo(float x,float y){} public void cubicTo(float a,float b,float c,float d,float e,float f){}
 public void close(){} public void transform(Matrix m){}''',
    'Matrix': '''public enum ScaleToFit{FILL} public void setRectToRect(RectF a,RectF b,ScaleToFit f){}''',
    'Paint': '''public static final int ANTI_ALIAS_FLAG=1; public Paint(int f){}
 public void setAlpha(int a){} public void setColorFilter(ColorFilter f){}''',
    'ColorFilter': '',
    'PixelFormat': 'public static final int TRANSLUCENT=-3;',
    'Canvas': '''public int depth,clips; public float tx,ty;
 public int saveLayer(RectF r,Paint p){return ++depth;}
 public void translate(float x,float y){tx+=x;ty+=y;}
 public void clipPath(Path p){clips++;} public void restoreToCount(int n){depth=n-1;}''',
}
DRAWABLE = '''package android.graphics.drawable;
import android.graphics.*;
public abstract class Drawable {
 private Rect bounds=new Rect(0,0,0,0);
 public interface Callback {void invalidateDrawable(Drawable d);
 void scheduleDrawable(Drawable d,Runnable r,long t);void unscheduleDrawable(Drawable d,Runnable r);}
 public Rect getBounds(){return bounds;} public void setBounds(Rect r){bounds=new Rect(r);}
 public abstract void draw(Canvas c); public void setCallback(Callback c){}
 public int getIntrinsicWidth(){return 108;} public int getIntrinsicHeight(){return 108;}
 public void setAlpha(int a){} public void setColorFilter(ColorFilter f){} public int getOpacity(){return 0;}
 public void invalidateSelf(){} public void scheduleSelf(Runnable r,long t){} public void unscheduleSelf(Runnable r){}
}'''
ADAPTIVE = '''package android.graphics.drawable;
import android.graphics.*;
public class AdaptiveIconDrawable extends Drawable {
 private final Drawable bg,fg;
 public AdaptiveIconDrawable(Drawable b,Drawable f){bg=b;fg=f;}
 public Drawable getBackground(){return bg;} public Drawable getForeground(){return fg;}
 public static float getExtraInsetFraction(){return .25f;}
 public void draw(Canvas c){throw new AssertionError("Old system mask must not be drawn");}
}'''
CHECK = '''package com.lijinmu.arashi.xposed.hooks;
import android.graphics.*;
import android.graphics.drawable.*;
public class IconCheck {
 static class Layer extends Drawable {
  Rect seen; boolean fail;
  public void draw(Canvas c){seen=new Rect(getBounds());if(fail)throw new IllegalStateException();}
 }
 static void rect(Rect r,int l,int t,int right,int bottom){
  if(r==null||r.left!=l||r.top!=t||r.right!=right||r.bottom!=bottom)throw new AssertionError("Layer bounds");
 }
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void adaptive(int w,int h,int l,int t,int r,int b){
  Layer bg=new Layer(),fg=new Layer();
  bg.setBounds(new Rect(1,2,33,44)); fg.setBounds(new Rect(5,6,77,88));
  IosIconDrawable icon=new IosIconDrawable(new AdaptiveIconDrawable(bg,fg),null);
  icon.setBounds(new Rect(13,17,13+w,17+h)); Canvas c=new Canvas();icon.draw(c);
  rect(bg.seen,l,t,r,b);rect(fg.seen,l,t,r,b);
  rect(bg.getBounds(),1,2,33,44);rect(fg.getBounds(),5,6,77,88);
  check(c.depth==0&&c.clips==1&&c.tx==13&&c.ty==17);
 }
 public static void main(String[] args){
  // Android's 108px adaptive canvas has a 72px viewport. A graphic
  // placed in either layer must occupy exactly the same visible area.
  adaptive(72,72,-18,-18,90,90);
  adaptive(192,192,-48,-48,240,240);
  // Small folder thumbnails and odd/non-square bounds must retain pixel precision.
  adaptive(31,45,-8,-11,38,55);
  Layer legacy=new Layer(); legacy.setBounds(new Rect(1,2,3,4));
  IosIconDrawable icon=new IosIconDrawable(legacy,null);
  icon.setBounds(new Rect(0,0,192,192));icon.draw(new Canvas());
  rect(legacy.seen,0,0,192,192);rect(legacy.getBounds(),1,2,3,4);
  check(icon.original()==legacy);
  Layer fg=new Layer();fg.setBounds(new Rect(1,2,3,4));fg.fail=true;
  icon=new IosIconDrawable(new AdaptiveIconDrawable(null,fg),null);
  icon.setBounds(new Rect(0,0,72,72));Canvas c=new Canvas();
  try{icon.draw(c);throw new AssertionError();}catch(IllegalStateException expected){}
  rect(fg.getBounds(),1,2,3,4);check(c.depth==0);
  fg.fail=false;icon.draw(new Canvas());rect(fg.seen,-18,-18,90,90);
  System.out.println("iOS icon layer geometry checks passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='arashi-icons-') as directory:
    tmp = Path(directory)
    sources = {f'android/graphics/{name}.java': f'package android.graphics; public class {name} {{{body}}}'
               for name, body in STUBS.items()}
    sources.update({'android/graphics/drawable/Drawable.java': DRAWABLE,
                    'android/graphics/drawable/AdaptiveIconDrawable.java': ADAPTIVE,
                    'com/lijinmu/arashi/xposed/hooks/IconCheck.java': CHECK})
    for name, source in sources.items():
        target = tmp / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source)
    real = ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed/hooks/IosIconDrawable.java'
    subprocess.run(['javac', '-d', str(tmp / 'classes'), *map(str, tmp.rglob('*.java')), str(real)], check=True)
    subprocess.run(['java', '-cp', str(tmp / 'classes'), 'com.lijinmu.arashi.xposed.hooks.IconCheck'], check=True)
