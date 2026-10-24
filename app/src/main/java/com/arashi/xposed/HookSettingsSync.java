package com.arashi.xposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;

import io.github.libxposed.service.XposedService;

public final class HookSettingsSync {
    private HookSettingsSync() {
    }

    public static synchronized void publish(Context context, XposedService service) {
        try {
            SharedPreferences local = context.getSharedPreferences(
                    HookConfig.PREF_NAME, Context.MODE_PRIVATE);
            SharedPreferences remote = service.getRemotePreferences(HookConfig.PREF_NAME);
            Map<String, ?> saved = remote.getAll();
            SharedPreferences.Editor editor = remote.edit();
            boolean changed = !Boolean.TRUE.equals(saved.get(HookConfig.KEY_REMOTE_READY));
            for (String key : HookConfig.keys()) {
                boolean value = local.getBoolean(key, HookConfig.defaultEnabled(key));
                if (!Boolean.valueOf(value).equals(saved.get(key))) {
                    editor.putBoolean(key, value);
                    changed = true;
                }
            }
            if (changed) {
                editor.putBoolean(HookConfig.KEY_REMOTE_READY, true).apply();
            }
        } catch (RuntimeException error) {
            Log.w("[Arashi-Settings]", "Remote settings synchronization unavailable", error);
        }
    }
}
