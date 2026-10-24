package com.arashi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;


final class Ui {
    private Ui() {}
    static final int LAVENDER = Color.rgb(203, 182, 244);
    static final int PURPLE = Color.rgb(107, 74, 168);
    static final int WHITE = Color.WHITE;
    static final int DARK_BG = Color.rgb(21, 18, 29);
    static final int DARK_SURFACE = Color.rgb(37, 32, 48);
    static final int DARK_TEXT = Color.rgb(246, 240, 255);
    static final int DARK_MUTED = Color.rgb(192, 180, 207);
    static final int STATUS_GREEN = Color.rgb(34, 150, 83);
    static final int STATUS_RED = Color.rgb(205, 48, 48);

    static int dp(Activity a, float value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    static boolean dark(Activity a) {
        int mode = a.getSharedPreferences("ui", Activity.MODE_PRIVATE).getInt("mode", 0);
        if (mode == 1) return false;
        if (mode == 2) return true;
        return (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int background(Activity a) { return dark(a) ? DARK_BG : Color.rgb(249, 247, 253); }
    static int surface(Activity a) { return dark(a) ? DARK_SURFACE : WHITE; }
    static int text(Activity a) { return dark(a) ? DARK_TEXT : Color.rgb(40, 33, 51); }
    static int secondary(Activity a) { return dark(a) ? DARK_MUTED : Color.rgb(105, 95, 117); }
    static int soft(Activity a) { return dark(a) ? Color.rgb(58, 47, 75) : Color.rgb(241, 233, 253); }
    static int accent(Activity a) { return dark(a) ? LAVENDER : PURPLE; }

    static GradientDrawable shape(Activity a, int color, float radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(a, radius));
        return bg;
    }

    static void touch(Activity a, View v, int color, float radius) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(dark(a)
                ? 0x55E9D9FF : 0x449A75D4), shape(a, color, radius), null));
    }

    static TextView label(Activity a, String value, int sp, int color, boolean bold) {
        TextView text = new TextView(a);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setIncludeFontPadding(true);
        return text;
    }

    static LinearLayout column(Activity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        return col;
    }

    static LinearLayout row(Activity a) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    static void section(Activity a, LinearLayout parent, String heading) {
        TextView title = label(a, heading, 16, text(a), true);
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.topMargin = dp(a, 28);
        p.bottomMargin = dp(a, 12);
        parent.addView(title, p);
    }

    static void hint(Activity a, LinearLayout parent, String message) {
        TextView v = label(a, message, 13, secondary(a), false);
        v.setLineSpacing(dp(a, 3), 1f);
        parent.addView(v, lp(-1, -2));
    }

    static LinearLayout card(Activity a, LinearLayout parent, int color) {
        LinearLayout c = column(a);
        c.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 18));
        c.setBackground(shape(a, color, 24));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.bottomMargin = dp(a, 12);
        parent.addView(c, p);
        return c;
    }

    static void link(Activity a, LinearLayout parent, String title, String subtitle, Runnable action) {
        LinearLayout c = card(a, parent, surface(a));
        touch(a, c, surface(a), 24);
        c.setMinimumHeight(dp(a, 76));
        c.setClickable(true);
        c.setFocusable(true);
        LinearLayout line = row(a);
        c.addView(line, lp(-1, -2));
        LinearLayout words = column(a);
        line.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        words.addView(label(a, title, 17, text(a), true));
        if (subtitle != null) {
            TextView note = label(a, subtitle, 13, secondary(a), false);
            LinearLayout.LayoutParams np = lp(-1, -2);
            np.topMargin = dp(a, 5);
            words.addView(note, np);
        }
        TextView arrow = label(a, "›", 30, secondary(a), false);
        arrow.setPadding(dp(a, 12), 0, 0, 0);
        line.addView(arrow);
        c.setOnClickListener(v -> action.run());
    }

    static void realSwitch(Activity a, LinearLayout parent, String title, String subtitle, 
                           String key, android.content.SharedPreferences prefs) {
        LinearLayout c = card(a, parent, surface(a));
        c.setMinimumHeight(dp(a, 80));
        LinearLayout line = row(a);
        c.addView(line, lp(-1, -2));
        LinearLayout words = column(a);
        line.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        words.addView(label(a, title, 16, text(a), true));
        TextView note = label(a, subtitle, 13, secondary(a), false);
        LinearLayout.LayoutParams np = lp(-1, -2);
        np.topMargin = dp(a, 5);
        words.addView(note, np);
        ModernSwitch toggle = new ModernSwitch(a);
        toggle.setContentDescription(title);
        toggle.setChecked(prefs.getBoolean(key, false));
        line.addView(toggle);
        toggle.setOnCheckedChangeListener(checked -> {
            prefs.edit().putBoolean(key, checked).apply();
            
            try {
                java.io.File prefsFile = new java.io.File(a.getApplicationInfo().dataDir 
                        + "/shared_prefs/" + com.arashi.xposed.HookConfig.PREF_NAME + ".xml");
                prefsFile.setReadable(true, false);
            } catch (Throwable ignored) {
            }
        });
    }

    static void dialog(Activity a, String title, String message, String button) {
        LinearLayout content = column(a);
        content.setPadding(dp(a, 24), dp(a, 26), dp(a, 24), dp(a, 20));
        content.setBackground(shape(a, surface(a), 28));
        content.addView(label(a, title, 21, text(a), true));
        TextView body = label(a, message, 15, secondary(a), false);
        body.setLineSpacing(dp(a, 4), 1f);
        LinearLayout.LayoutParams bp = lp(-1, -2);
        bp.topMargin = dp(a, 14);
        content.addView(body, bp);
        TextView ok = label(a, button, 15, WHITE, true);
        ok.setGravity(Gravity.CENTER);
        ok.setMinimumHeight(dp(a, 48));
        touch(a, ok, PURPLE, 16);
        LinearLayout.LayoutParams op = lp(-1, dp(a, 48));
        op.topMargin = dp(a, 24);
        content.addView(ok, op);
        AlertDialog d = new AlertDialog.Builder(a).setView(content).create();
        ok.setOnClickListener(v -> d.dismiss());
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(a.getResources().getDisplayMetrics().widthPixels - dp(a, 48),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
