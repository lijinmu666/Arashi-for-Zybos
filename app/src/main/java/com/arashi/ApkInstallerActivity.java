package com.arashi;

import com.arashi.ui.MiuixPages;

import androidx.activity.ComponentActivity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;

import com.arashi.zybos.ZybosService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class ApkInstallerActivity extends ComponentActivity {
    private static final String TAG = "ArashiInstaller";
    private static final int REQ_ALL_FILES = 3001;

    private Uri pendingUri;
    private volatile File stagedApk;
    private volatile boolean installing;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        inspect(getIncomingUri(getIntent()));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ALL_FILES) return;
        Uri uri = pendingUri;
        pendingUri = null;
        if (Environment.isExternalStorageManager() && uri != null) {
            inspect(uri);
        } else {
            fail("未授予所有文件访问权限，无法读取 APK 信息");
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        inspect(getIncomingUri(intent));
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (!installing) cleanupStagedApk();
        super.onDestroy();
    }

    private Uri getIncomingUri(Intent intent) {
        Uri data = intent.getData();
        if (data != null) return data;
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        return stream instanceof Uri ? (Uri) stream : null;
    }

    private void inspect(Uri uri) {
        if (uri == null) {
            fail("未收到 APK 文件地址");
            return;
        }
        if (installing) return;
        new Thread(() -> {
            String path = resolvePath(uri);
            boolean shared = isSharedStoragePath(path);
            boolean readable = path != null && new File(path).canRead();
            boolean needsStaging = !readable
                    || ("content".equalsIgnoreCase(uri.getScheme()) && !shared);
            if ((shared || needsStaging) && !Environment.isExternalStorageManager()) {
                requestAllFilesAccess(uri);
                return;
            }
            if (needsStaging) {
                File staged = stageApk(uri);
                if (staged == null) {
                    fail("无法从此文件来源读取 APK，或无法暂存到共享存储；请检查文件访问权限");
                    return;
                }
                if (!retainStagedApk(staged)) return;
                path = staged.getAbsolutePath();
            }
            ApkDetails apk = readApk(uri, path);
            if (apk == null) {
                fail("无法从所选文件解析 APK 包名；请确认文件完整，或使用特权工具中的文件选择器安装");
                return;
            }
            Log.i(TAG, "APK path=" + path + ", package=" + apk.packageName);
            showConfirmation(apk);
        }, "Arashi-Installer-Inspect").start();
    }

    private File stageApk(Uri uri) {
        File temporary = null;
        boolean ready = false;
        try {
            File directory = new File(Environment.getExternalStorageDirectory(),
                    Environment.DIRECTORY_DOWNLOADS);
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            temporary = File.createTempFile("arashi-install-", ".apk", directory);
            try (InputStream input = getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) return null;
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            if (temporary.length() == 0) return null;
            ready = true;
            return temporary;
        } catch (Exception error) {
            Log.w(TAG, "暂存 APK 失败", error);
            return null;
        } finally {
            if (!ready && temporary != null) temporary.delete();
        }
    }

    private synchronized boolean retainStagedApk(File temporary) {
        if (destroyed || isFinishing()) {
            temporary.delete();
            return false;
        }
        cleanupStagedApk();
        stagedApk = temporary;
        return true;
    }

    private synchronized void cleanupStagedApk() {
        File temporary = stagedApk;
        stagedApk = null;
        if (temporary != null) temporary.delete();
    }

    private void submitInstall(ApkDetails apk) {
        if (installing) return;
        installing = true;
        MiuixPages.renderInstallerProgress(this, apk.label, apk.packageName, apk.icon);
        new Thread(() -> {
            try {
                if (!ZybosService.detect(this)) {
                    fail("未检测到 ZybosSysApiManager");
                    return;
                }
                if (apk.packageName == null) {
                    fail("无法读取 APK 包名，无法确认安装目标；请检查 APK 文件是否完整");
                    return;
                }
                PackageInfo before = installedPackageInfo(apk.packageName);
                boolean isSdcard = isSharedStoragePath(apk.path);
                Log.i(TAG, "submit path=" + apk.path + ", isSdcard=" + isSdcard
                        + ", package=" + apk.packageName);
                if (!ZybosService.silentInstallApk(apk.path, isSdcard, apk.packageName)) {
                    fail("安装请求发送失败：" + ZybosService.getLastError());
                    return;
                }
                PackageInfo installed = installedPackageInfo(apk.packageName);
                for (int attempt = 0; !installationChanged(before, installed)
                        && attempt < 60; attempt++) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    installed = installedPackageInfo(apk.packageName);
                }
                if (installationChanged(before, installed)) {
                    showResult("安装成功", apk.packageName + " 已安装到当前用户");
                } else if (before != null && installed != null) {
                    showResult("暂未确认安装", apk.packageName
                            + " 原本已安装，尚未检测到版本或更新时间变化");
                } else {
                    showResult("未检测到安装", "请求已发送，但尚未检测到 "
                            + apk.packageName + " 安装到当前用户");
                }
            } finally {
                cleanupStagedApk();
                installing = false;
            }
        }, "Arashi-Installer-Submit").start();
    }

    private PackageInfo installedPackageInfo(String packageName) {
        try {
            return getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return null;
        }
    }

    private boolean installationChanged(PackageInfo before, PackageInfo installed) {
        return installed != null && (before == null
                || installed.getLongVersionCode() != before.getLongVersionCode()
                || installed.lastUpdateTime != before.lastUpdateTime);
    }

    private void requestAllFilesAccess(Uri uri) {
        runOnUiThread(() -> {
            if (destroyed || isFinishing()) return;
            pendingUri = uri;
            try {
                Intent settings = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(settings, REQ_ALL_FILES);
            } catch (Throwable ignored) {
                fail("无法打开所有文件访问权限设置页");
            }
        });
    }

    private String resolvePath(Uri uri) {
        String path = resolveDocumentPath(uri);
        if (path != null) return path;
        if (!"content".equalsIgnoreCase(uri.getScheme())) return null;
        try (ParcelFileDescriptor descriptor =
                     getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) return null;
            return normalizePath(Os.readlink("/proc/self/fd/" + descriptor.getFd()));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String resolveDocumentPath(Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return normalizePath(uri.getPath());
            }
            if (!"content".equalsIgnoreCase(uri.getScheme())
                    || !DocumentsContract.isDocumentUri(this, uri)) return null;
            String id = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.downloads.documents".equals(uri.getAuthority())
                    && id.startsWith("raw:/")) {
                return normalizePath(id.substring(4));
            }
            if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
            int colon = id.indexOf(':');
            if (colon < 0) return null;
            String volume = id.substring(0, colon);
            String relative = id.substring(colon + 1);
            if (relative.isEmpty() || relative.startsWith("/")) return null;
            File root = "primary".equalsIgnoreCase(volume)
                    ? Environment.getExternalStorageDirectory()
                    : volume.matches("(?i)[0-9a-f]{4}-[0-9a-f]{4}")
                            ? new File("/storage", volume) : null;
            return root == null ? null : normalizePath(new File(root, relative).getPath());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String normalizePath(String path) {
        if (path == null) return null;
        path = normalizeSharedStorageAlias(path);
        File source = new File(path);
        if (!source.isAbsolute()) return null;
        try {
            path = normalizeSharedStorageAlias(source.getCanonicalPath());
        } catch (Throwable ignored) {
            return null;
        }
        return path;
    }

    private String normalizeSharedStorageAlias(String path) {
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        path = path.replaceFirst("^/mnt/user/\\d+/emulated/", "/storage/emulated/");
        path = path.replaceFirst("^/mnt/runtime/[^/]+/emulated/", "/storage/emulated/");
        path = path.replaceFirst("^/mnt/user/\\d+/primary/", primary + "/");
        path = path.replaceFirst("^/mnt/runtime/[^/]+/primary/", primary + "/");
        return path.replaceFirst("^/storage/self/primary/", primary + "/");
    }

    private static boolean isSharedStoragePath(String path) {
        return path != null && (path.startsWith("/storage/") || path.startsWith("/sdcard/"));
    }

    private ApkDetails readApk(Uri uri, String path) {
        ApkDetails apk = readApk(path, path);
        if (apk != null || !"content".equalsIgnoreCase(uri.getScheme())) return apk;
        try (ParcelFileDescriptor descriptor =
                     getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor != null) {
                apk = readApk("/proc/self/fd/" + descriptor.getFd(), path);
                if (apk != null) return apk;
            }
        } catch (Throwable ignored) { }
        File temporary = null;
        try {
            temporary = File.createTempFile("arashi-apk-", ".apk", getCacheDir());
            try (InputStream input = getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) return null;
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            return readApk(temporary.getAbsolutePath(), path);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (temporary != null) temporary.delete();
        }
    }

    private ApkDetails readApk(String archivePath, String shownPath) {
        try {
            PackageManager manager = getPackageManager();
            PackageInfo info = manager.getPackageArchiveInfo(archivePath, 0);
            if (info == null || info.packageName == null) return null;
            ApplicationInfo app = info.applicationInfo;
            String label = info.packageName;
            Drawable icon = manager.getDefaultActivityIcon();
            if (app != null) {
                app.sourceDir = archivePath;
                app.publicSourceDir = archivePath;
                try {
                    CharSequence loaded = app.loadLabel(manager);
                    if (loaded != null && loaded.length() != 0) label = loaded.toString();
                } catch (Throwable ignored) { }
                try {
                    icon = app.loadIcon(manager);
                } catch (Throwable ignored) { }
            }
            return new ApkDetails(shownPath, info.packageName,
                    label, icon);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showConfirmation(ApkDetails apk) {
        runOnUiThread(() -> {
            if (!destroyed && !isFinishing()) {
                MiuixPages.renderInstallerConfirmation(
                        this, apk.path, apk.packageName, apk.label, apk.icon);
            }
        });
    }

    public void submitInstallFromMiuix(String path, String packageName, String label,
            Drawable icon) {
        submitInstall(new ApkDetails(path, packageName, label, icon));
    }

    private void fail(String message) {
        showResult("无法安装", message);
    }

    private void showResult(String title, String message) {
        runOnUiThread(() -> {
            if (!destroyed && !isFinishing()) {
                MiuixPages.renderInstallerResult(this, title, message);
            }
        });
    }

    private static final class ApkDetails {
        final String path;
        final String packageName;
        final String label;
        final Drawable icon;

        ApkDetails(String path, String packageName, String label, Drawable icon) {
            this.path = path;
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }
}
