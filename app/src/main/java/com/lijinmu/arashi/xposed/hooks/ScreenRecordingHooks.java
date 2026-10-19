package com.lijinmu.arashi.xposed.hooks;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Spinner;
import android.widget.Switch;
import com.lijinmu.arashi.xposed.ConfigManager;
import com.lijinmu.arashi.xposed.HookConfig;
import com.lijinmu.arashi.xposed.Reflect;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Restores the existing SystemUI audio-source UI and honors its selection. */
public final class ScreenRecordingHooks {
    private static final String TAG = "[Arashi-ScreenRecord]";
    private static final ThreadLocal<Integer> REQUESTED_SOURCE = new ThreadLocal<>();

    private ScreenRecordingHooks() {}

    public static void hookSystemUI(XposedModule module, ClassLoader loader) {
        try {
            Class<?> dialog = Class.forName("com.android.systemui.screenrecord.ScreenRecordDialog", false, loader);
            Class<?> service = Class.forName("com.android.systemui.screenrecord.RecordingService", false, loader);
            Method create = Reflect.findMethodExact(dialog, "onCreate", Bundle.class);
            if (create == null) return;
            // Install the intent hook before enabling the UI. Only requests inside
            // requestScreenCapture may override the original extra_useAudio value.
            boolean intentHooked = false;
            for (Method method : service.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (!method.getName().equals("getStartIntent") || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != Intent.class || args.length < 2
                        || args[0] != Context.class || args[1] != int.class) continue;
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    Integer source = REQUESTED_SOURCE.get();
                    if (source != null && result instanceof Intent) {
                        ((Intent) result).putExtra("extra_useAudio", source);
                        Log.i(TAG, "Recording audio source=" + source);
                    }
                    return result;
                });
                intentHooked = true;
            }
            if (!intentHooked) return;
            boolean requestHooked = false;
            for (Method method : dialog.getDeclaredMethods()) {
                if (!method.getName().equals("requestScreenCapture")) continue;
                module.hook(method).intercept(chain -> {
                    if (!ConfigManager.isEnabled(HookConfig.KEY_RECORD_INTERNAL_AUDIO)) return chain.proceed();
                    Object target = chain.getThisObject();
                    Object audio = Reflect.getObjectField(target, "mAudioSwitch");
                    Object options = Reflect.getObjectField(target, "mOptions");
                    if (!(audio instanceof Switch) || !(options instanceof Spinner)) return chain.proceed();
                    int source = 0; // NONE: the record-audio switch still controls recording.
                    if (((Switch) audio).isChecked()) {
                        Object selected = ((Spinner) options).getSelectedItem();
                        if (!(selected instanceof Enum<?>)) return chain.proceed();
                        String name = ((Enum<?>) selected).name();
                        if (!name.equals("INTERNAL") && !name.equals("MIC") && !name.equals("MIC_AND_INTERNAL")) {
                            return chain.proceed();
                        }
                        source = ((Enum<?>) selected).ordinal();
                    }
                    Integer previous = REQUESTED_SOURCE.get();
                    REQUESTED_SOURCE.set(source);
                    try { return chain.proceed(); }
                    finally {
                        if (previous == null) REQUESTED_SOURCE.remove(); else REQUESTED_SOURCE.set(previous);
                    }
                });
                requestHooked = true;
            }
            if (!requestHooked) return;
            module.hook(create).intercept(chain -> {
                Object result = chain.proceed();
                if (!ConfigManager.isEnabled(HookConfig.KEY_RECORD_INTERNAL_AUDIO)) return result;
                Object target = chain.getThisObject();
                // Android 12 uses an Activity; Android 14 uses a SystemUIDialog.
                Context context;
                if (target instanceof Activity) context = (Activity) target;
                else if (target instanceof Dialog) context = ((Dialog) target).getContext();
                else return result;
                int id = context.getResources().getIdentifier("record_spinner_background", "id", context.getPackageName());
                View row = id == 0 ? null : target instanceof Activity
                        ? ((Activity) target).findViewById(id) : ((Dialog) target).findViewById(id);
                Object options = Reflect.getObjectField(target, "mOptions");
                if (row != null && options instanceof Spinner) {
                    Spinner spinner = (Spinner) options;
                    // Default to internal audio, without enabling the microphone.
                    for (int i = 0; i < spinner.getCount(); i++) {
                        Object item = spinner.getItemAtPosition(i);
                        if (item instanceof Enum<?> && ((Enum<?>) item).name().equals("INTERNAL")) {
                            spinner.setSelection(i);
                            break;
                        }
                    }
                    row.setVisibility(View.VISIBLE);
                }
                return result;
            });
            Log.i(TAG, "Recording audio-source hooks registered");
        } catch (Throwable error) {
            Log.w(TAG, "Recording audio-source hooks unavailable", error);
        }
    }
}
