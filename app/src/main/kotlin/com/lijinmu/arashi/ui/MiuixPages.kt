package com.lijinmu.arashi.ui

import com.lijinmu.arashi.BuildConfig
import com.lijinmu.arashi.Main
import com.lijinmu.arashi.UninstallPromptActivity
import com.lijinmu.arashi.settings.HookSettings
import com.lijinmu.arashi.settings.BackgroundWhitelistActivity
import com.lijinmu.arashi.xposed.BackgroundPolicy
import com.lijinmu.arashi.tools.QuickCommandController
import com.lijinmu.arashi.tools.ScopedAppStopper
import com.lijinmu.arashi.tools.WorkMode
import rikka.shizuku.Shizuku
import com.lijinmu.arashi.tcp.TcpHistoryActivity
import com.lijinmu.arashi.uninstall.UninstallHistoryActivity
import com.lijinmu.arashi.uninstall.UninstallListActivity
import com.lijinmu.arashi.uninstall.UninstallSettings

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowInsetsController
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment…34243 tokens truncated…00启时生效") {
                openToggle("息屏关闭 WLAN", "zyb_power_screen_off_wifi")
            }
            QuickCommandItem("三指下拉截屏", "是否开启三指下拉快捷截屏功能") {
                openToggle("三指下拉截屏", "zyb_three_pointer_touch_screenshot")
            }
        }
        Group("Properties") {
            Link("作业帮学习机系统环境控制", "读取和修改 persist.sys.env_config") {
                onZybosEnv()
            }
            QuickCommandItem("允许安装应用", "控制 sys.allow.install.package 属性。重启后系统会将其恢复默认，其作用与 Hook 的 “允许 Shell 安装应用” 效果相同，仅限 7.13 以下有效") {
                val title = "允许安装应用"
                commandAction = QuickAction(
                    title,
                    listOf("true" to "true", "默认" to "").map { (label, value) ->
                        QuickOption(label) { done ->
                            val command =
                                "setprop sys.allow.install.package '$value' && " +
                                    "[ \"\$(getprop sys.allow.install.package)\" = '$value' ] && " +
                                    "echo 'sys.allow.install.package = $value'"
                            commands.run(title, command, done)
                        }
                    },
                    successMessageOnly = true,
                )
            }
        }
    }
}

