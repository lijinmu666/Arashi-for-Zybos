package com.arashi.xposed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.os.Process;
import android.provider.Settings;


public final class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.arashi.settings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET_ALL = "get_all_settings";
    public static final String STATUS_PREF_NAME = "hook_runtime";
    public static final String KEY_SYSTEM_SERVER_BOOT = "system_server_boot";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_GET_ALL.equals(method)) {
            return null;
        }
        
        Context context = getContext();
        if (context == null) {
            return null;
        }

        
        
        if (Binder.getCallingUid() == Process.SYSTEM_UID) {
            try {
                int boot = Settings.Global.getInt(context.getContentResolver(),
                        "boot_count", -1);
                if (boot >= 0) {
                    SharedPreferences runtime = context.getSharedPreferences(
                            STATUS_PREF_NAME, Context.MODE_PRIVATE);
                    if (runtime.getInt(KEY_SYSTEM_SERVER_BOOT, -1) != boot) {
                        runtime.edit().putInt(KEY_SYSTEM_SERVER_BOOT, boot).apply();
                    }
                }
            } catch (RuntimeException ignored) {
                
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        result.putBoolean(HookConfig.KEY_EXTERNAL_INSTALL, prefs.getBoolean(HookConfig.KEY_EXTERNAL_INSTALL, false));
        result.putBoolean(HookConfig.KEY_SHELL_INSTALL, prefs.getBoolean(HookConfig.KEY_SHELL_INSTALL, false));
        result.putBoolean(HookConfig.KEY_CLEAR_BLACKLIST, prefs.getBoolean(HookConfig.KEY_CLEAR_BLACKLIST, false));
        result.putBoolean(HookConfig.KEY_CAPTIVE_PORTAL, prefs.getBoolean(HookConfig.KEY_CAPTIVE_PORTAL, false));
        result.putBoolean(HookConfig.KEY_HIDE_ADB, prefs.getBoolean(HookConfig.KEY_HIDE_ADB, false));
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
