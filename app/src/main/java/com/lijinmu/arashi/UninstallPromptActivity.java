package com.lijinmu.arashi;

import com.lijinmu.arashi.ui.MiuixPages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.lijinmu.arashi.uninstall.UninstallScreenState;
import android.os.Bundle;
import android.os.ResultReceiver;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import java.util.concurrent.atomic.AtomicBoolean;

public final class UninstallPromptActivity extends ComponentActivity {
    private final AtomicBoolean decided = new AtomicBoolean(false);
    private ResultReceiver receiver;
    private boolean screenReceiverRegistered;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                sendDecision(UninstallScreenState.SCREEN_OFF_RESULT);
            }
        }
    };

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
        if (!getSharedPreferences(com.lijinmu.arashi.uninstall.UninstallConfig.PREF_NAME, MODE_PRIVATE)
                .getBoolean(com.lijinmu.arashi.uninstall.UninstallConfig.ENABLED, false)) {
            sendDecision(1);
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        screenReceiverRegistered = true;
        // Register before checking to cover a screen-off during activity startup.
        if (UninstallScreenState.isScreenOff(this)) {
            sendDecision(UninstallScreenState.SCREEN_OFF_RESULT);
            return;
        }
        setShowWhenLocked(true);
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
        sendDecision(UninstallScreenState.isScreenOff(this)
                ? UninstallScreenState.SCREEN_OFF_RESULT : allow ? 1 : 0);
    }

    public void timeout() {
        sendDecision(UninstallScreenState.isScreenOff(this)
                ? UninstallScreenState.SCREEN_OFF_RESULT : 2);
    }

    private void sendDecision(int result) {
        if (decided.compareAndSet(false, true)) {
            receiver.send(result, Bundle.EMPTY);
        }
        finish();
    }

    @Override protected void onDestroy() {
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        if (receiver != null && decided.compareAndSet(false, true)) {
            receiver.send(3, Bundle.EMPTY);
        }
        super.onDestroy();
    }
}
