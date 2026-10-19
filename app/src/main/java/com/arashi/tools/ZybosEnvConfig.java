package com.arashi.tools;

import org.json.JSONException;
import org.json.JSONObject;

/** Only values confirmed in the launcher's environment enum are accepted. */
public final class ZybosEnvConfig {
    public static final String PROPERTY = "persist.sys.env_config";
    public static final String DEFAULT_JSON = "{\"env\":\"PRODUCTION\",\"isShowDebugTools\":false,\"isTips\":false,\"tcpServerConfigs\":[]}";
    public final String json;
    public final String environment;
    public final boolean showDebugTools;
    public final boolean tips;

    private ZybosEnvConfig(String json, JSONObject value) throws JSONException {
        this.json = json;
        environment = value.getString("env");
        validateEnvironment(environment);
        showDebugTools = value.optBoolean("isShowDebugTools", false);
        tips = value.optBoolean("isTips", false);
    }

    public static ZybosEnvConfig parse(String raw) throws JSONException {
        String json = raw == null || raw.trim().isEmpty() ? DEFAULT_JSON : raw.trim();
        return new ZybosEnvConfig(json, new JSONObject(json));
    }

    public static String update(String original, String environment, boolean showTools, boolean tips)
            throws JSONException {
        validateEnvironment(environment);
        // Keep TCP endpoints and any fields added by another OS version.
        JSONObject value = new JSONObject(parse(original).json);
        value.put("env", environment);
        value.put("isShowDebugTools", showTools);
        value.put("isTips", tips);
        return value.toString();
    }

    private static void validateEnvironment(String value) {
        if (!"PRODUCTION".equals(value) && !"TEST".equals(value) && !"MOCK".equals(value)) {
            throw new IllegalArgumentException("无法识别的环境：" + value);
        }
    }

    public static String writeCommand(String json) throws JSONException {
        parse(json);
        return "setprop " + PROPERTY + " " + quote(json) + " && getprop " + PROPERTY;
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
