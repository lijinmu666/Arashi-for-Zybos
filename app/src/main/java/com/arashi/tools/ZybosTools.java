package com.arashi.tools;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;
import com.arashi.ui.Ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.system.Os;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.arashi.zybos.ZybosService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class ZybosTools extends BasePage {

    private static final int REQ_PICK_APK = 1001;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> uninstallPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    String pkg = data.getStringExtra(
                            ZybosUninstallPickerActivity.EXTRA_PACKAGE_NAME);
                    if (pkg != null && !pkg.isEmpty()) {
                        uninstallApk(pkg);
                    }
                }
            });

    @Override protected void populate() {
        if (com.arashi.zybos.SystemToolSupport.isBlocked()) {
            MiuixPages.renderSystemToolsUnavailable(this);
            return;
        }
        MiuixPages.renderZybosTools(this);

}

public void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), REQ_PICK_APK);
        } catch (Throwable t) {
            MiuixPages.showMessage(this, "无法打开文件选择器", String.valueOf(t.getMessage()));
        }
    }

    public void pickUninstallApp() {
        uninstallPicker.launch(new Intent(this, ZybosUninstallPickerActivity.class));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_APK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
            
        }
        new Thread(() -> {
            String path = resolvePath(uri);
            if (path == null) {
                path = resolveDescriptorPath(uri);
            }
            if (path == null || path.isEmpty()) {
                showResult("无法解析路径", "此文件来源只提供 content:// 地址，解析真实路径时出错；请在本机文件管理器中选择共享存储里的 APK，或使用「输入 APK 路径安装」");
            } else {
                installApk(path, uri, null);
            }
        }, "Arashi-ApkPath").start();
    }

    private String resolveDescriptorPath(Uri uri) {
        if (!"content".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        try (ParcelFileDescriptor descriptor =
                     getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                return null;
            }
            String linked = Os.readlink("/proc/self/fd/" + descriptor.getFd());
            return linked.startsWith("/") ? new File(linked).getCanonicalPath() : null;
        } catch (Exception error) {
            return null;
        }
    }

private String resolvePath(Uri uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                return path != null && path.startsWith("/") ? path : null;
            }
            if (!"content".equalsIgnoreCase(uri.getScheme())
                    || !DocumentsContract.isDocumentUri(this, uri)) return null;

            String id = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.downloads.documents".equals(uri.getAuthority())
                    && id.startsWith("raw:/storage/")) {
                String path = new File(id.substring(4)).getCanonicalPath();
                return path.startsWith("/storage/") ? path : null;
            }
            if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
            int colon = id.indexOf(':');
            if (colon < 0) return null;
            String volume = id.substring(0, colon);
            String relative = id.substring(colon + 1);
            if (relative.isEmpty() || relative.startsWith("/")) return null;

            File root = null;
            if ("primary".equalsIgnoreCase(volume)) {
                root = Environment.getExternalStorageDirectory();
            } else if (volume.matches("(?i)[0-9a-f]{4}-[0-9a-f]{4}")) {
                root = new File("/storage", volume);
            }
            if (root == null) return null;
            String base = root.getCanonicalPath();
            String path = new File(root, relative).getCanonicalPath();
            if (!path.startsWith(base + File.separator)) return null;

return path;
        } catch (Throwable t) {
            return null;
        }
    }

    public void installApk(String path, String packageName) {
        installApk(path, null, packageName);
    }

    private void installApk(String path, Uri sourceUri, String packageName) {
        new Thread(() -> {
            if (path == null || path.trim().isEmpty()) {
                showResult("路径无效", "请输入要安装的 APK 路径");
                return;
            }
            if (packageName != null && !packageName.isEmpty()
                    && !packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
                showResult("包名无效", "请输入正确的应用包名");
                return;
            }
            showResult("正在安装", "正在处理 " + path + "，请稍候");
            if (!ZybosService.detect(this)) {
                showResult("不可用", "未检测到 ZybosSysApiManager");
                return;
            }
            String parsedPackage = resolvePackageName(path, sourceUri);
            if (parsedPackage != null && packageName != null && !packageName.isEmpty()
                    && !parsedPackage.equals(packageName)) {
                showResult("包名不一致", "APK 中的包名是 " + parsedPackage + "，与填写的包名不同");
                return;
            }
            String pkg = parsedPackage != null ? parsedPackage : packageName;
            PackageInfo before = pkg == null ? null : installedPackageInfo(pkg);
            boolean isSdcard = path.startsWith("/sdcard/")
                    || path.startsWith("/storage/");
            if (!ZybosService.silentInstallApk(path, isSdcard, pkg)) {
                showResult("请求发送失败", "调用 silentInstallApk 未成功下发"
                        + "\n" + ZybosService.getLastError());
                return;
            }
            if (pkg == null) {
                showResult("请求已发送", sourceUri == null
                        ? "无法读取 APK 包名，因此无法核对安装结果；可以改用文件选择器，或在输入路径时填写包名"
                        : "仍无法从所选文件解析包名，因此无法核对安装结果；请确认所选文件是有效 APK");
                return;
            }
            PackageInfo installed = installedPackageInfo(pkg);
            for (int attempt = 0; !installationChanged(before, installed) && attempt < 60; attempt++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
                installed = installedPackageInfo(pkg);
            }
            if (installationChanged(before, installed)) {
                showResult("安装成功", pkg + " 已安装到当前用户");
            } else if (before != null && installed != null) {
                showResult("暂未确认安装", pkg + " 原本已安装，尚未检测到版本或更新时间变化");
            } else {
                showResult("未检测到安装", "请求已发送，但尚未检测到 " + pkg + " 安装到当前用户");
            }
        }, "Arashi-Install").start();
    }

    private PackageInfo installedPackageInfo(String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return null;
        }
    }

    private boolean installationChanged(PackageInfo before, PackageInfo installed) {
        return installed != null && (before == null
                || installed.getLongVersionCode() != before.getLongVersionCode()
                || installed.lastUpdateTime != before.lastUpdateTime);
    }

    private void showResult(String title, String message) {
        ui.post(() -> {
            if (!isFinishing() && !isDestroyed()) MiuixPages.showMessage(this, title, message);
        });
    }

