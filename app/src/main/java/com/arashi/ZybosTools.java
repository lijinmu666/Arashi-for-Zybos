package com.arashi;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.arashi.zybos.ZybosService;

import java.io.File;





public final class ZybosTools extends BasePage {

    private static final int REQ_PICK_APK = 1001;

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override protected void populate() {
        toolbar("系统特权工具");
        banner("系统特权工具", "利用 ZybosSysApiManager 无需权限为本设备执行安装、卸载应用等操作");

        Ui.section(this, content, "安装");
        Ui.link(this, content, "选择 APK 文件安装", "从文件管理器中选择 APK 安装",
                this::pickApk);
        Ui.link(this, content, "输入 APK 路径安装", "输入 APK 的路径",
                () -> prompt("安装 APK", "/sdcard/Download/MyApp.apk", "", path -> {
                    if (path.isEmpty()) return;
                    installApk(path);
                }));

        Ui.section(this, content, "卸载");
        Ui.link(this, content, "卸载应用", "输入应用包名进行卸载",
                () -> prompt("卸载应用", "com.example.kskblzdjd", "", pkg -> {
                    if (pkg.isEmpty()) return;
                    uninstallApk(pkg);
                }));

        Ui.hint(this, content, "本功能仅面向支持 ZybosSysApiManager 的 AibangOS 版本");
    }

    

    private void pickApk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), REQ_PICK_APK);
        } catch (Throwable t) {
            Ui.dialog(this, "无法打开文件选择器", String.valueOf(t.getMessage()), "知道了");
        }
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
            if (path == null || path.isEmpty()) {
                showResult("无法解析路径", "此文件来源只提供 content:// 地址，解析真实路径时出错。请在本机文件管理器中选择共享存储里的 APK，或使用「输入 APK 路径安装");
            } else {
                installApk(path);
            }
        }, "Arashi-ApkPath").start();
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

    private void installApk(String path) {
        new Thread(() -> {
            if (!ZybosService.detect(this)) {
                showResult("不可用", "未检测到 ZybosSysApiManager");
                return;
            }
            String pkg = resolvePackageName(path);
            boolean isSdcard = path != null && (path.startsWith("/sdcard/")
                    || path.startsWith("/storage/emulated/"));
            if (ZybosService.silentInstallApk(path, isSdcard, pkg)) {
                showRequestSent();
            } else {
                showResult("请求发送失败", "调用 silentInstallApk 未成功下发。"
                        + "\n" + ZybosService.getLastError());
            }
        }, "Arashi-Install").start();
    }

    private void showRequestSent() {
        ui.post(() -> {
            if (!isFinishing() && !isDestroyed())
                android.widget.Toast.makeText(this, "请求已发送",
                        android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    private void showResult(String title, String message) {
        ui.post(() -> {
            if (!isFinishing() && !isDestroyed()) Ui.dialog(this, title, message, "知道了");
        });
    }

    
    private String resolvePackageName(String apkPath) {
        try {
            android.content.pm.PackageInfo pi =
                    getPackageManager().getPackageArchiveInfo(apkPath, 0);
            if (pi != null) return pi.packageName;
        } catch (Throwable ignored) { }
        return null;
    }

    

    private void uninstallApk(String pkg) {
        new Thread(() -> {
            if (!ZybosService.detect(this)) {
                showResult("不可用", "未检测到 ZybosSysApiManager，本设备可能不是 AibangOS。");
                return;
            }
            if (ZybosService.silentUninstallApk(pkg, false)) {
                showRequestSent();
            } else {
                showResult("请求发送失败", "调用 silentUninstallApk 未成功下发。"
                        + "\n" + ZybosService.getLastError());
            }
        }, "Arashi-Uninstall").start();
    }

    

    private void prompt(String title, String hint, String preset, final Callback callback) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 20));
        box.setBackground(Ui.shape(this, Ui.surface(this), 28));
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
        Ui.touch(this, ok, Ui.PURPLE, 16);
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
