package com.lijinmu.arashi.tools;

import com.lijinmu.arashi.ui.BasePage;
import com.lijinmu.arashi.ui.MiuixPages;

/** Root calls are synchronous; the Compose page always invokes them on Dispatchers.IO. */
public final class ZybosEnvController extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderZybosEnvController(this);
    }

    private static String readProperty() {
        RootShell.Result result = RootShell.run("getprop " + ZybosEnvConfig.PROPERTY);
        if (!result.ok) throw new IllegalStateException(result.output);
        return result.output.trim();
    }

    public static ZybosEnvConfig readConfig() throws Exception {
        return ZybosEnvConfig.parse(readProperty());
    }

    public static String readFormattedProperty() {
        String raw = readProperty();
        if (raw.isEmpty()) return "属性为空";
        try {
            return new org.json.JSONObject(raw).toString(2);
        } catch (org.json.JSONException error) {
            return "当前属性不是有效的 JSON，原始内容如下\n\n" + raw;
        }
    }

    public static ZybosEnvConfig applyConfig(String original, String environment, boolean showTools, boolean tips)
            throws Exception {
        return write(ZybosEnvConfig.update(original, environment, showTools, tips));
    }

    public static ZybosEnvConfig resetConfig() throws Exception {
        return write(ZybosEnvConfig.DEFAULT_JSON);
    }

    private static ZybosEnvConfig write(String json) throws Exception {
        RootShell.Result result = RootShell.run(ZybosEnvConfig.writeCommand(json));
        if (!result.ok) throw new IllegalStateException(result.output);
        if (!json.equals(result.output.trim())) {
            throw new IllegalStateException("写入后的属性值与预期不一致，请重新读取确认");
        }
        return ZybosEnvConfig.parse(result.output);
    }
}
