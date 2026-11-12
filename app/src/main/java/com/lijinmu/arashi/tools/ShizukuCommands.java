package com.lijinmu.arashi.tools;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

final class ShizukuCommands {
    private ShizukuCommands() {}

    static RootShell.Result run(Context context, String command) {
        if (!WorkMode.shellReady()) {
            return new RootShell.Result(false, "请启动 Shizuku 或 Sui，并在工作模式中授权");
        }
        var args = new Shizuku.UserServiceArgs(new ComponentName(context, ShellCommandService.class))
                .tag("arashi_commands")
                .processNameSuffix("commands")
                .version(com.lijinmu.arashi.BuildConfig.VERSION_CODE)
                .daemon(false);
        var connected = new CompletableFuture<IShellCommandService>();
        var connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                connected.complete(IShellCommandService.Stub.asInterface(binder));
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                connected.completeExceptionally(new IllegalStateException("Shell 服务已断开"));
            }
        };
        try {
            Shizuku.bindUserService(args, connection);
            String[] result = connected.get(10, TimeUnit.SECONDS).execute(command);
            return new RootShell.Result("0".equals(result[0]), result[1]);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return new RootShell.Result(false, "Shell 执行失败：" + error);
        } finally {
            try {
                Shizuku.unbindUserService(args, connection, false);
            } catch (RuntimeException ignored) {
                // The backend may have stopped while the command was running.
            }
        }
    }
}
