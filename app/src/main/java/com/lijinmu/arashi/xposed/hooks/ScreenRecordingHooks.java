package com.lijinmu.arashi.xposed.hooks;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
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
            // Android 12 may inline getStartIntent into requestScreenCapture.
            // Patch at the final PendingIntent boundary as well, before its immutable
            // extras are captured. Thread-local scope excludes all unrelated callers.
            boolean pendingIntentHooked = hookPendingIntent(module);
            if (!intentHooked && !pendingIntentHooked) return;
            hookRecordingDiagnostics(module, loader, service);
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
                try {
                    module.deoptimize(method);
                    Log.i(TAG, "Deoptimized audio request: " + method);
                } catch (Throwable error) {
                    Log.w(TAG, "Unable to deoptimize audio request", error);
                }
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
    private static boolean hookPendingIntent(XposedModule module) {
        try {
            Method create = PendingIntent.class.getDeclaredMethod("getForegroundService",
                    Context.class, int.class, Intent.class, int.class);
            module.hook(create).intercept(chain -> {
                Integer source = REQUESTED_SOURCE.get();
                Object arg = chain.getArg(2);
                if (source != null && arg instanceof Intent) {
                    Intent intent = (Intent) arg;
                    if (isRecordingStart(intent)) {
                        // This request is still local: preserve resultCode, taps,
                        // flags, component and user context; update only the audio extra.
                        intent.putExtra("extra_useAudio", source);
                        Log.i(TAG, "PendingIntent recording audio source=" + source);
                    }
                }
                return chain.proceed();
            });
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Recording PendingIntent hook unavailable", error);
            return false;
        }
    }

    static boolean isRecordingStart(Intent intent) {
        return "com.android.systemui.screenrecord.START".equals(intent.getAction())
                && intent.getComponent() != null
                && "com.android.systemui.screenrecord.RecordingService".equals(
                        intent.getComponent().getClassName());
    }

    private static void hookRecordingDiagnostics(XposedModule module, ClassLoader loader,
                                                  Class<?> service) {
        try {
            Method command = Reflect.findMethodExact(service, "onStartCommand",
                    Intent.class, int.class, int.class);
            if (command != null) module.hook(command).intercept(chain -> {
                if (ConfigManager.isEnabled(HookConfig.KEY_RECORD_INTERNAL_AUDIO)) {
                    Object value = chain.getArg(0);
                    if (value instanceof Intent && isRecordingStart((Intent) value)) {
                        Log.i(TAG, "Service received audio source="
                                + ((Intent) value).getIntExtra("extra_useAudio", -1));
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            Log.w(TAG, "Recording service diagnostics unavailable", error);
        }
        try {
            Class<?> recorder = Class.forName(
                    "com.android.systemui.screenrecord.ScreenMediaRecorder", false, loader);
            for (String name : new String[] { "prepare", "start", "end", "save" }) {
                Method target = Reflect.findMethodExact(recorder, name);
                if (target == null) continue;
                module.hook(target).intercept(chain -> {
                    if (!ConfigManager.isEnabled(HookConfig.KEY_RECORD_INTERNAL_AUDIO))
                        return chain.proceed();
                    Object source = Reflect.getObjectField(chain.getThisObject(), "mAudioSource");
                    try {
                        Object result = chain.proceed();
                        Log.i(TAG, "Recorder " + name + " succeeded, source=" + source);
                        return result;
                    } catch (Throwable error) {
                        Log.e(TAG, "Recorder " + name + " failed, source=" + source, error);
                        // Preserve native cleanup/toast handling instead of hiding errors.
                        throw error;
                    }
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "Recording backend diagnostics unavailable", error);
        }
    }

}