@Composable
private fun WorkModePage(a: ComponentActivity, back: () -> Unit) {
    var mode by remember(a) { mutableStateOf(WorkMode.get(a)) }
    var canWrite by remember(a) { mutableStateOf(Settings.System.canWrite(a)) }
    var canWriteSecure by remember(a) { mutableStateOf(QuickCommandController.canWriteSecureSettings(a)) }
    var showSecureGrant by rememberSaveable { mutableStateOf(false) }
    val grantCommand = "pm grant ${a.packageName} android.permission.WRITE_SECURE_SETTINGS"
    var shellReady by remember { mutableStateOf(WorkMode.shellReady()) }

    DisposableEffect(a) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canWrite = Settings.System.canWrite(a)
                canWriteSecure = QuickCommandController.canWriteSecureSettings(a)
                shellReady = WorkMode.shellReady()
            }
        }
        val received = Shizuku.OnBinderReceivedListener { shellReady = WorkMode.shellReady() }
        val dead = Shizuku.OnBinderDeadListener { shellReady = false }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ ->
            shellReady = WorkMode.shellReady()
        }
        a.lifecycle.addObserver(observer)
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        onDispose {
            a.lifecycle.removeObserver(observer)
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }

    Page(a, "工作模式", back, overlay = {
        OverlayDialog(
            show = showSecureGrant,
            title = "写入安全设置",
            summary = "请在有权限的 Shell（例如 adb shell）中执行下面的指令授予权限，完成后返回 Arashi 检查授权状态。",
            onDismissRequest = { showSecureGrant = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(grantCommand)
                TextButton(
                    "复制命令",
                    {
                        a.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("ADB 授权命令", grantCommand))
                        showSecureGrant = false
                    },
                    Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }) {
        IntroCard("选择执行指令、冻结和解冻时使用的权限")
        Group("选择模式") {
            WorkMode.entries.forEach { option ->
                CheckboxPreference(
                    title = option.label,
                    summary = when (option) {
                        WorkMode.SHELL -> "通过 Shizuku 或 Sui 执行指令。由于系统限制，部分不可用"
                        WorkMode.ROOT -> "通过 su 执行指令"
                    },
                    checked = mode == option,
                    checkboxLocation = CheckboxLocation.End,
                    onCheckedChange = { checked ->
                        if (checked) {
                            option.save(a)
                            mode = option
                        }
                    },
                )
            }
        }
        when (mode) {
            WorkMode.SHELL -> Group("权限") {
                Link("Shizuku / Sui", if (shellReady) "已授权" else "检查服务并授权") {
                    try {
                        when {
                            !Shizuku.pingBinder() || Shizuku.isPreV11() ->
                                MiuixPages.showMessage(a, "服务不可用", "请启动受支持的 Shizuku 或 Sui")
                            WorkMode.shellReady() -> shellReady = true
                            Shizuku.shouldShowRequestPermissionRationale() ->
                                MiuixPages.showMessage(a, "需要授权", "请在 Shizuku 或 Sui 的应用管理中授权 Arashi")
                            else -> Shizuku.requestPermission(1302)
                        }
                    } catch (_: RuntimeException) {
                        MiuixPages.showMessage(a, "无法请求授权", "请检查 Shizuku 或 Sui 是否正在运行")
                    }
                }
            }
            WorkMode.ROOT -> InfoCard(" 请授予 Arashi 完整的超级用户权限，如果弹出授权对话框请选择允许")
        }
        Group("备用权限") {
            Link(
                "修改系统设置",
                if (canWrite) "已授权" else "前往系统设置授权",
            ) {
                try {
                    a.startActivity(Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.fromParts("package", a.packageName, null),
                    ))
                } catch (_: android.content.ActivityNotFoundException) {
                    MiuixPages.showMessage(a, "无法打开设置", "此系统未提供修改系统设置的授权页面")
                }
            }
            Link(
                "写入安全设置",
                if (canWriteSecure) "已授权" else "查看并复制 ADB 授权命令",
            ) {
                canWriteSecure = QuickCommandController.canWriteSecureSettings(a)
                showSecureGrant = true
            }
        }
    }
}

private enum class DeviceFreezeMode(
    val title: String,
    val summary: String,
    val command: String,
    val resultState: String,
) {
    USER("用户级冻结", "由于厂商限制，重启后冻结失效", "disable-user", "disabled-user"),
    SYSTEM("系统级冻结", "需要 Root 权限，重启后仍生效", "disable", "disabled");

    fun save(context: Context) {
        context.getSharedPreferences("work_mode", Context.MODE_PRIVATE)
            .edit().putString("device_freeze_mode", name).apply()
    }

    companion object {
        fun isAutomatic(context: Context): Boolean =
            context.getSharedPreferences("work_mode", Context.MODE_PRIVATE)
                .getBoolean("device_freeze_automatic", false)

        fun setAutomatic(context: Context, automatic: Boolean) {
            context.getSharedPreferences("work_mode", Context.MODE_PRIVATE)
                .edit().putBoolean("device_freeze_automatic", automatic).apply()
        }

        fun forWorkMode(context: Context): DeviceFreezeMode =
            when (WorkMode.get(context)) {
                WorkMode.SHELL -> USER
                WorkMode.ROOT -> SYSTEM
            }

        fun effective(context: Context): DeviceFreezeMode =
            if (isAutomatic(context)) forWorkMode(context) else get(context)

        fun get(context: Context): DeviceFreezeMode {
            val value = context.getSharedPreferences("work_mode", Context.MODE_PRIVATE)
                .getString("device_freeze_mode", null)
            return entries.firstOrNull { it.name == value } ?: SYSTEM
        }
    }
}

