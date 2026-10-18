package com.arashi.ui

import com.arashi.ApkInstallerActivity
import com.arashi.BuildConfig
import com.arashi.Main
import com.arashi.UninstallPromptActivity
import com.arashi.about.About
import com.arashi.settings.ArashiSettings
import com.arashi.settings.HookSettings
import com.arashi.settings.BackgroundWhitelistActivity
import com.arashi.xposed.BackgroundPolicy
import com.arashi.tools.QuickCommands
import com.arashi.tools.ZybosTools
import com.arashi.tools.ZybosUninstallPickerActivity
import com.arashi.tcp.TcpHistoryActivity
import com.arashi.uninstall.UninstallHistoryActivity
import com.arashi.uninstall.UninstallListActivity
import com.arashi.uninstall.UninstallSettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arashi.xposed.HookConfig
import com.arashi.xposed.HookScopes
import com.arashi.xposed.SettingsProvider
import com.arashi.uninstall.UninstallHistoryStore
import com.arashi.zybos.SystemToolSupport
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.WeakHashMap
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.arashi.uninstall.UninstallConfig

object MiuixPages {
    private data class Message(val title: String, val body: String, val buttonLabel: String = "知道了")
    private val messages = WeakHashMap<Activity, MutableState<Message?>>()

    @JvmStatic fun renderHome(a: Main) = render(a) { HomePage(a) }
    @JvmStatic fun renderHookSettings(a: HookSettings) = render(a) { HookPage(a) }
    @JvmStatic fun renderBackgroundWhitelist(a: BackgroundWhitelistActivity) =
        render(a) { BackgroundWhitelistPage(a) }
    @JvmStatic fun renderUninstallSettings(a: UninstallSettings) = render(a) { UninstallPage(a) }
    @JvmStatic fun renderTcpHistory(a: TcpHistoryActivity) = render(a) { TcpHistoryPage(a) }
    @JvmStatic fun renderUninstallHistory(a: UninstallHistoryActivity) = render(a) { UninstallHistoryPage(a) }
    @JvmStatic fun renderUninstallList(a: UninstallListActivity, blacklist: Boolean) =
        render(a) { UninstallListPage(a, blacklist) }
    @JvmStatic fun renderUninstallPrompt(
        a: UninstallPromptActivity,
        target: String?,
        uid: Int,
        packages: Array<String>?,
        deadline: Long,
    ) = render(a) { UninstallPrompt(a, target, uid, packages, deadline) }
    @JvmStatic fun renderQuickCommands(a: QuickCommands) = render(a) { QuickPage(a) }
    @JvmStatic fun renderZybosTools(a: ZybosTools) = render(a) { ZybosPage(a) }
    @JvmStatic fun renderZybosUninstallPicker(a: ZybosUninstallPickerActivity) =
        render(a) { ZybosUninstallPickerPage(a) }
    @JvmStatic fun renderSettings(a: ArashiSettings) = render(a) { SettingsPage(a) }
    @JvmStatic fun renderAbout(a: About) = render(a) { AboutPage(a) }
    @JvmStatic fun renderUnsupportedDevice(a: Main) = render(a) { UnsupportedDevicePage(a) }
    @JvmStatic fun renderInstallerConfirmation(
        a: ApkInstallerActivity,
        path: String,
        packageName: String?,
        label: String,
        icon: Drawable,
    ) = render(a) { InstallerPage(a, path, packageName, label, icon) }
    @JvmStatic fun renderSystemToolsUnavailable(a: ZybosTools) = render(a) {
        Page(a, "系统特权工具", a::finish) { InfoCard(SystemToolSupport.MESSAGE) }
    }
    @JvmStatic fun renderInstallerProgress(
        a: ApkInstallerActivity,
        label: String,
        packageName: String?,
        icon: Drawable?,
    ) = render(a) { InstallerProgressPage(a, label, packageName, icon) }
    @JvmStatic fun renderInstallerResult(a: ApkInstallerActivity, title: String, message: String) =
        render(a) {
            Page(a, title, a::finish) {
                InfoCard(message)
                TextButton(
                    "知道了",
                    a::finish,
                    Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }

    @JvmStatic fun showMessage(a: Activity, title: String, body: String) {
        a.runOnUiThread { messageState(a).value = Message(title, body) }
    }

    @JvmStatic fun showSignatureWarning(a: Activity) {
        a.runOnUiThread {
            messageState(a).value = Message(
                "签名验证",
                "当前应用与原作者签名不匹配，安装包可能被篡改！点击仍可继续使用",
                "哦",
            )
        }
    }

    private fun messageState(a: Activity): MutableState<Message?> = synchronized(messages) {
        messages.getOrPut(a) { mutableStateOf(null) }
    }

    private fun render(a: Activity, page: @Composable () -> Unit) {
        a.setContentView(ComposeView(a).apply {
            setContent {
                val uiPrefs = remember(a) { a.getSharedPreferences("ui", Context.MODE_PRIVATE) }
                var mode by remember(uiPrefs) { mutableIntStateOf(uiPrefs.getInt("mode", 0)) }
                var colorScheme by remember(uiPrefs) {
                    mutableIntStateOf(uiPrefs.getInt("color_scheme", 0))
                }
                DisposableEffect(uiPrefs) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                        when (key) {
                            "mode" -> mode = prefs.getInt("mode", 0)
                            "color_scheme" -> colorScheme = prefs.getInt("color_scheme", 0)
                        }
                    }
                    uiPrefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { uiPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }
                val usePurple = colorScheme != 1
                val dark = when (mode) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                }
                val colors = if (!usePurple) {
                    if (dark) darkColorScheme() else lightColorScheme()
                } else if (dark) {
                    darkColorScheme(
                        primary = Color(0xFFB69CFF),
                        primaryVariant = Color(0xFF9B7BFA),
                        onPrimaryVariant = Color(0xFFE6DEFF),
                        disabledPrimary = Color(0xFF493F66),
                        disabledPrimaryButton = Color(0xFF493F66),
                        disabledPrimarySlider = Color(0xFF5B5078),
                        primaryContainer = Color(0xFF7655D9),
                        tertiaryContainer = Color(0xFF382D57),
                        onTertiaryContainer = Color(0xFFC8B6FF),
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFF7655D9),
                        primaryVariant = Color(0xFF6845CC),
                        onPrimaryVariant = Color(0xFFE3D9FF),
                        disabledPrimary = Color(0xFFD9CEF5),
                        disabledPrimaryButton = Color(0xFFD9CEF5),
                        disabledPrimarySlider = Color(0xFFC8BAEA),
                        primaryContainer = Color(0xFF8768E7),
                        tertiaryContainer = Color(0xFFF0EBFF),
                        onTertiaryContainer = Color(0xFF6845CC),
                    )
                }

                SideEffect {
                    if (a is UninstallPromptActivity) {
                        a.window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    } else {
                        applySystemBars(a, dark)
                    }
                }
                MiuixTheme(colors = colors) { page() }
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars(a: Activity, dark: Boolean) {
        val background = if (dark) 0xFF000000.toInt() else 0xFFF7F7F7.toInt()
        a.window.statusBarColor = background
        a.window.navigationBarColor = background
        a.window.setBackgroundDrawable(ColorDrawable(background))
        val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val current = a.window.decorView.systemUiVisibility
        a.window.decorView.systemUiVisibility = if (dark) current and lightFlags.inv() else current or lightFlags
    }

    @Composable
    fun MessageDialog(a: Activity) {
        val message = messageState(a)
        val current = message.value
        // Visibility can change immediately, but the overlay still draws its
        // content during the exit animation. Retain the last displayed message
        // until replacement or composition disposal rather than blanking it.
        var retained by remember(a) { mutableStateOf(current) }
        SideEffect {
            if (current != null) retained = current
        }
        val displayed = current ?: retained
        OverlayDialog(
            show = current != null,
            title = displayed?.title,
            summary = displayed?.body,
            onDismissRequest = { message.value = null },
        ) {
            TextButton(
                displayed?.buttonLabel ?: "知道了",
                { message.value = null },
                Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun Page(
    activity: Activity,
    title: String,
    back: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (back == null) {
                TopAppBar(title = title, largeTitle = title)
            } else {
                SmallTopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = back) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = body) }
            }
            MiuixPages.MessageDialog(activity)
        }
    }
}

@Composable
private fun Group(title: String, body: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            modifier = Modifier.padding(start = 12.dp),
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.footnote1,
        )
        Card(Modifier.fillMaxWidth()) { Column(content = body) }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun IntroCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.tertiaryContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun Link(title: String, summary: String? = null, action: () -> Unit) =
    ArrowPreference(title = title, summary = summary, onClick = action)

@Composable
private fun StatusLine(name: String, value: String, available: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.SemiBold))
        Text(
            value,
            color = if (available) Color(0xFF36A866) else Color(0xFFE05252),
            style = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

private fun isLsposedActive(context: Context): Boolean {
    return HookScopes.isSystemServerActive(context)
}

@Composable
private fun rememberSystemServerActivation(a: androidx.activity.ComponentActivity): MutableState<Boolean> {
    val active = remember(a) { mutableStateOf(isLsposedActive(a)) }
    LaunchedEffect(a) {
        while (true) {
            if (a.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                active.value = withContext(Dispatchers.IO) { HookScopes.read(a).systemActive }
            }
            delay(1000)
        }
    }
    return active
}

@Composable
private fun HomePage(a: Main) = Page(a, "Arashi") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.tertiaryContainer,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            "版本 ${BuildConfig.VERSION_NAME}",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(18.dp))
        val moduleActive by rememberSystemServerActivation(a)
        StatusLine("LSPosed", if (moduleActive) "已激活" else "未激活", moduleActive)
    }
    Group("功能") {
        Link("Hook 功能设置", "控制 Hook 是否启用") { a.startActivity(Intent(a, HookSettings::class.java)) }
        Link("卸载验证设置", "验证发起的卸载请求") { a.startActivity(Intent(a, UninstallSettings::class.java)) }
        Link("快捷指令执行", "调整设置、冻结应用等快捷操作") { a.startActivity(Intent(a, QuickCommands::class.java)) }
        val toolsBlocked = SystemToolSupport.isBlocked()
        Box(Modifier.fillMaxWidth().clickable(enabled = toolsBlocked) {
            MiuixPages.showMessage(a, "暂不可用", SystemToolSupport.MESSAGE)
        }) {
            ArrowPreference(
                title = "系统特权工具",
                summary = "利用 ZybosSysApiManager 执行系统操作",
                enabled = !toolsBlocked,
                onClick = { a.startActivity(Intent(a, ZybosTools::class.java)) },
            )
        }
        Link("应用偏好设置", "配置外观等") { a.startActivity(Intent(a, ArashiSettings::class.java)) }
        Link("关于 Arashi", "有关 Arashi 的更多详细信息页") { a.startActivity(Intent(a, About::class.java)) }
    }
}

