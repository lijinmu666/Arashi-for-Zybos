"""Exercise real reply hook callbacks: legacy helper, R8 callbacks, policy bits and restoration."""
from pathlib import Path
import ast
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]
module=ast.parse((ROOT/'tests/check_navigation_access.py').read_text())
base=next(n.value for n in module.body if isinstance(n,ast.Assign)
          and any(isinstance(t,ast.Name) and t.id=='STUBS' for t in n.targets))
all_stubs=ast.literal_eval(base)
names=['android/util/Log.java','com/lijinmu/arashi/xposed/ConfigManager.java',
       'io/github/libxposed/api/XposedInterface.java','io/github/libxposed/api/XposedModule.java']
stubs={n:all_stubs[n] for n in names}
stubs['com/android/systemui/statusbar/policy/RemoteInputQuickSettingsDisabler.java']='''package com.android.systemui.statusbar.policy;
public class RemoteInputQuickSettingsDisabler {
 public boolean remoteInputActive=true,isLandscape=true,shouldUseSplitNotificationShade=false;
 public int adjustDisableFlags(int flags){return remoteInputActive&&isLandscape?flags|1:flags;}
}'''
for name in ['com.android.systemui.statusbar.phone.StatusBar',
             'com.android.systemui.statusbar.phone.CentralSurfacesCommandQueueCallbacks',
             'com.android.systemui.qs.QSFragment']:
    package,cls=name.rsplit('.',1)
    stubs[name.replace('.','/')+'.java']=f'''package {package};
public class {cls} {{
 public com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler mRemoteInputQuickSettingsDisabler=new com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler();
 public int calls,result,display,disable1;public boolean throwNow,animate;
 public void disable(int id,int d1,int d2,boolean a){{calls++;display=id;disable1=d1;animate=a;
 var r=mRemoteInputQuickSettingsDisabler;
 result=r.remoteInputActive&&r.isLandscape&&!r.shouldUseSplitNotificationShade?d2|1:d2;
 if(throwNow)throw new IllegalStateException("callback error");}}
}}'''
stubs['com/android/systemui/statusbar/policy/RemoteInputView.java']='''package com.android.systemui.statusbar.policy;
public class RemoteInputView {
 public RemoteEditText mEditText=new RemoteEditText();public boolean mSending,shown=true;
 public boolean isShown(){return shown;}
 public boolean isActive(){return mEditText.focused&&mEditText.enabled;}
 public void focus(){} public void onDefocus(boolean a,boolean b,Runnable r){}
 public static class RemoteEditText {
  public boolean enabled,focusable,focused,cursor;public int closes;
  public boolean isEnabled(){return enabled;}public boolean isFocusable(){return focusable;}
  public boolean isFocused(){return focused;}public boolean isShown(){return true;}
  public boolean hasWindowFocus(){return true;}
  public void setEnabled(boolean b){enabled=b;}public void setFocusable(boolean b){focusable=b;}
  public void setFocusableInTouchMode(boolean b){focusable=b;}public void setCursorVisible(boolean b){cursor=b;}
  public boolean requestFocus(){focused=enabled&&focusable;return focused;}
  public void defocusIfNeeded(boolean a){closes++;focused=false;focusable=false;}
 }
}'''
stubs['com/android/systemui/statusbar/notification/row/NotificationContentView.java']='''package com.android.systemui.statusbar.notification.row;
public class NotificationContentView {
 public com.android.systemui.statusbar.policy.RemoteInputView mExpandedRemoteInput=new com.android.systemui.statusbar.policy.RemoteInputView(),mHeadsUpRemoteInput=new com.android.systemui.statusbar.policy.RemoteInputView();
 public Object mExpandedChild=new Object(),mHeadsUpChild=new Object();
 public boolean mUserExpanding,shown=true;public int result,calls;
 public boolean isShown(){return shown;}public int calculateVisibleType(){calls++;return result;}
 public void selectLayout(boolean a,boolean b){} public void onLayout(boolean b,int a,int c,int d,int e){}
}'''
stubs['Check.java']='''import java.lang.reflect.*;
import com.lijinmu.arashi.xposed.*;import com.lijinmu.arashi.xposed.hooks.*;import io.github.libxposed.api.*;
import com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler;
public class Check {
 static XposedModule module=new XposedModule();
 static void check(boolean v){if(!v)throw new AssertionError();}
 static Object call(Method m,Object self,Object[] args,XposedInterface.Next next)throws Throwable {
  check(module.hooks.containsKey(m));return module.hooks.get(m).intercept(new XposedInterface.Chain(self,args,next));}
 public static void main(String[] args)throws Throwable {
 check(!HookConfig.defaultEnabled(HookConfig.KEY_NOTIFICATION_REPLY));
 NotificationReplyHooks.hookSystemUI(module,Check.class.getClassLoader());
 var content=new com.android.systemui.statusbar.notification.row.NotificationContentView();
 Method calculate=content.getClass().getMethod("calculateVisibleType");
 XposedInterface.Next calc=a->content.calculateVisibleType();
 ConfigManager.enabled=true;
 check((Integer)call(calculate,content,new Object[]{},calc)==0);
 var edit=content.mExpandedRemoteInput.mEditText;edit.enabled=true;edit.focused=true;
 check((Integer)call(calculate,content,new Object[]{},calc)==1);
 content.result=3;check((Integer)call(calculate,content,new Object[]{},calc)==1);
 // Sending, explicit expansion gestures, hidden shade and disabled hook remain native.
 content.mExpandedRemoteInput.mSending=true;
 check((Integer)call(calculate,content,new Object[]{},calc)==3);
 content.mExpandedRemoteInput.mSending=false;content.mUserExpanding=true;
 check((Integer)call(calculate,content,new Object[]{},calc)==3);
 content.mUserExpanding=false;content.shown=false;
 check((Integer)call(calculate,content,new Object[]{},calc)==3);
 content.shown=true;ConfigManager.enabled=false;
 check((Integer)call(calculate,content,new Object[]{},calc)==3);
 ConfigManager.enabled=true;content.result=2;
 check((Integer)call(calculate,content,new Object[]{},calc)==2);
 edit.focused=false;content.result=0;
 check((Integer)call(calculate,content,new Object[]{},calc)==0);
 content.mHeadsUpRemoteInput.mEditText.enabled=true;content.mHeadsUpRemoteInput.mEditText.focused=true;
 check((Integer)call(calculate,content,new Object[]{},calc)==2);
 content.mHeadsUpChild=null;check((Integer)call(calculate,content,new Object[]{},calc)==0);
 RemoteInputQuickSettingsDisabler legacy=new RemoteInputQuickSettingsDisabler();
 Method adjust=legacy.getClass().getMethod("adjustDisableFlags",int.class);
 ConfigManager.enabled=false;check(call(adjust,legacy,new Object[]{0x80},a->legacy.adjustDisableFlags((Integer)a[0])).equals(0x81));
 ConfigManager.enabled=true;
 for(int flags:new int[]{0,1,0x80,0x81,-1}) check(call(adjust,legacy,new Object[]{flags},a->{throw new AssertionError();}).equals(flags));
 for(String name:new String[]{"com.android.systemui.statusbar.phone.StatusBar","com.android.systemui.statusbar.phone.CentralSurfacesCommandQueueCallbacks","com.android.systemui.qs.QSFragment"}){
  Class<?> c=Class.forName(name);Object owner=c.getConstructor().newInstance();
  RemoteInputQuickSettingsDisabler state=(RemoteInputQuickSettingsDisabler)c.getField("mRemoteInputQuickSettingsDisabler").get(owner);
  Method method=c.getMethod("disable",int.class,int.class,int.class,boolean.class);
  XposedInterface.Next original=a->{try{return method.invoke(owner,a);}catch(InvocationTargetException e){throw e.getCause();}};
  Object[] input={7,0x200,0x80,true};call(method,owner,input,original);
  check(c.getField("result").getInt(owner)==0x80&&state.isLandscape);
  check(c.getField("display").getInt(owner)==7&&c.getField("disable1").getInt(owner)==0x200&&c.getField("animate").getBoolean(owner));
  // Existing system QS restrictions stay set; no other argument or policy bit is cleared.
  input[2]=0x81;call(method,owner,input,original);check(c.getField("result").getInt(owner)==0x81);
  input[2]=0x80;ConfigManager.enabled=false;call(method,owner,input,original);check(c.getField("result").getInt(owner)==0x81&&state.isLandscape);
  ConfigManager.enabled=true;state.remoteInputActive=false;call(method,owner,input,original);check(c.getField("result").getInt(owner)==0x80&&state.isLandscape);
  state.remoteInputActive=true;state.shouldUseSplitNotificationShade=true;call(method,owner,input,original);check(c.getField("result").getInt(owner)==0x80&&state.isLandscape);
  state.shouldUseSplitNotificationShade=false;state.isLandscape=false;call(method,owner,input,original);check(!state.isLandscape);
  state.isLandscape=true;c.getField("throwNow").setBoolean(owner,true);int before=c.getField("calls").getInt(owner);
  try{call(method,owner,input,original);throw new AssertionError();}catch(IllegalStateException expected){}
  check(state.isLandscape&&c.getField("calls").getInt(owner)==before+1);
 }
 System.out.println("Notification reply hooks: 12/14 paths, disabled mode, policy flags and exception restoration passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='arashi-reply-') as directory:
    tmp=Path(directory)
    for name,source in stubs.items():
        target=tmp/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source)
    real=ROOT/'app/src/main/java/com/lijinmu/arashi/xposed'
    subprocess.run(['javac','-d',str(tmp/'classes'),*map(str,tmp.rglob('*.java')),
                    str(real/'HookConfig.java'),str(real/'Reflect.java'),str(real/'hooks/NotificationReplyHooks.java')],check=True)
    subprocess.run(['java','-cp',str(tmp/'classes'),'Check'],check=True)
