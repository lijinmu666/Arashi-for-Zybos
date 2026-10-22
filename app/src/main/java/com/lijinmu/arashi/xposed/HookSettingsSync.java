package com.lijinmu.arashi.xposed;

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
                boolean value = HookConfig.KEY_AOSP_STATUS_ICONS.equals(key)
                        ? HookConfig.statusBarIconsEnabled(local.getAll())
                        : local.getBoolean(key, HookConfig.defaultEnabled(key));
                if (!Boolean.valueOf(value).equals(saved.get(key))) {
                    editor.putBoolean(key, value);
                    changed = true;
                }
            }
            java.util.Set<String> selected = new java.util.HashSet<>(local.getStringSet(
                    HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, java.util.Collections.emptySet()));
            if (!selected.equals(saved.get(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST))) {
                editor.putStringSet(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, selected);
                changed = true;
            }
            int accent = SystemUiThemeColors.opaque(local.getInt(
                    HookConfig.KEY_SYSTEMUI_THEME_COLOR, SystemUiThemeColors.DEFAULT_COLOR));
            if (!Integer.valueOf(accent).equals(saved.get(HookConfig.KEY_SYSTEMUI_THEME_COLOR))) {
                editor.putInt(HookConfig.KEY_SYSTEMUI_THEME_COLOR, accent);
                changed = true;
            }
            if (changed) {
                editor.putBoolean(HookConfig.KEY_REMOTE_READY, true).apply();
            }
        } catch (RuntimeException error) {
            Log.w("[Arashi-Settings]", "Remote settings synchronization unavailable", error);
        }
    }
}