@Composable
private fun UnsupportedDevicePage(a: Main) = Page(a, "Arashi") {
    OverlayDialog(
        show = true,
        summary = "当前系统可能无法正常使用 Arashi 功能",
        onDismissRequest = a::exitUnsupportedDevice,
    ) {
        TextButton(
            "确认",
            a::exitUnsupportedDevice,
            Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

private data class HookItem(val title: String, val summary: String, val key: String)

@Composable
private fun HookPage(a: HookSettings) = Page(a, "Hook 功能设置", a::finish) {
    IntroCard("首次使用请启用模块并勾选推荐作用域并重启系统。修改应用作用域后需重启对应应用，部分需要重启系统")
    val prefs = a.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE)
    var backgroundSelected by remember {
        mutableStateOf(prefs.getStringSet(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, emptySet()).orEmpty().toSet())
    }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HookConfig.KEY_KEEP_BACKGROUND_WHITELIST) {
                backgroundSelected = prefs.getStringSet(key, emptySet()).orEmpty().toSet()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var scopes by remember {
        mutableStateOf(HookScopes.Snapshot(isLsposedActive(a), false, emptySet<String>()))
    }
    LaunchedEffect(a) {
        while (true) {
            if (a.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                scopes = withContext(Dispatchers.IO) { HookScopes.read(a) }
            }
            delay(1000)
        }
    }
    val groups = listOf(
        "安装" to listOf(
            HookItem("允许外部应用安装", "禁止 PackageInstaller 的安装检查，允许安装外部 APK", HookConfig.KEY_EXTERNAL_INSTALL),
            HookItem("允许 Shell 安装", "放行 ADB 和 pm install 安装指令，允许 Shell 安装应用", HookConfig.KEY_SHELL_INSTALL),
            HookItem("解除安装黑名单限制", "允许安装被硬编码在 mZybBlacklistApp 中的包名。开关控制重启系统后生效", HookConfig.KEY_CLEAR_BLACKLIST),
        ),
        "行为" to listOf(
            HookItem("允许打开开发者选项", "绕过系统设置检查，允许打开开发者选项的 Activity", HookConfig.KEY_DEV_OPTIONS),
            HookItem("保留后台进程", "拦截划卡后强杀应用进程。开关控制立即生效", HookConfig.KEY_KEEP_BACKGROUND),
            HookItem("允许 USB 文件传输", "允许挂载外部 USB 设备和使用数据传输。开关控制立即生效", HookConfig.KEY_ALLOW_USB),
            HookItem("允许任意应用设置壁纸", "允许任意应用设置壁纸，并移除新版桌面覆盖系统壁纸的静态背景。需勾选系统框架与作业帮桌面作用域，开关控制立即生效", HookConfig.KEY_ALLOW_WALLPAPER),
            HookItem("允许 WiFi 网页登录", "允许连接到认证型 WiFi 正常跳转到 “登录到网络” 登录验证网页", HookConfig.KEY_CAPTIVE_PORTAL),
            HookItem("解除应用详情页限制", "允许打开 WebView、report服务 和 PadMs 的应用设置详情页。开关控制立即生效", HookConfig.KEY_CLEAR_DETAIL_BLACK),
            HookItem("录屏系统声音支持", "恢复 SystemUI 录屏菜单的设备声音、麦克风及混合音源选择选项", HookConfig.KEY_RECORD_INTERNAL_AUDIO),
            HookItem("阻止重设默认浏览器", "拦截 SystemUI 开机时将 “学习网站” 设为默认浏览器", HookConfig.KEY_BLOCK_DEFAULT_BROWSER),
        ),
        "美化" to listOf(
            HookItem("桌面图标圆角", "桌面应用图标与文件夹的改成 iOS 样式的圆角，文件夹使用半透明底色。强行停止桌面后生效", HookConfig.KEY_IOS_ICONS),
						HookItem("状态栏显示日期", "仿 iPadOS 在状态栏左侧时间后显示月日与星期，如 16:40  10月17日周六。开关控制在下次时钟刷新时生效", HookConfig.KEY_STATUS_BAR_DATE),
        ),
        "TCP 服务" to listOf(
            HookItem("屏蔽锁机广播", "拦截 ZpTcpService 发出的所有 login.fail 广播，以此防止云控锁机", HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL),
        ),
        "管控服务" to listOf(
            HookItem("拦截管控服务行为", "从 SystemServer 层拦截管控服务的强杀应用和移除任务行为", HookConfig.KEY_BLOCK_SUPERVISE),
            HookItem("阻止时长上报", "拦截管控服务的使用时长上传", HookConfig.KEY_BLOCK_USAGE_REPORT),
            HookItem("解除应用禁用", "将应用可用查询结果改为允许，绕过应用禁用和时长限制。已禁用的应用要先从其他应用打开一次才能从桌面启动", HookConfig.KEY_NEUTRALIZE_POLICY),
            HookItem("解除页面屏蔽", "将受限页面查询结果置空，允许打开被屏蔽的页面", HookConfig.KEY_NEUTRALIZE_ADVANCE),
            HookItem("阻止超时锁屏", "拦截管控服务的超时处理，达到整机时长限制或超出可用时段后不锁屏", HookConfig.KEY_NO_TIMEOUT_LOCK),
        ),
    )
    groups.forEach { (name, entries) ->
        Group(name) {
            entries.forEach { entry ->
                val available = scopes.isAvailable(entry.key)
                var checked by remember(entry.key) {
                    mutableStateOf(
                        prefs.getBoolean(entry.key, HookConfig.defaultEnabled(entry.key)),
                    )
                }
                SwitchPreference(
                    checked = checked,
                    onCheckedChange = {
                        if (scopes.isAvailable(entry.key)) {
                            checked = it
                            prefs.edit().putBoolean(entry.key, it).apply()
                        }
                    },
                    title = entry.title,
                    summary = if (available) entry.summary
                        else entry.summary + "。" + scopes.unavailableReason(entry.key),
                    enabled = available,
                )
                if (entry.key == HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL) {
                    ArrowPreference(
                        title = "TCP 拦截记录",
                        summary = "查看触发时间、错误码、响应状态和调用详情",
                        onClick = { a.startActivity(Intent(a, TcpHistoryActivity::class.java)) },
                    )
                }
                if (entry.key == HookConfig.KEY_DEV_OPTIONS) {
                    var injectDeveloperMenu by remember {
                        mutableStateOf(
                            prefs.getBoolean(
                                HookConfig.KEY_DEV_OPTIONS_MENU,
                                HookConfig.defaultEnabled(HookConfig.KEY_DEV_OPTIONS_MENU),
                            ),
                        )
                    }
                    DisposableEffect(prefs) {
                        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            if (key == HookConfig.KEY_DEV_OPTIONS_MENU) {
                                injectDeveloperMenu = prefs.getBoolean(
                                    HookConfig.KEY_DEV_OPTIONS_MENU,
                                    HookConfig.defaultEnabled(HookConfig.KEY_DEV_OPTIONS_MENU),
                                )
                            }
                        }
                        prefs.registerOnSharedPreferenceChangeListener(listener)
                        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                    }
                    AnimatedVisibility(
                        visible = checked,
                        enter = expandVertically(
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(
                            animationSpec = tween(250, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(150)),
                    ) {
                        SwitchPreference(
                            checked = injectDeveloperMenu,
                            onCheckedChange = {
                                if (available) {
                                    injectDeveloperMenu = it
                                    prefs.edit().putBoolean(HookConfig.KEY_DEV_OPTIONS_MENU, it).apply()
                                }
                            },
                            title = "注入开发者选项入口",
                            summary = "向设置侧边菜单中添加“开发者选项”入口。重启系统设置后生效",
                            enabled = available,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = entry.key == HookConfig.KEY_KEEP_BACKGROUND && checked,
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
                        title = "后台保留白名单",
                        summary = if (backgroundSelected.isEmpty()) "目前划卡后保留所有应用后台进程"
                            else "目前划卡后只保留已选择的 ${backgroundSelected.size} 个应用",
                        onClick = {
                            a.startActivity(Intent(a, BackgroundWhitelistActivity::class.java))
                        },
                    )
                }
            }
        }
    }
    InfoCard("开关修改后自动保存")
}

private data class BackgroundChoice(val packageName: String, val label: String, val isSystem: Boolean? = null)

@Composable
private fun BackgroundWhitelistPage(a: BackgroundWhitelistActivity) {
    val prefs = a.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE)
    var selected by remember {
        mutableStateOf(prefs.getStringSet(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, emptySet()).orEmpty().toSet())
    }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var choices by remember { mutableStateOf(emptyList<BackgroundChoice>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(a) {
        try {
            choices = withContext(Dispatchers.IO) {
                val manager = a.packageManager
                manager.getInstalledApplications(0)
                    .filterNot { BackgroundPolicy.isExempt(it.packageName) }
                    .map { info ->
                        BackgroundChoice(info.packageName,
                            runCatching { info.loadLabel(manager).toString() }.getOrDefault(info.packageName),
                            (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0)
                    }
                    .sortedWith(compareBy<BackgroundChoice> { it.label.lowercase() }.thenBy { it.packageName })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "无法读取应用列表"
        } finally {
            loading = false
        }
    }
    val knownPackages = remember(choices) { choices.mapTo(HashSet<String>()) { it.packageName } }
    val missing = selected.filterNot { it in knownPackages || BackgroundPolicy.isExempt(it) }
        .sorted().map { BackgroundChoice(it, "未安装或不可见的应用") }
    val visible = (missing + choices).filter {
        val matchesCategory = when (filter) {
            1 -> it.isSystem == false
            2 -> it.isSystem == true
            3 -> it.packageName in selected
            else -> true
        }
        matchesCategory && (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
    }
    fun save(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, next).apply()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "后台保留白名单",
                navigationIcon = {
                    IconButton(onClick = a::finish) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                IntroCard(
                    "选中的应用划卡后不会被强杀，不选择任何应用时默认全部不杀，系统原有豁免应用不显示。选择后立即保存")
            }
            item {
                AppSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    label = "搜索应用名或包名",
                )
            }
            item {
                AppPickerFilterRow(
                    status = if (selected.isEmpty()) "当前保留全部应用" else "已选择 ${selected.size} 个应用",
                    categories = listOf("全部", "用户应用", "系统应用", "已选择"),
                    selectedIndex = filter,
                    onSelectedIndexChange = { filter = it },
                )
            }
            if (loading) {
                item { InfoCard("正在加载应用…") }
            } else if (error != null) {
                item { InfoCard("加载失败：" + error.orEmpty()) }
            } else {
                items(visible, key = { it.packageName }) { choice ->
                    val icon = remember(choice.packageName) {
                        runCatching { a.packageManager.getApplicationIcon(choice.packageName) }
                            .getOrElse { a.packageManager.defaultActivityIcon }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            AndroidView(
                                factory = { context ->
                                    ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                                },
                                update = { it.setImageDrawable(icon) },
                                modifier = Modifier.padding(start = 16.dp).size(40.dp),
                            )
                            Box(Modifier.weight(1f)) {
                                CheckboxPreference(
                                    checked = choice.packageName in selected,
                                    onCheckedChange = { checked ->
                                        save(if (checked) selected + choice.packageName else selected - choice.packageName)
                                    },
                                    title = choice.label, summary = choice.packageName,
                                    checkboxLocation = CheckboxLocation.End,
                                )
                            }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item { InfoCard("没有匹配的应用") }
                }
            }
        }
    }
}

private data class UninstallChoice(
    val key: String,
    val title: String,
    val summary: String,
    val kind: Int,
)

@Composable
private fun UninstallPage(a: UninstallSettings) = Page(a, "卸载验证", a::finish) {
    val prefs = a.getSharedPreferences(UninstallConfig.PREF_NAME, Context.MODE_PRIVATE)
    var moduleActive by rememberSystemServerActivation(a)
    var enabled by remember { mutableStateOf(prefs.getBoolean(UninstallConfig.ENABLED, false)) }
    DisposableEffect(a, prefs) {
        val runtime = a.getSharedPreferences(SettingsProvider.STATUS_PREF_NAME, Context.MODE_PRIVATE)
        fun refreshActivation() {
            moduleActive = isLsposedActive(a)
            enabled = UninstallConfig.syncActivation(a, moduleActive)
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SettingsProvider.KEY_SYSTEM_SERVER_BOOT) {
                refreshActivation()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshActivation()
        }
        runtime.registerOnSharedPreferenceChangeListener(listener)
        a.lifecycle.addObserver(observer)
        refreshActivation()
        onDispose {
            runtime.unregisterOnSharedPreferenceChangeListener(listener)
            a.lifecycle.removeObserver(observer)
        }
    }
    var spoofResult by remember { mutableStateOf(prefs.getBoolean(UninstallConfig.SPOOF_RESULT, false)) }
    val durations = listOf(5, 10, 15, 30)
    var duration by remember {
        mutableIntStateOf(durations.indexOf(UninstallConfig.normalizeTimeoutSeconds(
            prefs.getInt(UninstallConfig.TIMEOUT_SECONDS, UninstallConfig.DEFAULT_TIMEOUT))))
    }
    IntroCard("细化并管理每一次卸载请求。当系统尝试卸载应用时，会向你询问是否允许")
    Group("验证") {
        SwitchPreference(
            checked = enabled && moduleActive,
            onCheckedChange = { requested ->
                moduleActive = isLsposedActive(a)
                enabled = requested && moduleActive
                prefs.edit().putBoolean(UninstallConfig.ENABLED, enabled)
                    .putBoolean(UninstallConfig.RESTORE_ENABLED, requested && !moduleActive).apply()
            },
            title = "启用卸载验证",
            summary = if (moduleActive) "每次卸载请求将会弹窗向你确认"
                else "未检测到 system_server Hook，卸载验证暂时关闭；激活后会恢复此前的开启状态",
            enabled = moduleActive,
        )
        OverlayDropdownPreference(
            items = durations.map { "$it 秒" },
            selectedIndex = duration,
            title = "验证倒计时",
            onSelectedIndexChange = {
                duration = it
                prefs.edit().putInt(UninstallConfig.TIMEOUT_SECONDS, durations[it]).apply()
            },
        )
        SwitchPreference(
            checked = spoofResult,
            onCheckedChange = {
                spoofResult = it
                prefs.edit().putBoolean(UninstallConfig.SPOOF_RESULT, it).apply()
            },
            title = "伪装卸载结果",
            summary = "返回拒绝的卸载请求已成功卸载",
        )
    }

    Group("发起方策略") {
        Link("白名单", "白名单中的发起方不会弹窗向你确认，直接允许") {
            a.startActivity(Intent(a, UninstallListActivity::class.java).putExtra("blacklist", false))
        }
        Link("黑名单", "与白名单排斥，黑名单直接拒绝卸载；优先于白名单") {
            a.startActivity(Intent(a, UninstallListActivity::class.java).putExtra("blacklist", true))
        }
    }
    Group("记录") {
        Link("卸载记录", "查看允许和拒绝的卸载验证记录") {
            a.startActivity(Intent(a, UninstallHistoryActivity::class.java))
        }
    }
}

@Composable
private fun UninstallHistoryPage(a: UninstallHistoryActivity) {
    var records by remember { mutableStateOf<List<UninstallHistoryStore.Record>>(emptyList()) }
    var request by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var hasMore by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var clearError by remember { mutableStateOf<String?>(null) }
    // A single effect serializes refresh, pagination and deletion. Changing the
    // request cancels any old read before it can repopulate a cleared list.
    LaunchedEffect(request) {
        val deleting = clearing
        loading = !deleting
        try {
            if (deleting) {
                val count = withContext(Dispatchers.IO) { UninstallHistoryStore.clear(a) }
                records = emptyList()
                hasMore = false
                loadError = null
                clearError = null
                MiuixPages.showMessage(a, "清空完成", "已清空 $count 条卸载记录")
            } else {
                val before = records.lastOrNull()?.id ?: Long.MAX_VALUE
                val batch = withContext(Dispatchers.IO) {
                    UninstallHistoryStore.read(a, before, 101)
                }
                records = records + batch.take(100)
                hasMore = batch.size > 100
                loadError = null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (deleting) {
                clearError = error.message ?: "清空记录失败"
                MiuixPages.showMessage(a, "清空失败", clearError.orEmpty())
            } else {
                loadError = error.message ?: "读取记录失败"
            }
        }
        loading = false
        clearing = false
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "卸载记录",
                navigationIcon = {
                    IconButton(onClick = a::finish) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                IntroCard("记录已捕获的卸载请求，允许不代表系统最终卸载成功")
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        "刷新",
                        {
                            records = emptyList()
                            hasMore = true
                            request++
                        },
                        Modifier.weight(1f),
                        enabled = !loading && !clearing,
                    )
                    TextButton(
                        "清空记录",
                        { confirmClear = true },
                        Modifier.weight(1f),
                        enabled = records.isNotEmpty() && !loading && !clearing,
                    )
                }
            }
            items(records, key = { it.id }) { record ->
                val time = remember(record.time) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.time))
                }
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (record.allowed) "允许" else "拒绝",
                                color = if (record.allowed) Color(0xFF36A866) else Color(0xFFE05252),
                                style = MiuixTheme.textStyles.title3,
                            )
                            Text(time, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        Text("目标应用：" + (record.targetName ?: record.targetPackage ?: "未知应用"))
                        if (!record.targetPackage.isNullOrBlank()
                            && record.targetPackage != record.targetName) {
                            Text(record.targetPackage.orEmpty(),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        Text("发起方：" + (record.callerName ?: "进程") + " (" + record.callerUid + ")")
                        if (!record.callerPackage.isNullOrBlank()
                            && record.callerPackage != record.callerName) {
                            Text(record.callerPackage.orEmpty(),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        Text("原因：" + (record.reason ?: "未知"),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
            if (records.isEmpty() && !loading && loadError == null) {
                item { InfoCard("暂无卸载记录") }
            }
            if (loading) {
                item { InfoCard("正在加载记录…") }
            }
            if (loadError != null) {
                item { InfoCard("读取失败：" + loadError) }
            }
            if (clearError != null) {
                item { InfoCard("清空失败：" + clearError) }
            }
            if (hasMore && !loading && !clearing && loadError == null) {
                item {
                    TextButton("加载更多", { request++ }, Modifier.fillMaxWidth())
                }
            }
        }
    }
    MiuixPages.MessageDialog(a)
    OverlayDialog(
        show = confirmClear,
        title = "清空卸载记录",
        summary = "确定清空全部卸载记录吗？",
        onDismissRequest = { confirmClear = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton("取消", { confirmClear = false }, Modifier.weight(1f))
            TextButton(
                "清空",
                {
                    confirmClear = false
                    if (!clearing) {
                        clearing = true
                        request++
                    }
                },
                Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun UninstallListPage(a: UninstallListActivity, blacklist: Boolean) {
    val prefKey = if (blacklist) UninstallConfig.BLACKLIST else UninstallConfig.WHITELIST
    val title = if (blacklist) "卸载黑名单" else "卸载白名单"
    val prefs = a.getSharedPreferences(UninstallConfig.PREF_NAME, Context.MODE_PRIVATE)
    var selected by remember(prefKey) {
        mutableStateOf(prefs.getStringSet(prefKey, mutableSetOf()).orEmpty().toSet())
    }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val choices = remember {
        val manager = a.packageManager
        val installed = runCatching { manager.getInstalledApplications(0) }.getOrDefault(emptyList())
        val applications = installed.map { info ->
            val label = runCatching { info.loadLabel(manager).toString() }.getOrDefault(info.packageName)
            val isSystem = (info.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            UninstallChoice(
                "pkg:" + info.packageName,
                label,
                info.packageName +
                    (if (isSystem) " · 系统应用" else " · 用户应用"),
                if (isSystem) 2 else 1,
            )
        }.sortedWith(compareBy<UninstallChoice> { it.kind }.thenBy { it.title.lowercase() })
        val systemUids = listOf(
            "root" to 0, "daemon" to 1, "bin" to 2,
            "sys" to 3, "system" to 1000, "shell" to 2000,
        )
        val uids = systemUids.map { (name, uid) ->
            UninstallChoice("uid:" + uid, name + " (" + uid + ")", "UID " + uid, 3)
        }
        applications + uids
    }
    val knownKeys = remember(choices) { choices.mapTo(HashSet<String>()) { it.key } }
    val manualChoices = selected.filterNot { it in knownKeys }.sorted().map { key ->
        if (key.startsWith("uid:")) {
            UninstallChoice(key, "UID " + key.removePrefix("uid:"), "手动添加", 3)
        } else {
            UninstallChoice(key, key.removePrefix("pkg:"), "手动添加", 0)
        }
    }
    val visible = (manualChoices + choices).filter { choice ->
        val inFilter = when (filter) {
            0 -> choice.kind == 1
            1 -> choice.kind == 2
            2 -> choice.kind == 3
            else -> choice.key in selected
        }
        inFilter && (query.isBlank() || choice.title.contains(query, true) ||
            choice.summary.contains(query, true) || choice.key.contains(query, true))
    }
    fun save(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet(prefKey, next).apply()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = a::finish) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                IntroCard(
                    if (blacklist) "选中的发起方直接拒绝卸载" else "选中的发起方直接允许卸载；黑名单优先",
                )
            }
            item {
                AppSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    label = "搜索...",
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = manual,
                        onValueChange = { manual = it; inputError = null },
                        modifier = Modifier.weight(1f),
                        label = "输入包名或 UID...",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                    TextButton(
                        "添加",
                        {
                            val value = manual.trim()
                            val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
                            val rawUid = value.removePrefix("uid:").toIntOrNull()
                            val rawPackage = value.removePrefix("pkg:")
                            val entry = when {
                                rawUid != null && rawUid >= 0 -> "uid:" + rawUid
                                packagePattern.matches(rawPackage) -> "pkg:" + rawPackage
                                else -> null
                            }
                            if (entry == null) {
                                inputError = "请输入应用包名或非负数字 UID"
                            } else {
                                save(selected + entry)
                                manual = ""
                                query = ""
                                filter = 3
                            }
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
            if (inputError != null) {
                item { Text(inputError.orEmpty(), color = Color(0xFFE05252)) }
            }
            item {
                AppPickerFilterRow(
                    status = "已选择 " + selected.size + " 项 · 当前显示 " + visible.size + " 项",
                    categories = listOf("用户应用", "系统应用", "UID", "已选择"),
                    selectedIndex = filter,
                    onSelectedIndexChange = { filter = it },
                )
            }
            items(visible, key = { it.key }) { choice ->
                Card(Modifier.fillMaxWidth()) {
                    val preference: @Composable () -> Unit = {
                        CheckboxPreference(
                            checked = choice.key in selected,
                            onCheckedChange = { checked ->
                                save(if (checked) selected + choice.key else selected - choice.key)
                            },
                            title = choice.title,
                            summary = choice.summary,
                            checkboxLocation = CheckboxLocation.End,
                        )
                    }
                    if (choice.key.startsWith("pkg:")) {
                        val icon = remember(choice.key) {
                            runCatching {
                                a.packageManager.getApplicationIcon(choice.key.removePrefix("pkg:"))
                            }.getOrElse { a.packageManager.defaultActivityIcon }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AndroidView(
                                factory = { context ->
                                    ImageView(context).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        setImageDrawable(icon)
                                    }
                                },
                                update = { it.setImageDrawable(icon) },
                                modifier = Modifier.padding(start = 16.dp).size(40.dp),
                            )
                            Box(Modifier.weight(1f)) { preference() }
                        }
                    } else {
                        preference()
                    }
                }
            }
            if (visible.isEmpty()) {
                item { InfoCard("没有匹配的项目") }
            }
        }
    }
}

@Composable
private fun UninstallPrompt(
    a: UninstallPromptActivity,
    target: String?,
    uid: Int,
    packages: Array<String>?,
    deadline: Long,
) {
    val callerName = remember(uid, packages) {
        val callerPackage = packages?.firstOrNull()
        if (callerPackage != null) {
            runCatching {
                a.packageManager.getApplicationInfo(callerPackage, 0)
                    .loadLabel(a.packageManager).toString()
            }.getOrDefault(callerPackage)
        } else {
            when (uid) {
                0 -> "root"
                1 -> "daemon"
                2 -> "bin"
                3 -> "sys"
                1000 -> "system"
                2000 -> "shell"
                else -> runCatching { a.packageManager.getNameForUid(uid) }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: "进程"
            }
        }
    }
    val targetName = remember(target) {
        if (target == null) "未知应用" else runCatching {
            a.packageManager.getApplicationInfo(target, 0)
                .loadLabel(a.packageManager).toString()
        }.getOrDefault(target)
    }
    val targetIcon = remember(target) {
        if (target == null) a.packageManager.defaultActivityIcon else runCatching {
            a.packageManager.getApplicationIcon(target)
        }.getOrElse { a.packageManager.defaultActivityIcon }
    }
    var remaining by remember { mutableIntStateOf(((deadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)) }
    LaunchedEffect(deadline) {
        while (remaining > 0) {
            delay(250)
            remaining = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(0)
        }
        a.timeout()
    }
    Box(Modifier.fillMaxWidth().padding(12.dp)) {
        Card(Modifier.fillMaxWidth(), cornerRadius = 28.dp, insideMargin = PaddingValues(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("是否要允许 $callerName($uid) 卸载这个应用？", style = MiuixTheme.textStyles.title2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageDrawable(targetIcon)
                            }
                        },
                        update = { it.setImageDrawable(targetIcon) },
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(targetName, style = MiuixTheme.textStyles.body1)
                        Text(target ?: "未知包名", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                Text(
                    "剩余 $remaining 秒",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton("拒绝", { a.decide(false) }, Modifier.weight(1f))
                    TextButton(
                        "允许", { a.decide(true) }, Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPage(a: QuickCommands) = Page(a, "快捷指令执行", a::finish) {
    IntroCard("以下功能使用 root 权限执行，第一次使用请授予 Arashi 权限")
    Group("应用相关") {
        Link("禁用 PadMs", "冻结 com.zuoyebang.padms") { a.run("禁用 PadMs", "pm disable com.zuoyebang.padms") }
        Link("禁用 report服务", "冻结 com.zuoyebang.iot.pad.zpreport") { a.run("禁用 report服务", "pm disable com.zuoyebang.iot.pad.zpreport") }
        Link("禁用 系统升级", "冻结 com.zuoyebang.iot.zp.lib.ota.server") { a.run("禁用 系统升级", "pm disable com.zuoyebang.iot.zp.lib.ota.server") }
        Link("启用 系统升级", "与上方功能作用排斥") { a.run("启用 系统升级", "pm enable com.zuoyebang.iot.zp.lib.ota.server") }
    }
    Group("设置相关") {
        fun set(title: String, key: String, value: Int) =
            a.run(title, "settings put global $key $value; settings get global $key")
        Link("打开开发者模式", "打开 development_settings_enabled") { set("打开开发者模式", "development_settings_enabled", 1) }
        Link("关闭开发者模式", "关闭 development_settings_enabled") { set("关闭开发者模式", "development_settings_enabled", 0) }
        Link("打开 USB调试", "打开 adb_enabled") { set("打开 USB调试", "adb_enabled", 1) }
        Link("关闭 USB调试", "关闭 adb_enabled") { set("关闭 USB调试", "adb_enabled", 0) }
    }
    Group("投屏") {
        Link("开启投屏", "启用 SystemUI 的无线显示并添加投屏磁贴") {
            a.run("开启投屏", "settings put global wifi_display_on 1 && am broadcast --user current -a com.android.systemui.cast.Add -p com.android.systemui")
        }
        Link("关闭投屏", "与上方设置排斥") {
            a.run("关闭投屏", "settings put global wifi_display_on 0 && am broadcast --user current -a com.android.systemui.cast.Remove -p com.android.systemui")
        }
    }
    InfoCard("KSU 请手动授权，Magisk 弹窗时请允许授权；官方版 Arashi 不会执行危险指令")
}

@Composable
private fun ZybosPage(a: ZybosTools) = Page(a, "系统特权工具", a::finish) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var packageInput by remember { mutableStateOf("") }
    IntroCard("利用 ZybosSysApiManager 无需权限为本设备执行安装、卸载应用等操作")
    Group("安装") {
        Link("选择 APK 文件安装", "从文件管理器中选择 APK 安装") { a.pickApk() }
        Link("输入 APK 路径安装", "输入 APK 的路径") {
            input = ""
            packageInput = ""
            dialog = "install"
        }
    }
    Group("卸载") {
        Link("选择应用卸载", "从应用列表中选择应用卸载") { a.pickUninstallApp() }
        Link("输入包名卸载", "输入应用包名卸载") {
            input = ""
            dialog = "uninstall"
        }
    }
    InfoCard("本功能仅面向支持 ZybosSysApiManager 的 AIbangOS 版本")
    OverlayDialog(
        show = dialog != null,
        title = if (dialog == "install") "安装 APK" else "卸载应用",
        onDismissRequest = { dialog = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = if (dialog == "install") "/sdcard/Download/MyApp.apk" else "com.example.kskblzdjd",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            if (dialog == "install") {
                TextField(
                    value = packageInput,
                    onValueChange = { packageInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "应用包名（可选，路径无法读取时填写）",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton("取消", { dialog = null }, Modifier.weight(1f))
                TextButton(
                    "确定",
                    {
                        val value = input.trim()
                        val kind = dialog
                        if (value.isNotEmpty()) {
                            dialog = null
                            if (kind == "install") {
                                a.installApk(value, packageInput.trim().takeIf { it.isNotEmpty() })
                            } else {
                                a.uninstallApk(value)
                            }
                        }
                    },
                    Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun ZybosUninstallPickerPage(a: ZybosUninstallPickerActivity) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<UninstallChoice?>(null) }
    val choices = remember {
        val manager = a.packageManager
        runCatching { manager.getInstalledApplications(0) }.getOrDefault(emptyList())
            .mapNotNull { info ->
                val updatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (system && !updatedSystem) {
                    null
                } else {
                    val label = runCatching { info.loadLabel(manager).toString() }
                        .getOrDefault(info.packageName)
                    UninstallChoice(
                        info.packageName,
                        label,
                        info.packageName + " · UID " + info.uid +
                            (if (updatedSystem) " · 已更新的系统应用" else " · 用户应用"),
                        if (updatedSystem) 2 else 1,
                    )
                }
            }.sortedWith(compareBy<UninstallChoice> { it.kind }.thenBy { it.title.lowercase() })
    }
    val visible = choices.filter { choice ->
        (filter == 0 || choice.kind == filter) &&
            (query.isBlank() || choice.title.contains(query, true) ||
                choice.key.contains(query, true) || choice.summary.contains(query, true))
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "选择应用卸载",
                navigationIcon = {
                    IconButton(onClick = a::finish) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    IntroCard("可选择用户应用或已更新的系统应用执行卸载")
                }
                item {
                    AppSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        label = "搜索应用名称或包名...",
                    )
                }
                item {
                    AppPickerFilterRow(
                        status = "当前显示 " + visible.size + " 项",
                        categories = listOf("全部", "用户应用", "已更新的系统应用"),
                        selectedIndex = filter,
                        onSelectedIndexChange = { filter = it },
                    )
                }
                items(visible, key = { it.key }) { choice ->
                    val icon = remember(choice.key) {
                        runCatching { a.packageManager.getApplicationIcon(choice.key) }
                            .getOrElse { a.packageManager.defaultActivityIcon }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { pending = choice },
                        insideMargin = PaddingValues(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AndroidView(
                                factory = { context ->
                                    ImageView(context).apply {
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        setImageDrawable(icon)
                                    }
                                },
                                update = { it.setImageDrawable(icon) },
                                modifier = Modifier.size(44.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(choice.title, style = MiuixTheme.textStyles.body1)
                                Text(
                                    choice.summary,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                            }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item { InfoCard("没有匹配的应用") }
                }
            }
            OverlayDialog(
                show = pending != null,
                title = "确认卸载",
                summary = pending?.let {
                    it.title + "\n" + it.key +
                        (if (it.kind == 2) "\n将卸载更新并恢复系统预装版本" else "\n将卸载此应用")
                },
                onDismissRequest = { pending = null },
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton("取消", { pending = null }, Modifier.weight(1f))
                    TextButton(
                        "卸载",
                        {
                            val selected = pending?.key
                            pending = null
                            if (selected != null) a.choosePackage(selected)
                        },
                        Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(a: ArashiSettings) = Page(a, "应用偏好设置", a::finish) {
    val prefs = a.getSharedPreferences("ui", Context.MODE_PRIVATE)
    val hookPrefs = a.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE)
    val themeNames = listOf("默认", "开启", "关闭")
    val themeModes = listOf(0, 2, 1)
    var selectedTheme by remember {
        mutableIntStateOf(themeModes.indexOf(prefs.getInt("mode", 0)).coerceAtLeast(0))
    }
    var selectedColorScheme by remember {
        mutableIntStateOf(if (prefs.getInt("color_scheme", 0) == 1) 1 else 0)
    }
    var hidden by remember { mutableStateOf(a.isLauncherHidden) }
    var installer by remember { mutableStateOf(a.isInstallerEnabled) }
    var android12BlacklistHook by remember {
        mutableStateOf(hookPrefs.getBoolean(HookConfig.KEY_ANDROID12_BLACKLIST_HOOK, false))
    }
    val isAndroid12 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
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
    Group("外观") {
        OverlayDropdownPreference(
            items = listOf("紫色", "蓝色"),
            selectedIndex = selectedColorScheme,
            title = "配色设置",
            onSelectedIndexChange = { index ->
                selectedColorScheme = index
                prefs.edit().putInt("color_scheme", index).apply()
                a.recreate()
            },
        )
        OverlayDropdownPreference(
            items = themeNames,
            selectedIndex = selectedTheme,
            title = "夜间模式",
            onSelectedIndexChange = { index ->
                selectedTheme = index
                prefs.edit().putInt("mode", themeModes[index]).apply()
                a.recreate()
            },
        )
    }
    Group("兼容性") {
        SwitchPreference(
            checked = android12BlacklistHook,
            onCheckedChange = {
                android12BlacklistHook = it
                hookPrefs.edit().putBoolean(HookConfig.KEY_ANDROID12_BLACKLIST_HOOK, it).apply()
            },
            title = "兼容安卓12清除安装黑名单",
            summary = if (isAndroid12) {
                "安卓12改用 PMS 方法，是否清除仍由 Hook 功能设置控制。修改后重启生效"
            } else {
                "本功能仅适用于安卓12，当前系统默认使用对应的 Hook 路径"
            },
            enabled = isAndroid12,
        )
    }
    Group("其他") {
        SwitchPreference(
            checked = hidden,
            onCheckedChange = { requested ->
                if (!isLsposedActive(a)) {
                    moduleActive = false
                } else {
                    try {
                        a.setLauncherHidden(requested)
                        hidden = requested
                    } catch (error: RuntimeException) {
                        MiuixPages.showMessage(a, "切换失败", "无法更改桌面图标状态：${error.message}")
                    }
                }
            },
            title = "隐藏桌面图标",
            summary = if (moduleActive) "隐藏后仍可从 LSPosed 管理器打开" else "需先激活 LSPosed 才能隐藏桌面图标",
            enabled = moduleActive,
        )
        val installerBlocked = SystemToolSupport.isBlocked()
        Box(Modifier.fillMaxWidth().clickable(enabled = installerBlocked) {
            MiuixPages.showMessage(a, "暂不可用", SystemToolSupport.MESSAGE)
        }) {
        SwitchPreference(
            checked = installer && !installerBlocked,
            enabled = !installerBlocked,
            onCheckedChange = { requested ->
                try {
                    a.setInstallerEnabled(requested)
                    installer = requested
                } catch (error: RuntimeException) {
                    MiuixPages.showMessage(a, "切换失败", "无法更改安装器状态：${error.message}")
                }
            },
            title = "启用 Arashi 安装器",
            summary = "启用后可在打开 APK 文件时选择 Arashi 作为安装器",
        )
        }
    }
}

@Composable
private fun AboutCard(
    title: String,
    value: String,
    arrow: Boolean = false,
    externalLink: Boolean = false,
    compactValue: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(88.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(5.dp))
                Text(
                    value,
                    style = if (compactValue) MiuixTheme.textStyles.body1 else MiuixTheme.textStyles.title2,
                )
            }
            if (arrow || externalLink) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    if (externalLink) MiuixIcons.Link else MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = if (externalLink) Modifier.size(22.dp)
                        else Modifier.size(width = 10.dp, height = 16.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

@Composable
private fun AboutPage(a: About) = Page(a, "关于 Arashi", a::finish) {
    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapAt by remember { mutableLongStateOf(0L) }
    AboutCard("版本号", BuildConfig.VERSION_NAME, onClick = {
            val now = SystemClock.elapsedRealtime()
            versionTapCount = if (now - lastVersionTapAt <= 1200L) versionTapCount + 1 else 1
            lastVersionTapAt = now
            if (versionTapCount >= 7) {
                versionTapCount = 0
                lastVersionTapAt = 0L
                MiuixPages.showMessage(a, "棍母棍母棍母", "棍母棍母棍母")
            }
    })
    AboutCard("作者", "李瑾沐", arrow = true, onClick = {
        MiuixPages.showMessage(a, "关于作者", "首席开发者 : 李瑾沐\n小红书号 : failgacha\nQQ 昵称 : H...Hey")
    })
    AboutCard("QQ 群聊", "加入 Arashi 用户交流与反馈群", externalLink = true, compactValue = true, onClick = {
        val url = "https://qun.qq.com/universal-share/share?ac=1&authKey=i%2Bh3ZyTelsMfhjWvc8srNCHnS4BMGrsr4hj1fDcEXx0fC5ECgHyUjh45kS5qiQ3W&busi_data=eyJncm91cENvZGUiOiIxMTI1MTkwMzUwIiwidG9rZW4iOiIrRFg2KzdBTHhRRlZKODZjUUt2U2gySFRBaHFBeFNIYTdFTFc0emcydXJuTEZsN1dvS3MwT1J4ZFF2dnNJNUhOIiwidWluIjoiODQxNTYzNjAifQ%3D%3D&data=PB0LIOhizgFR_GzQFzdDn6FEbw0rzkzMw5JbqCzMZp0t72lHOOiSfSqMcISpvsGOvjkcACA_44t9aKGSV3GaNQ&svctype=4&tempid=h5_group_info"
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            MiuixPages.showMessage(a, "无法打开链接", "请先安装可打开网页的应用")
        }
    })
}

@Composable
private fun InstallerPage(
    a: ApkInstallerActivity,
    path: String,
    packageName: String?,
    label: String,
    icon: Drawable,
) = Page(a, "安装应用", a::finish) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(icon)
                    }
                },
                update = { it.setImageDrawable(icon) },
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MiuixTheme.textStyles.title2)
                Spacer(Modifier.height(5.dp))
                if (packageName != null) {
                    Text(packageName, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton("取消", a::finish, Modifier.weight(1f))
        TextButton(
            "继续",
            { a.submitInstallFromMiuix(path, packageName, label, icon) },
            Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun InstallerProgressPage(
    a: ApkInstallerActivity,
    label: String,
    packageName: String?,
    icon: Drawable?,
) = Page(a, "正在安装") {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(icon)
                        }
                    },
                    update = { it.setImageDrawable(icon) },
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MiuixTheme.textStyles.title2)
                if (packageName != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(packageName, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("正在安装…", style = MiuixTheme.textStyles.title3)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), height = 8.dp)
            Text(
                "安装包较大时可能需要一些时间",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
