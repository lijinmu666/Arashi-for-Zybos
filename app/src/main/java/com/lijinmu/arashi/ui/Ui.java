package com.lijinmu.arashi.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.widget.LinearLayout;

public final class Ui {
    private Ui() {}

    private static final int DARK_BG = Color.rgb(18, 18, 18);

    public static int dp(Activity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    static boolean dark(Activity a) {
        int mode = a.getSharedPreferences("ui", Activity.MODE_PRIVATE).getInt("mode", 0);
        if (mode == 1) return false;
        if (mode == 2) return true;
        return (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int background(Activity a) { return dark(a) ? DARK_BG : Color.rgb(247, 247, 247); }
    public static LinearLayout column(Activity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }
}
