package com.arashi.ui;

import android.os.Bundle;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;

public abstract class BasePage extends ComponentActivity {
    protected LinearLayout content;
    private int shownThemeMode;
    private boolean shownDark;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        shownThemeMode = getSharedPreferences("ui", MODE_PRIVATE).getInt("mode", 0);
        shownDark = Ui.dark(this);
        getWindow().setStatusBarColor(Ui.background(this));
        getWindow().setNavigationBarColor(Ui.background(this));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Ui.background(this));
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 32));
        scroll.addView(content);
        setContentView(scroll);
        
        try {
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(Ui.dark(this) ? 0
                    : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } catch (RuntimeException ignored) {  }
        populate();
    }

    @Override protected void onResume() {
        super.onResume();
        int currentMode = getSharedPreferences("ui", MODE_PRIVATE).getInt("mode", 0);
        if (shownThemeMode != currentMode || shownDark != Ui.dark(this)) {
            recreate();
        }
    }

    protected abstract void populate();

    protected void toolbar(String title) {
        LinearLayout line = Ui.row(this);
        LinearLayout.LayoutParams p = Ui.lp(-1, Ui.dp(this, 52));
        p.bottomMargin = Ui.dp(this, 20);
        content.addView(line, p);
        MaterialBackArrow back = new MaterialBackArrow(this);
        back.setContentDescription("返回");
        Ui.touch(this, back, android.graphics.Color.TRANSPARENT, 26);
        line.addView(back, Ui.lp(Ui.dp(this, 52), -1));
        back.setOnClickListener(v -> finish());
        TextView name = Ui.label(this, title, 22, Ui.text(this), true);
        line.addView(name, Ui.lp(-2, -2));
    }

    protected void banner(String title, String body) {
        LinearLayout box = Ui.card(this, content, Ui.soft(this));
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        box.addView(Ui.label(this, title, 19, Ui.text(this), true));
        TextView description = Ui.label(this, body, 14, Ui.secondary(this), false);
        description.setLineSpacing(Ui.dp(this, 4), 1f);
        LinearLayout.LayoutParams p = Ui.lp(-1, -2);
        p.topMargin = Ui.dp(this, 10);
        box.addView(description, p);
    }
}
