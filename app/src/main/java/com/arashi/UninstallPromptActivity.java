package com.arashi;

import com.arashi.ui.MiuixPages;

import android.os.Bundle;
import android.os.ResultReceiver;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UninstallPromptActivity extends ComponentActivity {
    private final AtomicBoolean decided = new AtomicBoolean(false);
    private ResultReceiver receiver;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            receiver = getIntent().getParcelableExtra("decision_receiver", ResultReceiver.class);
        } else {
            receiver = getIntent().getParcelableExtra("decision_receiver");
        }
        if (receiver == null) {
            finish();
            return;
        }
        // A queued request may arrive after the user has switched validation off.
        if (!getSharedPreferences(com.arashi.uninstall.UninstallConfig.PREF_NAME, MODE_PRIVATE)
                .getBoolean(com.arashi.uninstall.UninstallConfig.ENABLED, false)) {
            decide(true);
            return;
        }
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                decide(false);
            }
        });
        MiuixPages.renderUninstallPrompt(this,
                getIntent().getStringExtra("target"),
                getIntent().getIntExtra("caller_uid", -1),
                getIntent().getStringArrayExtra("caller_packages"),
                getIntent().getLongExtra("deadline_elapsed", android.os.SystemClock.elapsedRealtime()));
    }

    public void decide(boolean allow) {
        sendDecision(allow ? 1 : 0);
    }

    public void timeout() {
        sendDecision(2);
    }

    private void sendDecision(int result) {
        if (decided.compareAndSet(false, true)) {
            receiver.send(result, Bundle.EMPTY);
        }
        finish();
    }

    @Override protected void onDestroy() {
        if (receiver != null && decided.compareAndSet(false, true)) {
            receiver.send(3, Bundle.EMPTY);
        }
        super.onDestroy();
    }
}