@Composable
private fun FreezeModePage(a: ComponentActivity, back: () -> Unit) {
    var mode by remember(a) { mutableStateOf(DeviceFreezeMode.get(a)) }
    var automatic by remember(a) { mutableStateOf(DeviceFreezeMode.isAutomatic(a)) }
    val effectiveMode = if (automatic) DeviceFreezeMode.forWorkMode(a) else mode
    Page(a, "冻结方式", back) {
        IntroCard("仅用于「管控和云控」")
        Group("冻结方式") {
            SwitchPreference(
                title = "自动选择",
                summary = if (automatic) "当前使用${effectiveMode.title}" else "按工作模式选择冻结方式",
                checked = automatic,
                onCheckedChange = {
                    DeviceFreezeMode.setAutomatic(a, it)
                    automatic = it
                },
            )
            DeviceFreezeMode.entries.forEach { option ->
                CheckboxPreference(
                    title = option.title,
                    summary = "pm ${option.command} · ${option.summary}",
                    checked = effectiveMode == option,
                    enabled = !automatic,
                    checkboxLocation = CheckboxLocation.End,
                    onCheckedChange = { checked ->
                        if (!automatic && checked) {
                            option.save(a)
                            mode = option
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    a: androidx.activity.ComponentActivity,
    onWorkMode: () -> Unit,
    onFreezeMode: () -> Unit,
    onNotifications: () -> Unit,
    onAbout: () -> Unit,
    onLogs: () -> Unit,
) = Page(a, "应用偏好设置") {
    val prefs = a.getSharedPreferences("ui", Context.MODE_PRIVATE)
    val logPrefs = remember(a) {
        a.getSharedPreferences(com.lijinmu.arashi.logs.RuntimeLogStore.PREFS, Context.MODE_PRIVATE)
    }
    var loggingEnabled by remember(logPrefs) {
        mutableStateOf(com.lijinmu.arashi.logs.RuntimeLogStore.isEnabled(a))
    }
    var togglingLogs by remember { mutableStateOf(false) }
    val logsScope = rememberCoroutineScope()
    DisposableEffect(logPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == com.lijinmu.arashi.logs.RuntimeLogStore.KEY_ENABLED) {
                loggingEnabled = changed.getBoolean(key, false)
            }
        }
        logPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { logPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val themeNames = listOf("默认", "开启", "关闭")
    val themeModes = listOf(0, 2, 1)
    var selectedTheme by remember {
        mutableIntStateOf(themeModes.indexOf(prefs.getInt("mode", 0)).coerceAtLeast(0))
    }
    // Keep persisted values: 0 = purple, 1 = blue, 2 = system Monet.
    val colorSchemeValues = listOf(1, 0, 2)
    var selectedColorScheme by remember {
        mutableIntStateOf(colorSchemeValues.indexOf(prefs.getInt("color_scheme", 2)).coerceAtLeast(0))
    }
    val launcher = remember(a) { android.content.ComponentName(a, "com.lijinmu.arashi.LauncherAlias") }
    var hidden by remember {
        mutableStateOf(a.packageManager.getComponentEnabledSetting(launcher) ==
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }
    var navigationStyle by remember { mutableIntStateOf(prefs.getInt("navigation_style", 0).coerceIn(0, 1)) }
    var moduleActive by rememberSystemServerActivation(a)
    DisposableEffect(a) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                moduleActive = isLsposedActive(a)
            }
        }
        a.lifecycle.addObserver(observer)
        onDispose { a.lifecycle.removeObserver(observer) }
    }
    val workPrefs = remember(a) { a.getSharedPreferences("work_mode", Context.MODE_PRIVATE) }
    var workMode by remember(a) { mutableStateOf(WorkMode.get(a)) }
    var freezeMode by remember(a) { mutableStateOf(DeviceFreezeMode.effective(a)) }
    DisposableEffect(workPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            workMode = WorkMode.get(a)
            freezeMode = DeviceFreezeMode.effective(a)
        }
        workPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { workPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Group("工作模式") {
        Link("工作模式", workMode.label) { onWorkMode() }
        Link("冻结方式", freezeMode.title) { onFreezeMode() }
    }
    Group("外观") {
        OverlayDropdownPreference(
            items = listOf("蓝色", "紫色", "系统默认的 Monet 颜色"),
            selectedIndex = selectedColorScheme,
            title = "配色模式",
            onSelectedIndexChange = { index ->
                selectedColorScheme = index
                prefs.edit().putInt("color_scheme", colorSchemeValues[index]).apply()
            },
        )
        OverlayDropdownPreference(
            items = themeNames,
            selectedIndex = selectedTheme,
            title = "深色模式",
            onSelectedIndexChange = { index ->
                selectedTheme = index
                prefs.edit().putInt("mode", themeModes[index]).apply()
            },
        )
        OverlayDropdownPreference(
            items = listOf("默认", "悬浮"),
            selectedIndex = navigationStyle,
            title = "导航栏样式",
            onSelectedIndexChange = { index ->
                navigationStyle = index
                prefs.edit().putInt("navigation_style", index).apply()
            },
        )
    }
    Group("通知") {
        Link("通知设置", "管理包管理服务与 TCP 拦截通知") {
            onNotifications()
        }
    }
    Group("其他") {
        SwitchPreference(
            checked = loggingEnabled,
            onCheckedChange = { requested ->
                if (!togglingLogs) {
                    togglingLogs = true
                    loggingEnabled = requested
                    logsScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                        try {
                            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                                com.lijinmu.arashi.logs.RuntimeLogger.setEnabled(a, requested)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            MiuixPages.showMessage(a, "切换日志失败", failure.message ?: "无法更新运行日志状态")
                        } finally {
                            loggingEnabled = com.lijinmu.arashi.logs.RuntimeLogStore.isEnabled(a)
                            togglingLogs = false
                        }
                    }
                }
            },
            title = "运行日志",
            summary = "仅保留最近 12 小时的运行信息与异常",
            enabled = !togglingLogs,
        )
        AnimatedVisibility(
            visible = loggingEnabled,
            enter = expandVertically(
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(150)),
        ) {
            ArrowPreference(
                title = "查看日志",
                summary = "查看、复制、导出或清除运行日志",
                enabled = !togglingLogs,
                onClick = { if (!togglingLogs && loggingEnabled) onLogs() },
            )
        }
        SwitchPreference(
            checked = hidden,
            onCheckedChange = { requested ->
                if (!isLsposedActive(a)) {
                    moduleActive = false
                } else {
                    try {
                        a.packageManager.setComponentEnabledSetting(
                            launcher,
                            if (requested) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP,
                        )
                        hidden = requested
                        com.lijinmu.arashi.logs.RuntimeLogger.info("Settings", "隐藏桌面图标 = $requested")
                    } catch (error: RuntimeException) {
                        MiuixPages.showMessage(a, "切换失败", "无法更改桌面图标状态：${error.message}")
                    }
                }
            },
            title = "隐藏桌面图标",
            summary = if (moduleActive) "隐藏后仍可从 LSPosed 管理器打开" else "需要先激活 LSPosed 才能隐藏桌面图标",
            enabled = moduleActive,
        )

    }
    Group("关于") {
        Link("关于 Arashi", "有关 Arashi 的更多详细信息页") {
            onAbout()
        }
    }

}

@Composable
private fun AboutCard(
    title: String,
    value: String,
    arrow: Boolean = false,
    externalLink: Boolean = false,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = value,
            onClick = onClick,
            endActions = if (arrow || externalLink) {
                {
                    Icon(
                        if (externalLink) MiuixIcons.Link else MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        modifier = if (externalLink) Modifier.size(22.dp)
                            else Modifier.size(width = 10.dp, height = 16.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            } else null,
        )
    }
}

@Composable
private fun AboutPage(a: androidx.activity.ComponentActivity, back: () -> Unit) = Page(a, "关于 Arashi", back) {
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapAt by remember { mutableLongStateOf(0L) }
    AboutCard("版本号", BuildConfig.VERSION_NAME, onClick = {
            val now = SystemClock.elapsedRealtime()
            versionTapCount = if (now - lastVersionTapAt <= 1200L) versionTapCount + 1 else 1
            lastVersionTapAt = now
            if (versionTapCount >= 7) {
                versionTapCount = 0
                lastVersionTapAt = 0L
                MiuixPages.showMessage(a, "", "棍母棍母棍母")
            }
    })
    AboutCard("作者", "李瑾沐", arrow = true, onClick = {
        MiuixPages.showDetails(a, "关于作者", "我的名字叫李瑾沐（€rustStore 或者是 H…Hey），一名独立开发者。可以在我的网站或者群聊上找到我")
    })
    AboutCard("Arashi 官网", "arashi.lijinmu.com", externalLink = true, onClick = {
        val url = "https://arashi.lijinmu.com"
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            MiuixPages.showMessage(a, "无法打开链接", "请先安装可打开网页的应用")
        }
    })
    AboutCard("GitHub 仓库", "lijinmu666/Arashi-for-Zybos", externalLink = true, onClick = {
        val url = "https://github.com/lijinmu666/Arashi-for-Zybos"
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            MiuixPages.showMessage(a, "无法打开链接", "请先安装可打开网页的应用")
        }
    })
    AboutCard("QQ 群聊", "加入 Arashi 用户交流与反馈群", externalLink = true, onClick = {
        val url = "https://qun.qq.com/universal-share/share?ac=1&authKey=i%2Bh3ZyTelsMfhjWvc8srNCHnS4BMGrsr4hj1fDcEXx0fC5ECgHyUjh45kS5qiQ3W&busi_data=eyJncm91cENvZGUiOiIxMTI1MTkwMzUwIiwidG9rZW4iOiIrRFg2KzdBTHhRRlZKODZjUUt2U2gySFRBaHFBeFNIYTdFTFc0emcydXJuTEZsN1dvS3MwT1J4ZFF2dnNJNUhOIiwidWluIjoiODQxNTYzNjAifQ%3D%3D&data=PB0LIOhizgFR_GzQFzdDn6FEbw0rzkzMw5JbqCzMZp0t72lHOOiSfSqMcISpvsGOvjkcACA_44t9aKGSV3GaNQ&svctype=4&tempid=h5_group_info"
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            MiuixPages.showMessage(a, "无法打开链接", "请先安装可打开网页的应用")
        }
    })
     AboutCard("lijinmu.com", "前往我的主页", externalLink = true, onClick = {
        val url = "https://lijinmu.com"
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            MiuixPages.showMessage(a, "无法打开链接", "请先安装可打开网页的应用")
        }
    })
}

@Composable
private fun NotificationSettingsPage(a: androidx.activity.ComponentActivity, back: () -> Unit) = Page(a, "通知设置", back) {
    val prefs = a.getSharedPreferences(com.lijinmu.arashi.notifications.NotificationPreferences.PREFS, Context.MODE_PRIVATE)
    IntroCard("按类型设置消息提醒，点按通知可查看对应记录")
    Group("包管理服务") {
        listOf(
            com.lijinmu.arashi.notifications.NotificationPreferences.BLOCKED_TARGET to "保护名单拒绝",
            com.lijinmu.arashi.notifications.NotificationPreferences.BLACKLIST to "黑名单拒绝",
            com.lijinmu.arashi.notifications.NotificationPreferences.WHITELIST_ONLY_DENIED to "非白名单拒绝",
            com.lijinmu.arashi.notifications.NotificationPreferences.WHITELIST to "白名单放行",
            com.lijinmu.arashi.notifications.NotificationPreferences.TIMEOUT to "倒计时自动拒绝",
            com.lijinmu.arashi.notifications.NotificationPreferences.SCREEN_OFF to "息屏自动拒绝",
        ).forEach { (key, title) ->
            var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, true)) }
            SwitchPreference(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    prefs.edit().putBoolean(key, it).apply()
                    if (it) com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(a)
                },
                title = title,
            )
        }
    }
    Group("TCP 服务") {
        val key = com.lijinmu.arashi.notifications.NotificationPreferences.TCP
        var checked by remember { mutableStateOf(prefs.getBoolean(key, true)) }
        SwitchPreference(
            checked = checked,
            onCheckedChange = {
                checked = it
                prefs.edit().putBoolean(key, it).apply()
                if (it) com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(a)
            },
            title = "login.fail 拦截成功",
        )
    }
    Group("系统") {
        Link("系统通知权限", "允许 Arashi 发送通知，或调整各类通知的声音与显示方式") {
            com.lijinmu.arashi.notifications.AppNotifications.openSettings(a)
        }
    }
}

