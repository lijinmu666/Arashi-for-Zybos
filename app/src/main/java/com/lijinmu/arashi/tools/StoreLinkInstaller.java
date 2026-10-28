package com.lijinmu.arashi.tools;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;
import org.json.JSONObject;

/** Sends the app store's remote-install request; does not claim installation completion. */
public final class StoreLinkInstaller extends BasePage {
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
            int id = Integer.parseInt(appId.trim());
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
            sendBroadcast(intent);
            MiuixPages.showMessage(this, "", "安装广播已发送，请到应用商店查看进度；不代表安装成功");
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            MiuixPages.showMessage(this, "", "未找到作业帮应用商店");
        } catch (NumberFormatException error) {
            MiuixPages.showMessage(this, "", "文件大小、版本号和应用 ID 需为有效正整数（不超过 2147483647）");
        } catch (Exception error) {
            MiuixPages.showMessage(this, "无法发送安装请求", error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }
}
