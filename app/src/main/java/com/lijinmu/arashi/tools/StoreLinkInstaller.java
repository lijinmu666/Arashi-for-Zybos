package com.lijinmu.arashi.tools;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;
import org.json.JSONObject;

/** Sends the app store's remote-install request; does not claim installation completion. */
public final class StoreLinkInstaller extends BasePage {
    public static final class LocalApk {
        public final String path, packageName, name, md5, size, version, versionCode;
        LocalApk(String p, String pkg, String n, String hash, String bytes, String v, String code) {
            path=p; packageName=pkg; name=n; md5=hash; size=bytes; version=v; versionCode=code;
        }
    }
    public interface Prepared { void complete(LocalApk apk, String error); }
    public void prepareLocal(Uri uri, Prepared callback) {
        InstallDiagnostics.reset(this);
        InstallDiagnostics.record(this,"开始读取本地 APK",null);
        new Thread(() -> {
            java.io.File file = new java.io.File(getCacheDir(), "store-" + java.util.UUID.randomUUID() + ".apk");
            try {
                java.io.File[] old = getCacheDir().listFiles((dir,n) -> n.startsWith("store-") && n.endsWith(".apk"));
                if (old != null) for (java.io.File cached : old)
                    if (System.currentTimeMillis() - cached.lastModified() > 86400000L) cached.delete();
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
                try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                     java.io.OutputStream out = new java.io.FileOutputStream(file)) {
                    if (in == null) throw new java.io.IOException("无法读取 APK");
                    byte[] buffer = new byte[65536]; int count;
                    while ((count=in.read(buffer)) != -1) {
                        if (file.length() + count > Integer.MAX_VALUE) throw new java.io.IOException("APK 过大");
                        out.write(buffer,0,count); digest.update(buffer,0,count);
                    }
                }
                android.content.pm.PackageInfo info = getPackageManager().getPackageArchiveInfo(file.getPath(),0);
                if (info == null || info.applicationInfo == null) throw new java.io.IOException("无法解析 APK");
                info.applicationInfo.sourceDir=file.getPath(); info.applicationInfo.publicSourceDir=file.getPath();
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
                LocalApk apk = new LocalApk(file.getPath(),info.packageName,
                    info.applicationInfo.loadLabel(getPackageManager()).toString(), hash.toString(),
                    Long.toString(file.length()), info.versionName == null ? "1.0" : info.versionName,
                    Long.toString(android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode));
                InstallDiagnostics.record(this,"APK 读取完成；大小："+apk.size+" 字节",null);
                runOnUiThread(() -> callback.complete(apk,null));
            } catch (Exception e) {
                InstallDiagnostics.record(this,"读取 APK 失败",e);
                file.delete(); runOnUiThread(() -> callback.complete(null,e.getMessage()));
            }
        }, "StoreApkReader").start();
    }
    public void stopLocalServer() { stopService(new Intent(this, LocalApkServer.class)); }
    private String localPath;
    public void sendLocal(LocalApk apk, String appId, String icon) {
        localPath=apk.path;
        try { sendInstall(apk.packageName,apk.name,"https://127.0.0.1/local.apk",apk.md5,apk.size,apk.version,apk.versionCode,appId,icon); }
        finally { localPath=null; }
    }
    private static final String STORE = "com.zuoyebang.iot.pad.appstore";
    @Override protected void populate() { MiuixPages.renderStoreLinkInstaller(this); }

    private static String url(String value, boolean optional) {
        value = value.trim();
        if (optional && value.isEmpty()) return "";
        Uri uri = Uri.parse(value);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getHost().isEmpty())
            throw new IllegalArgumentException("请填写有效的 HTTP/HTTPS 直链");
        return value;
    }

    public void sendInstall(String packageName, String name, String apkUrl, String md5,
            String size, String version, String versionCode, String appId, String icon) {
        InstallDiagnostics.reset(this);
        InstallDiagnostics.record(this,localPath == null ? "开始校验直接安装参数" : "开始校验本地安装参数",null);
        try {
            packageName = packageName.trim();
            if (!packageName.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
                throw new IllegalArgumentException("请填写有效包名");
            if (!md5.trim().matches("[a-fA-F0-9]{32}"))
                throw new IllegalArgumentException("MD5 必须为 32 位十六进制字符");
            if (name.trim().isEmpty() || version.trim().isEmpty())
                throw new IllegalArgumentException("请填写应用名称和版本名称");
            int bytes = Integer.parseInt(size.trim());
            int code = Integer.parseInt(versionCode.trim());
            int id = appId.trim().isEmpty() ? 100 : Integer.parseInt(appId.trim());
            if (bytes <= 0 || code <= 0 || id <= 0)
                throw new IllegalArgumentException("文件大小、版本号和应用 ID 必须为正整数");
            getPackageManager().getApplicationInfo(STORE, 0);
            JSONObject data = new JSONObject();
            data.put("id", id).put("apkName", packageName).put("name", name.trim())
                .put("apkUrl", url(apkUrl, false)).put("apkMd5", md5.trim())
                .put("apkSize", bytes).put("apkVersion", version.trim()).put("apkVersionCode", code)
                .put("icon", url(icon.trim().isEmpty() ? "https://www.mcmsf.com/upload/fwq/69/cover.png" : icon, true)).put("changeLog", "").put("developer", "")
                .put("enName", "").put("remark", "").put("summary", "").put("statusInPad", 0)
                .put("isSensitive", 0).put("uploadTime", 0).put("from", 0).put("remoteInstallMsg", "");
            JSONObject request = new JSONObject().put("type", 2).put("sub_type", 3)
                .put("msg_id", "arashi-" + java.util.UUID.randomUUID()).put("pkg_name", packageName)
                .put("data", data);
            Intent intent = new Intent("com.zuoyebang.iot.push");
            intent.setComponent(new ComponentName(STORE, "com.zuoyebang.iot.pad.push.PushMessageReceiver"));
            intent.putExtra("response", request.toString());
            if (localPath != null) {
                Intent service = new Intent(this, LocalApkServer.class)
                    .putExtra("path",localPath).putExtra("request",request.toString());
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
                MiuixPages.showMessage(this,"","正在启动本机 HTTPS 服务；启动后发送安装广播，请查看服务通知");
                return;
            }
            InstallDiagnostics.record(this,"准备发送直接安装广播；匹配接收器数量："+getPackageManager().queryBroadcastReceivers(intent,0).size(),null);
            sendBroadcast(intent);
            InstallDiagnostics.record(this,"直接安装广播已提交系统，尚未确认商店接收",null);
            MiuixPages.showMessage(this, "", "安装广播已发送，请到应用商店查看进度；不代表安装成功");
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            InstallDiagnostics.record(this,"安装请求失败",error);
            MiuixPages.showMessage(this, "", "未找到作业帮应用商店");
        } catch (NumberFormatException error) {
            InstallDiagnostics.record(this,"安装请求失败",error);
            MiuixPages.showMessage(this, "", "文件大小、版本号和应用 ID 需为有效正整数（不超过 2147483647）");
        } catch (Exception error) {
            InstallDiagnostics.record(this,"安装请求失败",error);
            MiuixPages.showMessage(this, "无法发送安装请求", error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
}
