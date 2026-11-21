package com.lijinmu.arashi.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import com.lijinmu.arashi.R;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.activity.ComponentActivity;

public abstract class BasePage extends ComponentActivity {
    private int shownThemeMode;
    private boolean shownDark;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        com.lijinmu.arashi.logs.RuntimeLogger.info("Activity", "打开 " + getClass().getSimpleName());
        // Android 14+ owns cross-Activity predictive transitions. XML/legacy overrides
        // are retained only for older Android releases below.
        if (Build.VERSION.SDK_INT >= 34) {
            getWindow().setWindowAnimations(android.R.style.Animation_Activity);
        }
        shownThemeMode = getSharedPreferences("ui", MODE_PRIVATE).getInt("mode", 0);
        shownDark = Ui.dark(this);
        // Compose owns inset avoidance; its root overlay must also cover the system bars.
        // Do this before installing content so the first layout already receives full insets.
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setStatusBarContrastEnforced(false);
        getWindow().setNavigationBarContrastEnforced(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Ui.background(this));
        LinearLayout content = Ui.column(this);
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

    @SuppressWarnings("deprecation")
    @Override public void startActivity(Intent intent, Bundle options) {
        super.startActivity(intent, options);
        // Legacy overrides must run immediately after the actual launch.
        // Leave external apps and the floating uninstall prompt to their own transitions.
        android.content.ComponentName target = intent.getComponent();
        if (Build.VERSION.SDK_INT < 34 && target != null
                && getPackageName().equals(target.getPackageName())
                && !com.lijinmu.arashi.UninstallPromptActivity.class.getName().equals(target.getClassName())) {
            overridePendingTransition(R.anim.page_enter_right, R.anim.page_exit_left);
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void finish() {
        super.finish();
        if (Build.VERSION.SDK_INT < 34 && !(this instanceof com.lijinmu.arashi.Main)) {
            overridePendingTransition(R.anim.page_enter_left, R.anim.page_exit_right);
        }
    }

    protected abstract void populate();
}