@Composable
private fun ZybosEnvPage(a: ComponentActivity, back: () -> Unit = a::finish) = Page(a, "Zybos 环境控制", back) {
    val environments = listOf("PRODUCTION", "TEST", "MOCK")
    var original by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var environment by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var showTools by remember { mutableStateOf(false) }
    var tips by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(original == null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun accept(config: com.lijinmu.arashi.tools.ZybosEnvConfig) {
        original = config.json
        environment = environments.indexOf(config.environment)
        error = null
    }
    LaunchedEffect(Unit) {
        if (original == null) {
            busy = true
            try {
                accept(withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.readConfig(a) })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "无法读取环境配置"
            } finally {
                busy = false
            }
        }
    }
    IntroCard("这些开关很危险，请谨慎使用，更改环境会影响登录和激活。重启应用或系统后写入生效")
    if (busy) LoadingCard("读取或写入环境配置")
    if (error != null) InfoCard("操作失败：${error.orEmpty()}")
    Group("环境配置") {
        OverlayDropdownPreference(
            items = environments,
            selectedIndex = environment,
            title = "环境",
            enabled = !busy && original != null,
            onSelectedIndexChange = { environment = it },
        )
        SwitchPreference(
            checked = showTools,
            onCheckedChange = { showTools = it },
            title = "启用作业帮调试工具悬浮窗",
            enabled = !busy && original != null,
        )
        SwitchPreference(
            checked = tips,
            onCheckedChange = { tips = it },
            title = "启用tips环境",
            enabled = !busy && original != null,
        )
    }
    TextButton(
        "应用并写入",
        {
            val base = original
            if (!busy && base != null) {
                busy = true
                val selectedEnv = environments[environment]
                val selectedTools = showTools
                val selectedTips = tips
                scope.launch {
                    try {
                        accept(withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.applyConfig(a, base, selectedEnv, selectedTools, selectedTips) })
                        MiuixPages.showMessage(a, "", "写入完毕")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "写入失败"
                        MiuixPages.showMessage(a, "写入失败", error.orEmpty())
                    } finally {
                        busy = false
                    }
                }
            }
        },
        Modifier.fillMaxWidth(),
        enabled = !busy && original != null,
        colors = ButtonDefaults.textButtonColorsPrimary(),
    )
    TextButton(
        "查看当前配置",
        {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        val json = withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.readFormattedProperty(a) }
                        MiuixPages.showDetails(a, "当前环境配置", json)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        MiuixPages.showMessage(a, "读取失败", failure.message ?: "无法读取环境配置")
                    } finally {
                        busy = false
                    }
                }
            }
        },
        Modifier.fillMaxWidth(),
        enabled = !busy,
    )
    TextButton(
        "重置为默认",
        {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        accept(withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.resetConfig(a) })
                        showTools = false
                        tips = false
                        MiuixPages.showMessage(a, "", "已重置为默认")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "重置失败"
                        MiuixPages.showMessage(a, "重置失败", error.orEmpty())
                    } finally {
                        busy = false
                    }
                }
            }
        },
        Modifier.fillMaxWidth(),
        enabled = !busy,
    )
}


