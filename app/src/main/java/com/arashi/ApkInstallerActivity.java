package com.arashi;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.arashi.zybos.ZybosService;

import java.io.File;

public final class ApkInstallerActivity extends Activity {
    private static final String TAG = "ArashiInstaller";
    private static final int REQ_ALL_FILES = 3001;

    private Uri pendingUri;

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
            fail("未授予所有文件访问权限，无法读取 APK 信息。");
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        inspect(getIncomingUri(intent));
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
        if (!Environment.isExternalStorageManager()) {
            requestAllFilesAccess(uri);
            return;
        }
        new Thread(() -> {
            String path = resolvePath(uri);
            if (path == null) {
                fail("无法取得 APK 的真实路径，此文件来源未提供可供系统安装服务读取的路径。");
                return;
            }
            ApkDetails apk = readApk(uri, path);
            if (apk == null) {
                fail("无法读取 APK 信息，请确认选择的是有效 APK 文件。");
                return;
            }
            Log.i(TAG, "APK path=" + path + ", package=" + apk.packageName);
            showConfirmation(apk);
        }, "Arashi-Installer-Inspect").start();
    }

    private void submitInstall(ApkDetails apk) {
        new Thread(() -> {
            if (!ZybosService.detect(this)) {
                fail("未检测到 ZybosSysApiManager");
                return;
            }
            boolean isSdcard = apk.path.startsWith("/sdcard/")
                    || apk.path.startsWith("/storage/emulated/");
            Log.i(TAG, "submit path=" + apk.path + ", isSdcard=" + isSdcard);
            if (ZybosService.silentInstallApk(apk.path, isSdcard, apk.packageName)) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "安装请求已发送", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                fail("安装请求发送失败：" + ZybosService.getLastError());
            }
        }, "Arashi-Installer-Submit").start();
    }

    private void requestAllFilesAccess(Uri uri) {
        runOnUiThread(() -> {
            pendingUri = uri;
            try {
                Intent settings = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(settings, REQ_ALL_FILES);
            } catch (Throwable ignored) {
                fail("无法打开所有文件访问权限设置页。");
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
                    && id.startsWith("raw:/storage/")) {
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
        path = path.replaceFirst("^/mnt/user/\\d+/emulated/", "/storage/emulated/");
        path = path.replaceFirst("^/mnt/runtime/[^/]+/emulated/", "/storage/emulated/");
        try {
            path = new File(path).getCanonicalPath();
        } catch (Throwable ignored) {
            return null;
        }
        return path.startsWith("/storage/") || path.startsWith("/sdcard/") ? path : null;
    }

    private ApkDetails readApk(Uri uri, String path) {
        ApkDetails apk = readApk(path, path);
        if (apk != null || !"content".equalsIgnoreCase(uri.getScheme())) return apk;
        try (ParcelFileDescriptor descriptor =
                     getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) return null;
            return readApk("/proc/self/fd/" + descriptor.getFd(), path);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ApkDetails readApk(String archivePath, String shownPath) {
        try {
            PackageManager manager = getPackageManager();
            PackageInfo info = manager.getPackageArchiveInfo(
                    archivePath, PackageManager.GET_META_DATA);
            if (info == null || info.applicationInfo == null || info.packageName == null) return null;
            ApplicationInfo app = info.applicationInfo;
            app.sourceDir = archivePath;
            app.publicSourceDir = archivePath;
            CharSequence label = app.loadLabel(manager);
            Drawable icon = app.loadIcon(manager);
            return new ApkDetails(shownPath, info.packageName,
                    label == null || label.length() == 0 ? info.packageName : label.toString(), icon);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void showConfirmation(ApkDetails apk) {
        runOnUiThread(() -> {
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            int padding = Ui.dp(this, 22);
            content.setPadding(padding, Ui.dp(this, 22), padding, Ui.dp(this, 16));
            content.setBackground(Ui.shape(this, Ui.DARK_SURFACE, 28));

            TextView title = new TextView(this);
            title.setText("确认安装");
            title.setTextSize(21);
            title.setTextColor(Ui.DARK_TEXT);
            title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            content.addView(title);

            LinearLayout header = new LinearLayout(this);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(apk.icon);
            header.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
            TextView name = new TextView(this);
            name.setText(apk.label);
            name.setTextSize(19);
            name.setTextColor(Ui.DARK_TEXT);
            name.setPadding(Ui.dp(this, 16), 0, 0, 0);
            header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
            headerParams.topMargin = Ui.dp(this, 20);
            content.addView(header, headerParams);

            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(android.view.Gravity.END);
            TextView cancel = new TextView(this);
            cancel.setText("取消");
            cancel.setTextSize(15);
            cancel.setTextColor(Ui.DARK_TEXT);
            cancel.setGravity(android.view.Gravity.CENTER);
            Ui.touch(this, cancel, Ui.DARK_SURFACE, 16);
            actions.addView(cancel, new LinearLayout.LayoutParams(Ui.dp(this, 88), Ui.dp(this, 48)));

            TextView confirm = new TextView(this);
            confirm.setText("确认安装");
            confirm.setTextSize(15);
            confirm.setTextColor(Ui.WHITE);
            confirm.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            confirm.setGravity(android.view.Gravity.CENTER);
            Ui.touch(this, confirm, Ui.PURPLE, 16);
            LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 48));
            confirmParams.leftMargin = Ui.dp(this, 8);
            actions.addView(confirm, confirmParams);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
            actionsParams.topMargin = Ui.dp(this, 24);
            content.addView(actions, actionsParams);

            AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
            cancel.setOnClickListener(v -> {
                dialog.dismiss();
                finish();
            });
            confirm.setOnClickListener(v -> {
                dialog.dismiss();
                submitInstall(apk);
            });
            dialog.setOnCancelListener(ignored -> finish());
            dialog.show();
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48),
                        android.view.WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
    }

    private void fail(String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("无法安装")
                .setMessage(message)
                .setCancelable(true)
                .setOnCancelListener(dialog -> finish())
                .setPositiveButton("知道了", (dialog, which) -> finish())
                .show());
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