private String archivePackageName(String apkPath) {
        try {
            PackageInfo pi = getPackageManager().getPackageArchiveInfo(apkPath, 0);
            if (pi != null) return pi.packageName;
        } catch (Throwable ignored) { }
        return null;
    }

    private String resolvePackageName(String apkPath, Uri sourceUri) {
        String packageName = archivePackageName(apkPath);
        if (packageName != null || sourceUri == null) {
            return packageName;
        }
        try (ParcelFileDescriptor descriptor =
                     getContentResolver().openFileDescriptor(sourceUri, "r")) {
            if (descriptor != null) {
                packageName = archivePackageName("/proc/self/fd/" + descriptor.getFd());
                if (packageName != null) {
                    return packageName;
                }
            }
        } catch (Exception ignored) { }
        File temporary = null;
        try {
            temporary = File.createTempFile("arashi-apk-", ".apk", getCacheDir());
            try (InputStream input = getContentResolver().openInputStream(sourceUri);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) {
                    return null;
                }
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            return archivePackageName(temporary.getAbsolutePath());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (temporary != null) {
                temporary.delete();
            }
        }
    }

public void uninstallApk(String pkg) {
        new Thread(() -> {
            if (pkg == null || pkg.trim().isEmpty()) {
                showResult("包名无效", "请输入要卸载的应用包名");
                return;
            }
            showResult("正在卸载", "正在处理 " + pkg + "，请稍候");
            if (!ZybosService.detect(this)) {
                showResult("不可用", "未检测到 ZybosSysApiManager，本设备可能不是 AibangOS");
                return;
            }
            Boolean wasPresent = isPackagePresent(pkg);
            if (Boolean.FALSE.equals(wasPresent)) {
                showResult("未找到应用", pkg + " 未安装在当前用户中");
                return;
            }
            PackageInfo before = Boolean.TRUE.equals(wasPresent)
                    ? installedPackageInfo(pkg) : null;
            if (!ZybosService.silentUninstallApk(pkg, false)) {
                showResult("请求发送失败", "调用 silentUninstallApk 未成功下发"
                        + "\n" + ZybosService.getLastError());
                return;
            }
            Boolean present = isPackagePresent(pkg);
            PackageInfo installed = Boolean.TRUE.equals(present)
                    ? installedPackageInfo(pkg) : null;
            for (int attempt = 0; Boolean.TRUE.equals(present)
                    && !updatedSystemAppRemoved(before, installed) && attempt < 20; attempt++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    break;
                }
                present = isPackagePresent(pkg);
                installed = Boolean.TRUE.equals(present) ? installedPackageInfo(pkg) : null;
            }
            if (Boolean.FALSE.equals(present)) {
                showResult("卸载成功", pkg + " 已从当前用户中移除");
            } else if (updatedSystemAppRemoved(before, installed)) {
                showResult("卸载更新成功", pkg + " 已恢复到系统预装版本");
            } else if (Boolean.TRUE.equals(present)) {
                showResult("未检测到卸载", pkg + " 仍在当前用户的应用列表中，系统未完成卸载");
            } else {
                showResult("请求已发送", "无法核对 " + pkg + " 的卸载结果");
            }
        }, "Arashi-Uninstall").start();
    }

    private boolean updatedSystemAppRemoved(PackageInfo before, PackageInfo installed) {
        return before != null && before.applicationInfo != null
                && (before.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                && installed != null && installed.applicationInfo != null
                && (installed.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
    }

    private Boolean isPackagePresent(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        } catch (RuntimeException error) {
            return null;
        }
    }

private void prompt(String title, String hint, String preset, final Callback callback) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 18));
        box.setBackground(Ui.shape(this, Ui.surface(this), 24));
        box.addView(Ui.label(this, title, 20, Ui.text(this), true));
        final EditText input = new EditText(this);
        input.setText(preset);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(Ui.text(this));
        input.setHintTextColor(Ui.secondary(this));
        LinearLayout.LayoutParams ip = Ui.lp(-1, -2);
        ip.topMargin = Ui.dp(this, 14);
        box.addView(input, ip);
        TextView ok = Ui.label(this, "确定", 15, Ui.WHITE, true);
        ok.setGravity(Gravity.CENTER);
        ok.setMinimumHeight(Ui.dp(this, 48));
        Ui.touch(this, ok, Ui.PURPLE, 14);
        LinearLayout.LayoutParams op = Ui.lp(-1, Ui.dp(this, 48));
        op.topMargin = Ui.dp(this, 22);
        box.addView(ok, op);
        final AlertDialog d = new AlertDialog.Builder(this).setView(box).create();
        ok.setOnClickListener(v -> {
            d.dismiss();
            callback.onInput(input.getText().toString().trim());
        });
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 48),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private interface Callback { void onInput(String value); }
}
