"""Run real QS restore hooks against Android 12/14 vendor-shaped host fixtures.

This checks routing/bookkeeping/hierarchy and disabled behavior, not device rendering.
"""
from pathlib import Path
import ast
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
tree = ast.parse((ROOT / 'tests/check_navigation_access.py').read_text())
base = ast.literal_eval(next(n.value for n in tree.body if isinstance(n, ast.Assign)
                            and any(isinstance(t, ast.Name) and t.id == 'STUBS' for t in n.targets)))
stubs = {k: base[k] for k in ['android/util/Log.java', 'io/github/libxposed/api/XposedInterface.java']}
stubs.update({
'io/github/libxposed/api/XposedModule.java': '''package io.github.libxposed.api;import java.lang.reflect.*;import java.util.*;
public class XposedModule {public Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();
 public class Builder {Executable m;Builder(Executable x){m=x;}public void intercept(XposedInterface.Hooker h){hooks.put(m,h);}}
 public Builder hook(Executable m){return new Builder(m);}public boolean deoptimize(Executable m){return true;}}
''',
'android/R.java': '''package android;public class R {public static class id {public static int mask=999;}
 public static class color {public static final int system_neutral1_50=50,system_neutral1_100=100,
 system_neutral1_800=800,system_neutral1_900=900;}}
''',
'android/util/DisplayMetrics.java': 'package android.util;public class DisplayMetrics {public float density=2;}',
'android/content/res/Configuration.java': '''package android.content.res;public class Configuration {
 public static final int UI_MODE_NIGHT_MASK=48,UI_MODE_NIGHT_YES=32;public int uiMode,screenWidthDp;}
''',
'android/content/res/Resources.java': '''package android.content.res;import java.util.*;
public class Resources {public static class NotFoundException extends RuntimeException {}public static class Theme {}
 public String owner="com.android.systemui";public Map<Integer,String> names=new HashMap<>();
 public Configuration config=new Configuration();public android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
 public Configuration getConfiguration(){return config;}public android.util.DisplayMetrics getDisplayMetrics(){return metrics;}
 public String getResourceName(int id){if(!names.containsKey(id))throw new NotFoundException();return owner+":"+names.get(id);}
 public int getIdentifier(String name,String kind,String pkg){return name.hashCode();}
 public boolean getBoolean(int id){return false;}public int getInteger(int id){return 17;}public float getDimension(int id){return 17.5f;}
 public int getDimensionPixelSize(int id){return 17;}public int getDimensionPixelOffset(int id){return 17;}
 public int getColor(int id,Theme theme){return id;}}
''',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;import android.content.res.Resources;
public class Drawable {public Drawable mutate(){return this;}public ConstantState getConstantState(){return null;}
 public static abstract class ConstantState {public abstract Drawable newDrawable(Resources r);}}
''',
'android/graphics/drawable/DrawableWrapper.java': '''package android.graphics.drawable;public class DrawableWrapper extends Drawable {
 public Drawable child;public Drawable getDrawable(){return child;}}
''',
'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable;public class GradientDrawable extends Drawable {
 public int radius;public void setCornerRadius(float r){radius=(int)r;}}
''',
'android/graphics/drawable/RippleDrawable.java': '''package android.graphics.drawable;import android.content.res.Resources;
public class RippleDrawable extends Drawable {public GradientDrawable fill=new GradientDrawable(),mask=new GradientDrawable();
 public Drawable findDrawableByLayerId(int id){return id==android.R.id.mask?mask:fill;}
 public ConstantState getConstantState(){return new ConstantState(){public Drawable newDrawable(Resources r){return new RippleDrawable();}};}}
''',
'android/view/Gravity.java': '''package android.view;public class Gravity {public static final int START=8388611,CENTER_VERTICAL=16;}
''',
'android/view/View.java': '''package android.view;import android.content.res.Resources;
public class View {public static class MeasureSpec {public static final int EXACTLY=1073741824;
 public static int makeMeasureSpec(int size,int mode){return size|mode;}}
 public static final int VISIBLE=0,INVISIBLE=4,GONE=8,NO_ID=-1;public int id=NO_ID,visibility,ps,pt,pe,pb,minHeight;
 public Resources resources=new Resources();public ViewGroup parent;public ViewGroup.LayoutParams params=new ViewGroup.LayoutParams(-1,-2);
 public android.graphics.drawable.Drawable background;public Resources getResources(){return resources;}
 public void setBackground(android.graphics.drawable.Drawable d){background=d;}public boolean isClickable(){return true;}
 public void setId(int id){this.id=id;}public int getId(){return id;}public ViewGroup getParent(){return parent;}
 public View findViewById(int id){return this.id==id?this:null;}
 public void setVisibility(int v){visibility=v;}public int getVisibility(){return visibility;}
 public ViewGroup.LayoutParams getLayoutParams(){return params;}public void setLayoutParams(ViewGroup.LayoutParams p){params=p;}
 public int getPaddingStart(){return ps;}public int getPaddingTop(){return pt;}public int getPaddingEnd(){return pe;}
 public void setPaddingRelative(int a,int b,int c,int d){ps=a;pt=b;pe=c;pb=d;}public void setMinimumHeight(int h){minHeight=h;}
 public boolean post(Runnable r){r.run();return true;}}
''',
'android/view/ViewGroup.java': '''package android.view;import java.util.*;
public class ViewGroup extends View {public java.util.List<View> children=new ArrayList<>();
 public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
 public void addView(View v){if(v.parent!=null)throw new IllegalStateException("double parent");children.add(v);v.parent=this;}
 public void addView(View v,LayoutParams p){v.setLayoutParams(p);addView(v);}
 public void addView(View v,int index,LayoutParams p){v.setLayoutParams(p);if(v.parent!=null)throw new IllegalStateException();children.add(index,v);v.parent=this;}
 public int indexOfChild(View v){return children.indexOf(v);}
 public void removeView(View v){children.remove(v);v.parent=null;}public int getChildCount(){return children.size();}
 public View getChildAt(int i){return children.get(i);}
 public View findViewById(int id){if(this.id==id)return this;for(View v:children){View r=v.findViewById(id);if(r!=null)return r;}return null;}}
''',
'android/widget/LinearLayout.java': '''package android.widget;public class LinearLayout extends android.view.ViewGroup {
 public static final int HORIZONTAL=0;public int orientation=1,gravity;public void setOrientation(int v){orientation=v;}public void setGravity(int v){gravity=v;}
 public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public float weight;public int start;
 public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,float x){super(w,h);weight=x;}
 public void setMarginStart(int x){start=x;}}}
''',
'android/widget/FrameLayout.java': '''package android.widget;public class FrameLayout extends android.view.ViewGroup {
 public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public int topMargin;
 public LayoutParams(int w,int h){super(w,h);}}}
''',
'android/widget/RelativeLayout.java': '''package android.widget;public class RelativeLayout extends android.view.ViewGroup {
 public static final int BELOW=3;public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public int topMargin,below;
 public LayoutParams(int w,int h){super(w,h);}public void addRule(int rule,int value){below=value;}}}
''',
'android/widget/TextView.java': '''package android.widget;public class TextView extends android.view.View {
 public float size;public int gravity;public void setTextSize(float s){size=s;}public void setGravity(int v){gravity=v;}}
''',
'com/lijinmu/arashi/xposed/ConfigManager.java': '''package com.lijinmu.arashi.xposed;public class ConfigManager {
 public static boolean enabled;public static boolean isEnabledCached(String key){return enabled;}}
''',
'com/lijinmu/arashi/xposed/hooks/SystemUiThemeHooks.java': '''package com.lijinmu.arashi.xposed.hooks;
public class SystemUiThemeHooks {public static int refreshes;static void refreshDynamicColors(){refreshes++;}}
''',
'com/lijinmu/arashi/xposed/hooks/SystemUiMonetHooks.java': '''package com.lijinmu.arashi.xposed.hooks;
public class SystemUiMonetHooks {static boolean setField(Object o,String n,Object v){try{SystemUiQsExpansion.set(o,n,v);return true;}catch(Exception e){return false;}}}
''',
'com/android/systemui/shade/QuickSettingsController.java': '''package com.android.systemui.shade;
public class QuickSettingsController {public int mMinExpansionHeight=100,mMaxExpansionHeight=500,updates,original,expandedChanges;
 public float mExpansionHeight=100;public boolean mFullyExpanded,mExpanded,mAnimatorExpand,mAnimating,mStackScrollerOverscrolling,mDozing;
 public Panel panel=new Panel();public Listener mExpansionHeightListener=new Listener(panel);
 public static class Panel {public int callbacks;public boolean full;public void onQsSetExpansionHeightCalled(boolean b){callbacks++;full=b;}}
 public static class Listener {public Panel f$0;public Listener(Panel p){f$0=p;}}
 public void setExpanded(boolean b){mExpanded=b;expandedChanges++;}public void updateExpansion(){updates++;}
 public void setExpansionHeight(float value){original++;mExpansionHeight=mMinExpansionHeight;}}
''',
'com/android/systemui/statusbar/phone/NotificationPanelViewController.java': '''package com.android.systemui.statusbar.phone;import java.util.*;
public class NotificationPanelViewController {public int mQsMinExpansionHeight=100,mQsMaxExpansionHeight=500,updates,original,mBarState,layout;
 public float mQsExpansionHeight=100;public boolean mQsFullyExpanded,mQsExpanded,mStackScrollerOverscrolling,mDozing;
 public A11y mAccessibilityManager=new A11y();public TitleView mView=new TitleView();public Falsing mFalsingManager=new Falsing();
 public Collector mFalsingCollector=new Collector();public StatusBar mStatusBar=new StatusBar();
 public List<ExpansionListener> mExpansionListeners=new ArrayList<>();
 public static class A11y {public boolean isEnabled(){return true;}}
 public static class TitleView {public CharSequence title;public void setAccessibilityPaneTitle(CharSequence s){title=s;}}
 public static class Falsing {public boolean disabled;public boolean isUnlockingDisabled(){return disabled;}}
 public static class Collector {public boolean enforce;public boolean shouldEnforceBouncer(){return enforce;}}
 public static class StatusBar {public int bouncer;public void executeRunnableDismissingKeyguard(Runnable a,Runnable b,boolean c,boolean d,boolean e){if(d)bouncer++;}}
 public static class ExpansionListener {public float value;public void onQsExpansionChanged(float f){value=f;}}
 public void setQsExpanded(boolean b){mQsExpanded=b;}public void updateQsExpansion(){updates++;}
 public void requestScrollerTopPaddingUpdate(boolean b){layout++;}public void updateHeaderKeyguardAlpha(){}
 public void updateKeyguardBottomAreaAlpha(){layout++;}public void positionClockAndNotifications(){layout++;}
 public void updateBigClockAlpha(){layout++;}public String determineAccessibilityPaneTitle(){return "QS";}
 public void setQsExpansion(float f){original++;mQsExpansionHeight=mQsMinExpansionHeight;}}
''',
 'com/android/systemui/shade/carrier/ShadeCarrierGroupController.java': '''package com.android.systemui.shade.carrier;
public class ShadeCarrierGroupController {public boolean mListening,mIsSingleCarrier;public int updates;
 public Object mOnSingleCarrierChangedListener;public Handler mBgHandler=new Handler();
 public static class Handler {public boolean post(Runnable r){r.run();return true;}}}''',
'com/android/systemui/shade/carrier/ShadeCarrierGroupController$$ExternalSyntheticLambda2.java': '''package com.android.systemui.shade.carrier;
public class ShadeCarrierGroupController$$ExternalSyntheticLambda2 implements Runnable {ShadeCarrierGroupController c;
 public ShadeCarrierGroupController$$ExternalSyntheticLambda2(ShadeCarrierGroupController c,int i){this.c=c;}
 public void run(){c.updates++;}}''',
'com/android/systemui/shade/ShadeHeaderController$updateListeners$1.java': '''package com.android.systemui.shade;
public class ShadeHeaderController$updateListeners$1 {public ShadeHeaderController$updateListeners$1(ShadeHeaderController c){}}''',
'com/android/systemui/shade/ShadeHeaderController.java': '''package com.android.systemui.shade;import android.view.*;
public class ShadeHeaderController {public View header=new View();public boolean qsDisabled,qsVisible,customizing,visible;public int original,singleUpdates;
 public com.android.systemui.shade.carrier.ShadeCarrierGroupController mShadeCarrierGroupController=new com.android.systemui.shade.carrier.ShadeCarrierGroupController();
 public void updateSingleCarrier(boolean b){singleUpdates++;}public void updateVisibility(){original++;header.setVisibility(View.GONE);}}
''',
 'com/android/systemui/util/LargeScreenUtils.java': '''package com.android.systemui.util;
public class LargeScreenUtils {public static boolean shouldUseSplitNotificationShade(android.content.res.Resources r){return false;}}''',
 'com/android/systemui/qs/QSPanelControllerBase.java': '''package com.android.systemui.qs;
public class QSPanelControllerBase {public android.view.View mView;
 public boolean switchTileLayout(boolean force){return force;}}''',
'com/android/systemui/qs/QSFragment.java': '''package com.android.systemui.qs;public class QSFragment {
 public boolean mQsDisabled,mQsExpanded,mStackScrollerOverscrolling,mHeaderAnimating;public Panel mQSPanelController=new Panel();
 public static class Panel {public android.view.View mView=new android.view.View();}
 public void updateQsState(){mQSPanelController.mView.setVisibility(8);}}
''',
'com/android/systemui/qs/QuickStatusBarHeader.java': '''package com.android.systemui.qs;public class QuickStatusBarHeader extends android.widget.FrameLayout {
 public android.view.View mClockView;public void onFinishInflate(){}public void onConfigurationChanged(android.content.res.Configuration c){}public void updateResources(){}}
''',
'com/android/systemui/qs/tileimpl/QSTileViewImpl.java': '''package com.android.systemui.qs.tileimpl;import android.view.*;import android.widget.*;import android.graphics.drawable.*;
public class QSTileViewImpl extends LinearLayout {public View _icon=new View();public LinearLayout labelContainer=new LinearLayout();
 public View sideView=new View();public RippleDrawable ripple=new RippleDrawable();public Drawable colorBackgroundDrawable=ripple.fill;
 public QSTileViewImpl(){addView(_icon);labelContainer.addView(new TextView());labelContainer.addView(new TextView());addView(labelContainer);}
 public int measuredWidth,measuredHeight;public void onMeasure(int w,int h){measuredWidth=w;measuredHeight=h;}
 public void onConfigurationChanged(android.content.res.Configuration c){}}
''',
'Check.java': r'''package com.lijinmu.arashi.xposed.hooks;
import android.view.*;import android.widget.*;import android.content.res.*;import android.graphics.drawable.*;
import com.lijinmu.arashi.xposed.*;import io.github.libxposed.api.*;import java.lang.reflect.*;import java.util.*;
import com.android.systemui.shade.*;import com.android.systemui.qs.*;import com.android.systemui.qs.tileimpl.*;
import com.android.systemui.statusbar.phone.NotificationPanelViewController;
public class Check {
 static XposedModule module=new XposedModule();static void check(boolean b){if(!b)throw new AssertionError();}
 static Object call(Method m,Object o,Object...args)throws Throwable{
  return module.hooks.get(m).intercept(new XposedInterface.Chain(o,args,a->m.invoke(o,a)));}
 static <T extends View>T named(T v,String name){v.setId(name.hashCode());return v;}
 static class Quick extends LinearLayout {public View mBrightnessView=new View(),mQsCustomModeView=new View();}
 public static void main(String[] a)throws Throwable {
  SystemUiQsHooks.hookSystemUI(module,Check.class.getClassLoader());
  check(!HookConfig.defaultEnabled(HookConfig.KEY_AOSP_QS));check(Arrays.asList(HookConfig.keys()).contains(HookConfig.KEY_AOSP_QS));
  QuickSettingsController modern=new QuickSettingsController();Method height=modern.getClass().getMethod("setExpansionHeight",float.class);
  call(height,modern,300f);check(modern.original==1&&modern.mExpansionHeight==100);ConfigManager.enabled=true;
  call(height,modern,300f);check(modern.mExpansionHeight==300&&modern.mExpanded&&!modern.mFullyExpanded&&modern.original==1&&modern.updates==1&&modern.panel.callbacks==1);
  call(height,modern,900f);check(modern.mExpansionHeight==500&&modern.mFullyExpanded&&modern.panel.full);
  call(height,modern,-20f);check(modern.mExpansionHeight==100&&!modern.mExpanded&&!modern.mFullyExpanded);
  modern.mDozing=true;call(height,modern,300f);check(!modern.mExpanded);modern.mDozing=false;
  modern.mStackScrollerOverscrolling=true;call(height,modern,300f);check(!modern.mExpanded);modern.mStackScrollerOverscrolling=false;
  modern.mAnimating=true;call(height,modern,300f);check(!modern.mExpanded);modern.mAnimatorExpand=true;call(height,modern,300f);check(modern.mExpanded);
  call(height,modern,Float.NaN);check(modern.mExpansionHeight==100&&!modern.mExpanded);
  check(SystemUiQsExpansion.clamp(Float.POSITIVE_INFINITY,100,500)==100);
  check(SystemUiQsExpansion.clamp(200,100,50)==100);check(!SystemUiQsExpansion.supports(Object.class,true));
  NotificationPanelViewController old=new NotificationPanelViewController();Method oldHeight=old.getClass().getMethod("setQsExpansion",float.class);
  var listener=new NotificationPanelViewController.ExpansionListener();old.mExpansionListeners.add(listener);old.mBarState=1;
  old.mFalsingCollector.enforce=true;call(oldHeight,old,500f);
  check(old.mQsExpanded&&old.mQsFullyExpanded&&old.mQsExpansionHeight==500&&old.original==0&&old.updates==1&&old.layout==4);
  check(listener.value==1&&old.mStatusBar.bouncer==1&&old.mView.title.equals("QS"));
  old.mFalsingManager.disabled=true;call(oldHeight,old,500f);check(old.mStatusBar.bouncer==1);
  call(oldHeight,old,0f);check(!old.mQsExpanded&&old.mQsExpansionHeight==100);
  old.mQsMinExpansionHeight=0;old.mQsMaxExpansionHeight=0;call(oldHeight,old,10f);check(!old.mQsFullyExpanded&&listener.value==0);
  ConfigManager.enabled=false;call(oldHeight,old,500f);check(old.original==1);ConfigManager.enabled=true;
  QSFragment fragment=new QSFragment();Method state=fragment.getClass().getMethod("updateQsState");
  call(state,fragment);check(fragment.mQSPanelController.mView.visibility==View.INVISIBLE);
  fragment.mQsExpanded=true;call(state,fragment);check(fragment.mQSPanelController.mView.visibility==View.VISIBLE);
  fragment.mQsDisabled=true;call(state,fragment);check(fragment.mQSPanelController.mView.visibility==View.INVISIBLE);
  fragment.mQsExpanded=false;fragment.mQsDisabled=false;fragment.mHeaderAnimating=true;call(state,fragment);check(fragment.mQSPanelController.mView.visibility==0);
  ConfigManager.enabled=false;call(state,fragment);check(fragment.mQSPanelController.mView.visibility==8);ConfigManager.enabled=true;
  ShadeHeaderController header=new ShadeHeaderController();Method vis=header.getClass().getMethod("updateVisibility");
  call(vis,header);check(header.header.visibility==4&&!header.visible);header.qsVisible=true;call(vis,header);
  check(header.header.visibility==0&&header.visible&&header.mShadeCarrierGroupController.mListening);
  check(header.mShadeCarrierGroupController.updates==1&&header.mShadeCarrierGroupController.mOnSingleCarrierChangedListener!=null);
  header.customizing=true;call(vis,header);check(header.header.visibility==4&&!header.mShadeCarrierGroupController.mListening);
  check(header.mShadeCarrierGroupController.updates==2&&header.mShadeCarrierGroupController.mOnSingleCarrierChangedListener==null);
  header.qsDisabled=true;call(vis,header);check(header.header.visibility==8);
  Resources split=new Resources();split.names.put(70,"bool/config_use_split_notification_shade");
  split.names.put(71,"bool/unrelated");Method bool=Resources.class.getMethod("getBoolean",int.class);
  Method splitDecision=com.android.systemui.util.LargeScreenUtils.class.getMethod("shouldUseSplitNotificationShade",Resources.class);
  for(int width:new int[]{0,400,599,600,960,1280}) {
   split.config.screenWidthDp=width;
   check((boolean)call(bool,split,70)==(width>=600));
   check((boolean)call(splitDecision,null,split)==(width>=600));
   check(!(boolean)call(bool,split,71));
  }
  split.owner="other";check(!(boolean)call(bool,split,70));
  split.owner="com.android.systemui";ConfigManager.enabled=false;
  check(!(boolean)call(bool,split,70));check(!(boolean)call(splitDecision,null,split));ConfigManager.enabled=true;
  Resources r=new Resources();r.names.put(1,"integer/quick_settings_num_columns");r.names.put(2,"dimen/qs_tile_height");
  Method integer=Resources.class.getMethod("getInteger",int.class),dim=Resources.class.getMethod("getDimensionPixelSize",int.class);
  check((int)call(integer,r,1)==2);check((int)call(dim,r,2)==160);
  check((float)call(Resources.class.getMethod("getDimension",int.class),r,2)==160f);
  check((int)call(dim,r,45)==17);r.owner="other";check((int)call(integer,r,1)==17);check((int)call(dim,r,2)==17);
  ConfigManager.enabled=false;r.owner="com.android.systemui";check((int)call(dim,r,2)==17);ConfigManager.enabled=true;
  check(SystemUiQsHooks.neutral(r,true)==900);r.config.uiMode=32;check(SystemUiQsHooks.neutral(r,true)==50&&SystemUiQsHooks.neutral(r,false)==800);
  QuickStatusBarHeader h=new QuickStatusBarHeader();LinearLayout wrapper=new LinearLayout();RelativeLayout holder=new RelativeLayout();Quick quick=named(new Quick(),"quick_qs_panel");
  h.addView(wrapper);wrapper.addView(holder);holder.addView(quick);SystemUiQsHooks.restoreHeader(h);
  check(quick.parent==h&&wrapper.visibility==8&&quick.pb==48&&quick.mBrightnessView.visibility==8&&quick.mQsCustomModeView.visibility==8);
  SystemUiQsHooks.restoreHeader(h);check(h.children.size()==2); // no duplicate reparent on config updates
  QuickStatusBarHeader h12=new QuickStatusBarHeader();wrapper=new LinearLayout();holder=named(new RelativeLayout(),"qs_container");
  LinearLayout date=named(new LinearLayout(),"quick_status_bar_date_privacy"),status=named(new LinearLayout(),"quick_qs_status_icons");
  View vendorClock=named(new View(),"clock"),nativeClock=named(new View(),"clock"),privacy=named(new View(),"privacy_container");
  date.addView(vendorClock);date.addView(privacy);status.addView(nativeClock);quick=named(new Quick(),"quick_qs_panel");
  quick.setLayoutParams(new RelativeLayout.LayoutParams(-1,-2));holder.addView(status);holder.addView(quick);wrapper.addView(date);wrapper.addView(holder);h12.addView(wrapper);
  SystemUiQsHooks.restoreHeader(h12);check(date.parent==h12&&holder.parent==h12&&wrapper.visibility==8);
  check(vendorClock.id==View.NO_ID&&vendorClock.visibility==8&&h12.mClockView==nativeClock&&privacy.parent==date);
  check(((RelativeLayout.LayoutParams)quick.params).below==status.id&&status.visibility==0);SystemUiQsHooks.restoreHeader(h12);check(h12.children.size()==3);
  QSTileViewImpl tile=new QSTileViewImpl();RippleDrawable original=tile.ripple;SystemUiQsHooks.restoreTile(tile);
  check(tile.orientation==0&&tile.ps==32&&tile.pt==24&&tile._icon.params.width==40&&tile.children.size()==3);
  check(((LinearLayout.LayoutParams)tile.labelContainer.params).weight==1f&&tile.sideView.parent==tile);
  check(((TextView)tile.labelContainer.getChildAt(0)).size==14&&tile.ripple.fill.radius==56&&original.fill.radius==0);
  check(tile.ripple!=original&&tile.colorBackgroundDrawable==tile.ripple.fill);
  SystemUiQsHooks.restoreTile(tile);check(tile.children.size()==3);
  Method measure=tile.getClass().getMethod("onMeasure",int.class,int.class);call(measure,tile,600,600);
  check(tile.measuredWidth==600&&tile.measuredHeight==(160|View.MeasureSpec.EXACTLY));
  ConfigManager.enabled=false;call(measure,tile,600,600);check(tile.measuredHeight==600);ConfigManager.enabled=true;
  var orderController=new QSPanelControllerBase();Quick orderPanel=new Quick();
  orderPanel.addView(new View());orderPanel.addView(orderPanel.mBrightnessView);orderController.mView=orderPanel;
  check((boolean)call(QSPanelControllerBase.class.getMethod("switchTileLayout",boolean.class),orderController,true));
  check(orderPanel.getChildAt(0)==orderPanel.mBrightnessView);
  ConfigManager.enabled=false;QSTileViewImpl untouched=new QSTileViewImpl();var constructor=QSTileViewImpl.class.getConstructor();
  module.hooks.get(constructor).intercept(new XposedInterface.Chain(untouched,new Object[]{},x->null));check(untouched.orientation==1);
  ConfigManager.enabled=true;module.hooks.get(constructor).intercept(new XposedInterface.Chain(untouched,new Object[]{},x->null));check(untouched.orientation==0);
  System.out.println("AOSP QS: 12/14 expansion, falsing, callbacks, scoped resources, disabled fallback, status headers, hierarchy and isolated tile geometry passed");
 }
}''',
})
# Drawable fixtures keep real bounds/levels so the AOSP rounded progress algorithm executes.
stubs['android/R.java'] = stubs['android/R.java'].replace('mask=999;', 'mask=999,background=998,progress=997;')
stubs['android/graphics/Rect.java'] = 'package android.graphics;public class Rect {public int left,top,right,bottom;public int width(){return right-left;}public int height(){return bottom-top;}}'
stubs['android/graphics/drawable/Drawable.java'] = '''package android.graphics.drawable;import android.content.res.Resources;import android.graphics.Rect;
public class Drawable {public Rect bounds=new Rect();public int level,tint;public Drawable mutate(){return this;}public void setTint(int v){tint=v;}
 public ConstantState getConstantState(){return null;}public Rect getBounds(){return bounds;}public int getLevel(){return level;}
 public void setBounds(int l,int t,int r,int b){bounds.left=l;bounds.top=t;bounds.right=r;bounds.bottom=b;onBoundsChange(bounds);}
 protected void onBoundsChange(Rect r){}protected boolean onLevelChange(int l){return false;}
 public boolean setLevel(int l){level=l;return onLevelChange(l);}public boolean onLayoutDirectionChanged(int d){return false;}
 public static abstract class ConstantState {public Drawable newDrawable(){return newDrawable(null);}public abstract Drawable newDrawable(Resources r);
 public int getChangingConfigurations(){return 0;}}}
'''
stubs['android/graphics/drawable/DrawableWrapper.java'] = '''package android.graphics.drawable;import android.graphics.Rect;
public class DrawableWrapper extends Drawable {public Drawable child;public DrawableWrapper(Drawable d){child=d;}
 public Drawable getDrawable(){return child;}protected void onBoundsChange(Rect r){child.setBounds(r.left,r.top,r.right,r.bottom);}}
'''
stubs['android/graphics/drawable/InsetDrawable.java'] = '''package android.graphics.drawable;import android.content.res.Resources;
public class InsetDrawable extends DrawableWrapper {public InsetDrawable(Drawable d,int inset){super(d);}
 public InsetDrawable(Drawable d,int a,int b,int c,int e){super(d);}
 public ConstantState getConstantState(){Drawable saved=child;return new ConstantState(){public Drawable newDrawable(Resources r){return new InsetDrawable(saved,0);}};}}
'''
stubs['android/graphics/drawable/GradientDrawable.java'] = stubs['android/graphics/drawable/GradientDrawable.java'].replace('public int radius;', 'public int radius,color;public void setColor(int c){color=c;}public void setSize(int w,int h){}')
stubs['android/graphics/drawable/LayerDrawable.java'] = '''package android.graphics.drawable;
public class LayerDrawable extends Drawable {public Drawable[] layers;public LayerDrawable(Drawable[] d){layers=d;}
 public void setLayerSize(int i,int w,int h){}public void setLayerGravity(int i,int g){}public void setLayerInsetEnd(int i,int v){}public void setId(int i,int id){}}
'''
stubs['android/widget/SeekBar.java'] = '''package android.widget;public class SeekBar extends android.view.View {
 public android.graphics.drawable.Drawable progress,thumb=new android.graphics.drawable.Drawable();
 public void setProgressDrawable(android.graphics.drawable.Drawable d){progress=d;}public void setThumb(android.graphics.drawable.Drawable d){thumb=d;}}
'''
stubs['android/view/Gravity.java'] = stubs['android/view/Gravity.java'].replace('CENTER_VERTICAL=16;', 'CENTER_VERTICAL=16,END=8388613,FILL_HORIZONTAL=7;')
stubs['android/content/res/Resources.java'] = stubs['android/content/res/Resources.java'].replace(' public int getColor(', ' public android.graphics.drawable.Drawable getDrawable(int id,Theme t){return new android.graphics.drawable.Drawable();}public int getColor(')
stubs['com/lijinmu/arashi/xposed/ConfigManager.java'] = stubs['com/lijinmu/arashi/xposed/ConfigManager.java'].replace('return enabled;', 'return HookConfig.KEY_AOSP_QS.equals(key)&&enabled;').replace('public static boolean enabled;', 'public static boolean enabled;public static int systemUiThemeColorCached(){return 0xff123456;}')
stubs['com/lijinmu/arashi/xposed/hooks/SystemUiMonetHooks.java'] = stubs['com/lijinmu/arashi/xposed/hooks/SystemUiMonetHooks.java'].replace('static boolean setField', 'static int accent(android.content.res.Resources r){return 0xff123456;}static boolean setField')
stubs['Check.java'] = stubs['Check.java'].replace('  System.out.println("AOSP QS:', '''  FrameLayout brightness=new FrameLayout();SeekBar slider=named(new SeekBar(),"slider");View auto=named(new View(),"brightness_auto");
  brightness.addView(slider);brightness.addView(auto);SystemUiQsHooks.restoreBrightness(brightness);
  check(slider.thumb==null&&slider.params.width==-1&&slider.params.height==96&&auto.visibility==8&&brightness.params.height==96);
  LayerDrawable bar=(LayerDrawable)slider.progress;check(bar.layers[1] instanceof AospBrightnessProgressDrawable);
  GradientDrawable fill=new GradientDrawable();AospBrightnessProgressDrawable progress=new AospBrightnessProgressDrawable(fill);
  progress.setBounds(0,0,400,48);progress.setLevel(0);check(fill.getBounds().width()==48);
  progress.setLevel(5000);check(fill.getBounds().width()==224);progress.setLevel(10000);check(fill.getBounds().width()==400);
  check(progress.getConstantState().newDrawable(brightness.resources) instanceof AospBrightnessProgressDrawable);
  System.out.println("AOSP QS:''')
with tempfile.TemporaryDirectory(prefix='arashi-aosp-qs-') as directory:
    temp = Path(directory)
    for name, source in stubs.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    sources = list(temp.rglob('*.java')) + [ROOT / 'app/src/main/java/com/lijinmu/arashi/xposed' / name
        for name in ['HookConfig.java', 'Reflect.java', 'hooks/SystemUiQsHooks.java', 'hooks/SystemUiQsExpansion.java', 'hooks/AospBrightnessProgressDrawable.java', 'SystemUiThemeColors.java']]
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', str(temp / 'classes')] + [str(p) for p in sources], check=True)
    subprocess.run(['java', '-cp', str(temp / 'classes'), 'com.lijinmu.arashi.xposed.hooks.Check'], check=True)
