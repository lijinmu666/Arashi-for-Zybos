package com.lijinmu.arashi.xposed;

import com.lijinmu.arashi.uninstall.UninstallConfig;
import com.lijinmu.arashi.uninstall.UninstallHistoryStore;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SettingsProvider extends ContentProvider {
    private static final String TAG = "[Arashi-Provider]";
    public static final String AUTHORITY = "com.lijinmu.arashi.settings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET_ALL = "get_all_settings";
    public static final String METHOD_GET_UNINSTALL = "get_uninstall_validation";
    public static final String METHOD_ADD_UNINSTALL_RECORD = "add_uninstall_record";
    public static final String METHOD_ADD_TCP_RECORD = "add_tcp_login_record";
    public static final String STATUS_PREF_NAME = "hook_runtime";
    public static final String KEY_SYSTEM_SERVER_BOOT = "system_server_boot";

private static final Map<Integer, Boolean> TRUSTED_UID_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(
                    HookConfig.PREF_NAME, Context.MODE_PRIVATE);
            if (!prefs.contains(HookConfig.KEY_ANDROID12_BLACKLIST_HOOK)) {
                boolean android12 = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU;
                prefs.edit().putBoolean(HookConfig.KEY_ANDROID12_BLACKLIST_HOOK, android12).apply();
            }
        }
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_ADD_TCP_RECORD.equals(method)) {
            Context context = getContext();
            if (context == null || extras == null || !isTcpRecordCaller(context)) return null;
            try {
                long recordId = com.lijinmu.arashi.tcp.TcpHistoryStore.append(context, extras);
                // Attribute notification permission and PendingIntent ownership to Arashi,
                // not the remote TCP caller. Authenticate before clearing Binder identity.
                long identity = Binder.clearCallingIdentity();
                try {
                    com.lijinmu.arashi.tcp.TcpBlockNotifications.post(context, recordId,
                            extras.getString(com.lijinmu.arashi.tcp.TcpHistoryStore.ERROR_CODE));
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
                return Bundle.EMPTY;
            } catch (RuntimeException error) {
                Log.w(TAG, "TCP record unavailable", error);
                return null;
            }
        }
        if (METHOD_ADD_UNINSTALL_RECORD.equals(method)) {
            if (Binder.getCallingUid() != Process.SYSTEM_UID || extras == null) {
                return null;
            }
            Context context = getContext();
            if (context == null) return null;
            try {
                long recordId = UninstallHistoryStore.append(context, extras);
                long identity = Binder.clearCallingIdentity();
                try {
                    com.lijinmu.arashi.notifications.UninstallNotifications.post(context, recordId, extras);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Uninstall notification unavailable", error);
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
                return Bundle.EMPTY;
            } catch (RuntimeException error) {
                Log.w(TAG, "Uninstall record unavailable", error);
                return null;
            }
        }
        if (!METHOD_GET_ALL.equals(method) && !METHOD_GET_UNINSTALL.equals(method)) {
            return null;
        }
        if (!isTrustedCaller()) {

Log.w(TAG, "reject untrusted caller uid=" + Binder.getCallingUid());
            return null;
        }
        
        Context context = getContext();
        if (context == null) {
            return null;
        }

        if (Binder.getCallingUid() == Process.SYSTEM_UID
                && "system_server".equals(arg)) {
            try {
                int boot = Settings.Global.getInt(context.getContentResolver(),
                        "boot_count", -1);
                if (boot >= 0) {
                    SharedPreferences runtime = context.getSharedPreferences(
                            STATUS_PREF_NAME, Context.MODE_PRIVATE);
                    if (runtime.getInt(KEY_SYSTEM_SERVER_BOOT, -1) != boot) {
                        runtime.edit().putInt(KEY_SYSTEM_SERVER_BOOT, boot).apply();
                    }
                    UninstallConfig.syncActivation(context, true);
                }
            } catch (RuntimeException ignored) {
            }
        }

        if (METHOD_GET_UNINSTALL.equals(method)) {
            SharedPreferences uninstall = context.getSharedPreferences(
                    UninstallConfig.PREF_NAME, Context.MODE_PRIVATE);
            Bundle policy = new Bundle();
            policy.putBoolean(UninstallConfig.ENABLED,
                    uninstall.getBoolean(UninstallConfig.ENABLED, false));
            policy.putBoolean(UninstallConfig.SPOOF_RESULT,
                    uninstall.getBoolean(UninstallConfig.SPOOF_RESULT, false));
            policy.putInt(UninstallConfig.TIMEOUT_SECONDS,
                    UninstallConfig.normalizeTimeoutSeconds(uninstall.getInt(
                            UninstallConfig.TIMEOUT_SECONDS, UninstallConfig.DEFAULT_TIMEOUT)));
            policy.putStringArrayList(UninstallConfig.WHITELIST,
                    new java.util.ArrayList<>(uninstall.getStringSet(
                            UninstallConfig.WHITELIST, java.util.Collections.emptySet())));
            policy.putStringArrayList(UninstallConfig.BLACKLIST,
                    new java.util.ArrayList<>(uninstall.getStringSet(
                            UninstallConfig.BLACKLIST, java.util.Collections.emptySet())));
            return policy;
        }

        SharedPreferences prefs = context.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE);
        
        Bundle result = new Bundle();
        result.putBoolean(HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL,
                prefs.getBoolean(HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL, false));
        result.putBoolean(HookConfig.KEY_ANDROID12_BLACKLIST_HOOK,
                prefs.getBoolean(HookConfig.KEY_ANDROID12_BLACKLIST_HOOK, false));
        result.putBoolean(HookConfig.KEY_EXTERNAL_INSTALL, prefs.getBoolean(HookConfig.KEY_EXTERNAL_INSTALL, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_SHELL_INSTALL, prefs.getBoolean(HookConfig.KEY_SHELL_INSTALL, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_CLEAR_BLACKLIST, prefs.getBoolean(HookConfig.KEY_CLEAR_BLACKLIST, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_CAPTIVE_PORTAL, prefs.getBoolean(HookConfig.KEY_CAPTIVE_PORTAL, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_ALLOW_USB, prefs.getBoolean(HookConfig.KEY_ALLOW_USB, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_RECORD_INTERNAL_AUDIO, prefs.getBoolean(HookConfig.KEY_RECORD_INTERNAL_AUDIO, HookConfig.defaultEnabled(HookConfig.KEY_RECORD_INTERNAL_AUDIO)));
        result.putBoolean(HookConfig.KEY_NOTIFICATION_REPLY, prefs.getBoolean(HookConfig.KEY_NOTIFICATION_REPLY, false));
        result.putBoolean(HookConfig.KEY_AOSP_STATUS_ICONS, HookConfig.statusBarIconsEnabled(prefs.getAll()));
        result.putBoolean(HookConfig.KEY_LEGACY_POWER_MENU, prefs.getBoolean(HookConfig.KEY_LEGACY_POWER_MENU, false));
        result.putBoolean(HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS,
                prefs.getBoolean(HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS, false));
        result.putBoolean(HookConfig.KEY_STATUS_BAR_DATE, prefs.getBoolean(HookConfig.KEY_STATUS_BAR_DATE, false));
        result.putBoolean(HookConfig.KEY_IOS_ICONS, prefs.getBoolean(HookConfig.KEY_IOS_ICONS, false));
        result.putBoolean(HookConfig.KEY_ALLOW_WALLPAPER, prefs.getBoolean(HookConfig.KEY_ALLOW_WALLPAPER, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_AOSP_APP_DETAILS, prefs.getBoolean(HookConfig.KEY_AOSP_APP_DETAILS, false));
        result.putBoolean(HookConfig.KEY_CLEAR_DETAIL_BLACK, prefs.getBoolean(HookConfig.KEY_CLEAR_DETAIL_BLACK, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_BLOCK_SUPERVISE, prefs.getBoolean(HookConfig.KEY_BLOCK_SUPERVISE, HookConfig.defaultEnabled(HookConfig.KEY_BLOCK_SUPERVISE)));
        result.putBoolean(HookConfig.KEY_BLOCK_USAGE_REPORT, prefs.getBoolean(HookConfig.KEY_BLOCK_USAGE_REPORT, HookConfig.defaultEnabled(HookConfig.KEY_BLOCK_USAGE_REPORT)));
        result.putBoolean(HookConfig.KEY_DEV_OPTIONS, prefs.getBoolean(HookConfig.KEY_DEV_OPTIONS, HookConfig.DEFAULT_ENABLED));
        result.putBoolean(HookConfig.KEY_DEV_OPTIONS_MENU,
                prefs.getBoolean(HookConfig.KEY_DEV_OPTIONS_MENU,
                        HookConfig.defaultEnabled(HookConfig.KEY_DEV_OPTIONS_MENU)));
        result.putBoolean(HookConfig.KEY_KEEP_BACKGROUND, prefs.getBoolean(HookConfig.KEY_KEEP_BACKGROUND, HookConfig.defaultEnabled(HookConfig.KEY_KEEP_BACKGROUND)));
        result.putStringArrayList(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST,
                new java.util.ArrayList<>(prefs.getStringSet(
                        HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, java.util.Collections.emptySet())));
        result.putBoolean(HookConfig.KEY_NEUTRALIZE_POLICY, prefs.getBoolean(HookConfig.KEY_NEUTRALIZE_POLICY, HookConfig.defaultEnabled(HookConfig.KEY_NEUTRALIZE_POLICY)));
        result.putBoolean(HookConfig.KEY_NEUTRALIZE_ADVANCE, prefs.getBoolean(HookConfig.KEY_NEUTRALIZE_ADVANCE, HookConfig.defaultEnabled(HookConfig.KEY_NEUTRALIZE_ADVANCE)));
        result.putBoolean(HookConfig.KEY_NO_TIMEOUT_LOCK, prefs.getBoolean(HookConfig.KEY_NO_TIMEOUT_LOCK, HookConfig.defaultEnabled(HookConfig.KEY_NO_TIMEOUT_LOCK)));
        result.putBoolean(HookConfig.KEY_BLOCK_DEFAULT_BROWSER, prefs.getBoolean(HookConfig.KEY_BLOCK_DEFAULT_BROWSER, HookConfig.DEFAULT_ENABLED));
        return result;
    }

    private boolean isTcpRecordCaller(Context context) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return true;
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages != null) {
            for (String name : packages) {
                if ("com.zuoyebang.iot.zptcpservice".equals(name)) return true;
            }
        }
        return false;
    }

private boolean isTrustedCaller() {
        int uid = Binder.getCallingUid();
        if (uid == Process.SYSTEM_UID || uid == Process.myUid()
                || uid < Process.FIRST_APPLICATION_UID) {
            return true;
        }
        Boolean cached = TRUSTED_UID_CACHE.get(uid);
        if (cached != null) {
            return cached;
        }
        boolean trusted = isSystemAppUid(uid);

if (trusted) {
            TRUSTED_UID_CACHE.put(uid, Boolean.TRUE);
        }
        return trusted;
    }

    private boolean isSystemAppUid(int uid) {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages == null) {
                return false;
            }
            for (String pkg : packages) {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        || (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
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
