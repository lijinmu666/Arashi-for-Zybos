package com.arashi;

import android.widget.LinearLayout;


public final class About extends BasePage {
    @Override protected void populate() {
        toolbar("关于 Arashi");

        LinearLayout version = Ui.card(this, content, Ui.surface(this));
        version.addView(Ui.label(this, "版本号", 15, Ui.secondary(this), false));
        LinearLayout.LayoutParams vp = Ui.lp(-1, -2);
        vp.topMargin = Ui.dp(this, 5);
        version.addView(Ui.label(this, BuildConfig.VERSION_NAME, 22, Ui.text(this), true), vp);

        LinearLayout author = Ui.card(this, content, Ui.surface(this));
        author.addView(Ui.label(this, "作者", 15, Ui.secondary(this), false));
        LinearLayout.LayoutParams ap = Ui.lp(-1, -2);
        ap.topMargin = Ui.dp(this, 5);
        author.addView(Ui.label(this, "LiJinMu & Codex", 20, Ui.text(this), true), ap);

        /*
        Ui.link(this, content, "QQ 群聊", "加入 Arashi 用户交流与反馈群", () ->
                Ui.dialog(this, "QQ 群聊", "群号：1234567890", "知道了"));
        Ui.link(this, content, "Telegram 频道", "查看更新消息和项目公告", () ->
                Ui.dialog(this, "Telegram 频道", "频道链接待公布", "知道了"));
        */
    }
}
