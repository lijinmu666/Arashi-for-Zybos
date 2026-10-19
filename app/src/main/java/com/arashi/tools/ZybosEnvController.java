package com.arashi.tools;

import com.arashi.ui.BasePage;
import com.arashi.ui.MiuixPages;

/** Root calls are synchronous; the Compose page always invokes them on Dispatchers.IO. */
public final class ZybosEnvController extends BasePage {
    @Override protected void populate() {
        MiuixPages.renderZybosEnvController(this);
    }

    public ZybosEnvConfig readConfig() throws Exception {
        RootShell.Result result = RootShell.run("getprop " + ZybosEnvConfig.PROPERTY);
        if (!result.ok) throw new IllegalStateException(result.output);
        return ZybosEnvConfig.parse(result.output);
    }

    public ZybosEnvConfig applyConfig(String original, String environment, boolean showTools, boolean tips)
            throws Exception {
        return write(ZybosEnvConfig.update(original, environment, showTools, tips));
    }

    public ZybosEnvConfig resetConfig() throws Exception {
        return write(ZybosEnvConfig.DEFAULT_JSON);
    }

    private ZybosEnvConfig write(String json) throws Exception {
        RootShell.Result result = RootShell.run(ZybosEnvConfig.writeCommand(json));
        if (!result.ok) throw new IllegalStateException(result.output);
        if (!json.equals(result.output.trim())) {
            throw new IllegalStateException("写入后的属性值与预期不一致，请重新读取确认");
        }
        return ZybosEnvConfig.parse(result.output);
    }
}
