package com.lijinmu.arashi.ui

import com.lijinmu.arashi.BuildConfig
import com.lijinmu.arashi.Main
import com.lijinmu.arashi.UninstallPromptActivity
import com.lijinmu.arashi.settings.HookSettings
import com.lijinmu.arashi.settings.BackgroundWhitelistActivity
import com.lijinmu.arashi.xposed.BackgroundPolicy
import com.lijinmu.arashi.tools.QuickCommands
import com.lijinmu.arashi.tools.ScopedAppStopper
import com.lijinmu.arashi.tcp.TcpHistoryActivity
import com.lijinmu.arashi.uninstall.UninstallHistoryActivity
import com.lijinmu.arashi.uninstall.UninstallListActivity
import com.lijinmu.arashi.uninstall.UninstallSettings

import android.app.Activity
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lijinmu.arashi.xposed.HookConfig
import com.lijinmu.arashi.xposed.HookScopes
import com.lijinmu.arashi.xposed.SettingsProvider
import com.lijinmu.arashi.uninstall.UninstallHistoryStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.WeakHashMap
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.lijinmu.arashi.uninstall.UninstallConfig

object MiuixPages {
    private data class Message(
        val title: String,
        val body: String,
        val buttonLabel: String = "知道了",
        val loading: Boolean = false,
        val feedback: Boolean = false,
        val id: Long = System.nanoTime(),
    )
    private val messages = WeakHashMap<Activity, MutableState<Message?>>()

    @JvmStatic fun renderHome(a: Main) = render(a) { MainNavigationPage(a) }
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
    @JvmStatic fun renderZybosEnvController(a: com.lijinmu.arashi.tools.ZybosEnvController) = render(a) { ZybosEnvPage(a) }

    @JvmStatic fun renderQuickCommands(a: QuickCommands) = render(a) { QuickPage(a) }

    @JvmStatic fun renderUnsupportedDevice(a: Main) = render(a) { UnsupportedDevicePage(a) }
    @JvmStatic fun showMessage(a: Activity, title: String, body: String) {
        com.lijinmu.arashi.logs.RuntimeLogger.record(
            if (title.contains("失败") || title.contains("错误")) "WARN" else "INFO",
            "Operation", (title + "\n" + body).trim(), null,
        )
        a.runOnUiThread { messageState(a).value = Message(title, body, feedback = true) }
    }

    @JvmStatic fun showDetails(a: Activity, title: String, body: String) {
        a.runOnUiThread { messageState(a).value = Message(title, body) }
    }

    @JvmStatic fun showProgress(a: Activity, title: String, detail: String) {
        a.runOnUiThread { messageState(a).value = Message(title, detail, loading = true) }
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
            if (a is Main) id = android.R.id.content
            setContent {
                val uiPrefs = remember(a) { a.getSharedPreferences("ui", Context.MODE_PRIVATE) }
                var mode by remember(uiPrefs) { mutableIntStateOf(uiPrefs.getInt("mode", 0)) }
                var colorScheme by remember(uiPrefs) {
                    mutableIntStateOf(uiPrefs.getInt("color_scheme", 2))
                }
                DisposableEffect(uiPrefs) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                        when (key) {
                            "mode" -> mode = prefs.getInt("mode", 0)
                            "color_scheme" -> colorScheme = prefs.getInt("color_scheme", 2)
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
                val systemTheme = remember(dark) {
                    ThemeController(if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight)
                }
                val colors = if (colorScheme == 2) {
                    systemTheme.currentColors()
                } else if (!usePurple) {
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
                        applySystemBars(a, dark, colors.background.toArgb())
                    }
                }
                MiuixTheme(colors = colors) {
                    if (a is UninstallPromptActivity) {
                        page()
                    } else {
                        // Paint behind transparent system bars as well as the page.
                        Box(Modifier.fillMaxSize().background(colors.background)) { page() }
                    }
                }
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars(a: Activity, dark: Boolean, background: Int) {
        a.window.statusBarColor = android.graphics.Color.TRANSPARENT
        a.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Draw the chosen background directly, without Android's contrast scrim.
        a.window.isStatusBarContrastEnforced = false
        a.window.isNavigationBarContrastEnforced = false
        a.window.setBackgroundDrawable(ColorDrawable(background))
        val lightAppearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        a.window.insetsController?.setSystemBarsAppearance(
            if (dark) 0 else lightAppearance,
            lightAppearance,
        )
    }

    @Composable
    fun MessageDialog(a: Activity) {
        val message = messageState(a)
        val current = message.value
        val feedbackHost = remember(current?.id) { SnackbarHostState() }
        LaunchedEffect(current?.id) {
            val result = current ?: return@LaunchedEffect
            if (!result.feedback) return@LaunchedEffect
            val body = result.body.trim()
            val detailed = body.length > 80 || body.lines().size > 2
            val preview = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            val summary = if (detailed) {
                (if (result.title.isBlank()) "" else result.title + if (preview.isEmpty()) "" else "：") +
                    preview.take(70) + if (body.length > preview.take(70).length) "…" else ""
            } else if (result.title.isBlank()) {
                body
            } else {
                result.title + if (body.isEmpty() || body == result.title) "" else "\n" + body
            }
            val response = feedbackHost.showSnackbar(
                message = summary,
                actionLabel = if (detailed) "查看详情" else null,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (message.value?.id == result.id) {
                message.value = if (response == top.yukonga.miuix.kmp.basic.SnackbarResult.ActionPerformed)
                    result.copy(feedback = false, id = System.nanoTime()) else null
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SnackbarHost(feedbackHost)
        }
        // Visibility can change immediately, but the overlay still draws its
        // content during the exit animation. Retain the last displayed message
        // until replacement or composition disposal rather than blanking it.
        var retained by remember(a) { mutableStateOf(current) }
        SideEffect {
            if (current != null) retained = current
        }
        val displayed = current ?: retained
        OverlayDialog(
            show = current != null && !current.feedback,
            title = displayed?.title,
            summary = displayed?.body,
            onDismissRequest = { message.value = null },
        ) {
            if (displayed?.loading == true) {
                Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(displayed.title)
                }
            }
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
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    overlay: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (back != null) {
                        IconButton(onClick = back) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    }
                },
                actions = actions,
            )
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = LocalNavigationContentPadding.current),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = body) }
            }
            MiuixPages.MessageDialog(activity)
            overlay()
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
private fun Link(
    title: String,
    summary: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    action: () -> Unit,
) = ArrowPreference(
    title = title,
    summary = summary,
    startAction = if (icon == null) null else {
        {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 16.dp).size(24.dp),
            )
        }
    },
    onClick = action,
)

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

// Enum names form a stable, Bundle-saveable route format without reflection/serialization.
internal enum class ArashiRoute : NavKey {
    Main, HookSettings, BackgroundWhitelist, TcpHistory, UninstallSettings,
    UninstallWhitelist, UninstallBlacklist, UninstallHistory, Notifications, About, Logs, QuickCommands, ZybosEnv,
}

private val ArashiBackStackSaver = Saver<NavBackStack, ArrayList<String>>(
    save = { stack -> ArrayList(stack.map { (it as ArashiRoute).name }) },
    restore = { names ->
        val routes = names.mapNotNull { name -> ArashiRoute.entries.find { it.name == name } }
        navBackStackOf(*routes.ifEmpty { listOf(ArashiRoute.Main) }.toTypedArray())
    },
)

@Composable
private fun MainNavigationPage(a: Main) {
    val stack = rememberSaveable(saver = ArashiBackStackSaver) { navBackStackOf(ArashiRoute.Main) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val open: (ArashiRoute) -> Unit = { route ->
        // The library requires unique entry identities, including during rapid repeated taps.
        if (route !in stack) stack.add(route)
    }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl)
        NavSwipeDirection.RightToLeft else NavSwipeDirection.LeftToRight
    NavDisplay(
        backStack = stack,
        modifier = Modifier.fillMaxSize(),
        onBack = back,
        effects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            backdropColor = MiuixTheme.colorScheme.background,
        ),
    ) {
        entry<ArashiRoute>(swipeDismiss = direction) { destination ->
            when (destination) {
                ArashiRoute.HookSettings -> HookPage(a, back,
                    { open(ArashiRoute.TcpHistory) }, { open(ArashiRoute.BackgroundWhitelist) })
                ArashiRoute.BackgroundWhitelist -> BackgroundWhitelistPage(a, back)
                ArashiRoute.TcpHistory -> TcpHistoryPage(a, back)
                ArashiRoute.UninstallSettings -> UninstallPage(a, back,
                    { open(ArashiRoute.UninstallWhitelist) }, { open(ArashiRoute.UninstallBlacklist) },
                    { open(ArashiRoute.UninstallHistory) })
                ArashiRoute.UninstallWhitelist -> UninstallListPage(a, false, back)
                ArashiRoute.UninstallBlacklist -> UninstallListPage(a, true, back)
                ArashiRoute.UninstallHistory -> UninstallHistoryPage(a, back)
                ArashiRoute.QuickCommands -> QuickPage(a, back, { open(ArashiRoute.ZybosEnv) })
                ArashiRoute.ZybosEnv -> ZybosEnvPage(a, back)
                ArashiRoute.Notifications -> NotificationSettingsPage(a, back)
                ArashiRoute.About -> AboutPage(a, back)
                ArashiRoute.Logs -> RuntimeLogPage(a, back)
                ArashiRoute.Main -> ArashiNavigation(a) { page ->
                    if (page == 0) HomePage(a, bottomBar = {},
                        onHookSettings = { open(ArashiRoute.HookSettings) },
                        onQuickCommands = { open(ArashiRoute.QuickCommands) },
                        onUninstallSettings = { open(ArashiRoute.UninstallSettings) })
                    else if (page == 1) JailbreakPage(a)
                    else SettingsPage(a,
                        onNotifications = { open(ArashiRoute.Notifications) },
                        onAbout = { open(ArashiRoute.About) },
                        onLogs = { open(ArashiRoute.Logs) })
                }
            }
        }
    }
}


/** Guided UI only: no privileged native code is loaded or executed here. */
@Composable
private fun JailbreakPage(a: Main) = Page(a, "越狱") {
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var deviceModel by remember { mutableStateOf("") }
    var androidVersion by remember { mutableStateOf("") }
    var kernelVersion by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    var managerInstalled by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val error = stage == 1 || stage == 6
    val cardColor by animateColorAsState(
        targetValue = if (error) Color(0xFFB3261E).copy(alpha = 0.20f)
            else MiuixTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 320),
        label = "jailbreakCardColor",
    )
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(360, easing = FastOutSlowInEasing)),
        insideMargin = PaddingValues(20.dp),
        colors = CardDefaults.defaultColors(color = cardColor, contentColor = MiuixTheme.colorScheme.onSurface),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) +
                        slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 9 })
                        .togetherWith(
                            fadeOut(tween(160)) +
                                slideOutHorizontally(tween(240)) { -it / 9 }
                        ).using(SizeTransform(clip = false))
                },
                label = "jailbreakStage",
            ) { visibleStage ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (visibleStage) {
                0 -> {
                    Text("在不解锁BL、不修改系统的情况下使用KernelSU的越狱模式。")
                }
                1 -> Text(problem)
                2 -> {
                    Text("请核对信息")
                    Text("品牌：作业帮")
                    Text("型号：$deviceModel")
                    Text("Android版本：$androidVersion")
                    Text("内核版本：$kernelVersion")
                }
                3 -> Text("Arashi 即将开始提权操作。")
                4 -> {
                    Text("正在处理…")
                    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "jailbreakProgress")
                    val progress by infinite.animateFloat(
                        initialValue = 0f, targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(1300),
                        ), label = "progress",
                    )
                    val progressColor = MiuixTheme.colorScheme.primary
                    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                        drawRoundRect(Color.Gray.copy(alpha = 0.25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                        val left = (size.width + size.width * 0.32f) * progress - size.width * 0.32f
                        drawRoundRect(progressColor,
                            topLeft = Offset(left, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.32f, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                    }
                }
                5 -> Text("提权完毕！！！请前往 KernelSU 继续。")
                6 -> Text("提权失败，当前版本尚未接入 DirtyFrag VR 执行组件。")
                7 -> Text("未安装KernelSU，请安装！")
                }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (stage) {
                    0 -> TextButton(if (checking) "正在检查…" else "继续", {
                        if (checking) return@TextButton
                        checking = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val kernel = System.getProperty("os.version")
                                    ?: android.system.Os.uname().release
                                val model = Build.MODEL ?: ""
                                val androidRelease = Build.VERSION.RELEASE ?: ""
                                val supported = kernel.startsWith("5.10.") && kernel.contains("android12")
                                val installed = try {
                                    a.packageManager.getPackageInfo("me.weishu.kernelsu", 0)
                                    true
                                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                                    false
                                }
                                arrayOf(kernel, model, androidRelease, supported.toString(), installed.toString())
                            }
                            kernelVersion = result[0]
                            deviceModel = result[1]
                            androidVersion = result[2]
                            managerInstalled = result[4].toBoolean()
                            problem = when {
                                deviceModel.isBlank() || kernelVersion.isBlank() -> "读取设备信息失败"
                                result[3] != "true" -> "内核版本不支持，在该设备上无法使用越狱模式！"
                                else -> ""
                            }
                            checking = false
                            stage = when {
                                problem.isNotBlank() -> 1
                                !managerInstalled -> 7
                                else -> 2
                            }
                        }
                    })
                    1 -> TextButton("重试", { stage = 0 })
                    2 -> TextButton("继续", { stage = 3 })
                    3 -> TextButton("继续", { stage = 6 })
                    5 -> TextButton("KernelSU ↗", {
                        a.packageManager.getLaunchIntentForPackage("me.weishu.kernelsu")?.let(a::startActivity)
                    })
                    6 -> TextButton("返回", { stage = 0 })
                    7 -> {
                        TextButton("返回", { stage = 0 })
                        TextButton("安装", {
                            a.startActivity(Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/tiann/KernelSU/releases")))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    a: Main,
    bottomBar: @Composable () -> Unit,
    onHookSettings: () -> Unit,
    onQuickCommands: () -> Unit,
    onUninstallSettings: () -> Unit,
) =
    Page(a, "Arashi for Zybos", bottomBar = bottomBar) {
    val moduleActive by rememberSystemServerActivation(a)
    val statusColor = if (moduleActive) MiuixTheme.colorScheme.primary else Color.White
    val statusBackground = if (moduleActive) MiuixTheme.colorScheme.tertiaryContainer
        else Color(0xFFD32F2F)
    val versionColor = if (moduleActive) MiuixTheme.colorScheme.onSurfaceVariantSummary
        else Color.White.copy(alpha = 0.85f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = statusBackground,
            contentColor = if (moduleActive) MiuixTheme.colorScheme.onSurface else Color.White,
        ),
    ) {
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            // Decorative status watermark: it does not duplicate accessibility text.
            Canvas(Modifier.matchParentSize()) {
                val extent = minOf(size.height * 1.25f, 190.dp.toPx())
                val left = size.width - extent * 0.92f
                val top = (size.height - extent) / 2f
                val watermark = statusColor.copy(alpha = 0.13f)
                if (moduleActive) {
                    val center = Offset(left + extent * 0.54f, top + extent * 0.5f)
                    drawCircle(watermark, radius = extent * 0.34f, center = center,
                        style = Stroke(width = extent * 0.07f))
                    val check = Path().apply {
                        moveTo(center.x - extent * 0.17f, center.y)
                        lineTo(center.x - extent * 0.04f, center.y + extent * 0.13f)
                        lineTo(center.x + extent * 0.18f, center.y - extent * 0.13f)
                    }
                    drawPath(check, watermark, style = Stroke(
                        width = extent * 0.07f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ))
                } else {
                    val center = Offset(left + extent * 0.54f, top + extent * 0.5f)
                    drawCircle(watermark, radius = extent * 0.34f, center = center,
                        style = Stroke(width = extent * 0.10f))
                    drawLine(watermark,
                        Offset(center.x, center.y - extent * 0.13f),
                        Offset(center.x, center.y + extent * 0.03f),
                        strokeWidth = extent * 0.07f, cap = StrokeCap.Round)
                    drawCircle(watermark, radius = extent * 0.04f,
                        center = Offset(center.x, center.y + extent * 0.16f))
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("LSPosed", color = statusColor, style = MiuixTheme.textStyles.body1)
                Text(
                    if (moduleActive) "已激活" else "未激活",
                    color = statusColor,
                    style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}",
                    color = versionColor,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
    Group("功能") {
        Link("Hook 设置", "控制 Hook 是否启用", Icons.Rounded.Extension) { onHookSettings() }
        Link("快捷指令执行", "调整设置、冻结应用等快捷操作", Icons.Rounded.Terminal) { onQuickCommands() }
        Link("卸载验证设置", "验证发起的卸载请求", Icons.Rounded.Delete) { onUninstallSettings() }
    }
}

@Composable
private fun UnsupportedDevicePage(a: Main) = Page(a, "Arashi") {
    OverlayDialog(
        show = true,
        summary = "当前系统不支持 Arashi",
        onDismissRequest = a::exitUnsupportedDevice,
    ) {
        TextButton(
            "确定",
            a::exitUnsupportedDevice,
            Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

private data class HookItem(val title: String, val summary: String, val key: String)

@Composable
private fun HookPage(
    a: ComponentActivity,
    back: () -> Unit = a::finish,
    onTcpHistory: () -> Unit = { a.startActivity(Intent(a, TcpHistoryActivity::class.java)) },
    onBackgroundWhitelist: () -> Unit = { a.startActivity(Intent(a, BackgroundWhitelistActivity::class.java)) },
) {
    LaunchedEffect(a) {
        if (a.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL, false) &&
            com.lijinmu.arashi.notifications.NotificationPreferences.enabled(a,
                com.lijinmu.arashi.notifications.NotificationPreferences.TCP)) {
            com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(a)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsExpanded by remember { mutableStateOf(false) }
    var stoppingScopes by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Page(a, "Hook 设置", back,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
        Box {
            IconButton(
                onClick = { actionsExpanded = !actionsExpanded },
                modifier = Modifier.semantics { contentDescription = "更多操作" },
            ) {
                Icon(
                    imageVector = MiuixIcons.MoreCircle,
                    contentDescription = "更多操作",
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            OverlayDropdownPopup(
                entry = DropdownEntry(listOf(
                    DropdownItem(
                        text = "杀死后台进程",
                        selected = false,
                        onClick = {
                            actionsExpanded = false
                            if (!stoppingScopes) {
                                stoppingScopes = true
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { ScopedAppStopper.stop() }
                                    } finally {
                                        stoppingScopes = false
                                    }
                                }
                            }
                        },
                    ),
                )),
                show = actionsExpanded,
                onDismiss = { actionsExpanded = false },
                onDismissFinished = {},
                maxHeight = null,
                dropdownColors = DropdownDefaults.dropdownColors(),
                renderInRootScaffold = true,
            )
        }
    }) {
    IntroCard("首次使用请启用模块并勾选推荐作用域并重启系统。修改应用作用域后需重启对应应用，部分需要重启系统")
    AppSearchBar(
        query = query,
        onQueryChange = { query = it },
        label = "搜索…",
    )
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
    val developerMenuTitle = "注入开发者选项入口"
    val developerMenuSummary = "在系统设置的左侧菜单中添加 “开发者选项” 入口"
    val tcpHistoryTitle = "TCP 拦截记录"
    val tcpHistorySummary = "查看触发时间、错误码、响应状态和调用详情"
    val backgroundListTitle = "后台保留白名单"
    val backgroundListSummary = if (backgroundSelected.isEmpty()) "目前划卡后保留所有应用后台进程"
        else "目前划卡后只保留已选择的 ${backgroundSelected.size} 个应用"
    val groups = listOf(
        "安装" to listOf(
            HookItem("允许外部应用安装", "绕过 PackageInstaller 安装限制，允许安装外部应用", HookConfig.KEY_EXTERNAL_INSTALL),
            HookItem("允许 Shell 安装", "绕过系统的 Shell 安装限制，允许 adb 和 pm install 安装应用", HookConfig.KEY_SHELL_INSTALL),
            HookItem("解除安装黑名单限制", "允许安装被硬编码在 mZybBlacklistApp 中的包名", HookConfig.KEY_CLEAR_BLACKLIST),
        ),
        "行为" to listOf(
            HookItem("允许打开开发者选项", "绕过系统设置检查，允许打开开发者选项的 Activity", HookConfig.KEY_DEV_OPTIONS),
            HookItem("保留后台进程", "拦截系统在划卡后杀死应用后台进程", HookConfig.KEY_KEEP_BACKGROUND),
            HookItem("允许 USB 文件传输", "允许挂载外部 USB 设备和使用数据传输", HookConfig.KEY_ALLOW_USB),
            HookItem("允许任意应用设置壁纸", "允许任意应用设置壁纸，并移除新版桌面覆盖系统壁纸的静态背景。需要系统框架和桌面作用域", HookConfig.KEY_ALLOW_WALLPAPER),
            HookItem("允许 WiFi 网页登录", "允许连接到认证型 WiFi 并正常跳转到 “登录到网络” 登录验证网页", HookConfig.KEY_CAPTIVE_PORTAL),
            HookItem("始终使用传统电源菜单", "将长按电源键与下拉面板的电源菜单改为系统传统对话框。需要系统框架和系统界面作用域", HookConfig.KEY_LEGACY_POWER_MENU),
            HookItem("原版应用详情页设置", "恢复完整的 AOSP 原版应用信息页及应用通知设置，沿用系统控制器", HookConfig.KEY_AOSP_APP_DETAILS),
            HookItem("解除应用详情页限制", "允许打开 WebView、report服务 和 PadMs 的应用设置详情页", HookConfig.KEY_CLEAR_DETAIL_BLACK),
            HookItem("恢复录屏声音", "修复 SystemUI 录屏菜单的设备声音、麦克风及混合音源选择选项", HookConfig.KEY_RECORD_INTERNAL_AUDIO),
						HookItem("恢复通知快捷回复", "修复 SystemUI 通知回复框出现后立即关闭的问题", HookConfig.KEY_NOTIFICATION_REPLY),
            HookItem("阻止重设默认浏览器", "拦截 SystemUI 开机时将 “学习网站” 设为默认浏览器", HookConfig.KEY_BLOCK_DEFAULT_BROWSER),
            HookItem("阻止禁用系统导航与下拉面板", "拦截返回、主页、最近任务及下拉面板的禁用限制。需要系统框架、系统界面和桌面的作用域", HookConfig.KEY_KEEP_NAVIGATION),
						HookItem("恢复通知长按操作", "允许使用通知卡片的长按菜单", HookConfig.KEY_NOTIFICATION_LONG_PRESS),
            HookItem("恢复 Monet 颜色", "修复基于壁纸的动态配色", HookConfig.KEY_SYSTEMUI_MONET),
        ),
        "美化" to listOf(
            HookItem("原版状态栏图标", "将电池、WLAN、蓝牙图标恢复为 AOSP 原版样式", HookConfig.KEY_AOSP_STATUS_ICONS),
            HookItem("解除通知图标数量限制", "取消普通状态栏通知图标的固定数量上限，按可用宽度显示", HookConfig.KEY_UNLIMITED_NOTIFICATION_ICONS),
            HookItem("桌面图标圆角", "桌面应用图标与文件夹的改成 iOS 样式的圆角，文件夹使用半透明底色", HookConfig.KEY_IOS_ICONS),
            HookItem("系统应用图标遮罩", "将已勾选作用域内的自适应应用图标遮罩改为与桌面相同的 iOS 连续圆角，重启对应应用后生效", HookConfig.KEY_IOS_SYSTEM_ICON_MASK),
            HookItem("状态栏显示日期", "仿 iPadOS 在状态栏左侧时间后显示月日与星期，如 16:40  10月17日周六", HookConfig.KEY_STATUS_BAR_DATE),
        ),
        "TCP 服务" to listOf(
            HookItem("屏蔽锁机广播", "拦截 ZpTcpService 发出的所有 login.fail 广播，以此防止云控锁机", HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL),
        ),
        "管控服务" to listOf(
            HookItem("拦截管控服务行为", "从 SystemServer 层拦截管控服务的强杀应用和移除任务行为", HookConfig.KEY_BLOCK_SUPERVISE),
            HookItem("解除应用禁用", "将应用可用查询结果改为允许，绕过应用禁用和时长限制。已禁用的应用要先从其他应用打开一次才能从桌面启动", HookConfig.KEY_NEUTRALIZE_POLICY),
            HookItem("解除页面屏蔽", "将受限页面查询结果置空，允许打开被屏蔽的页面", HookConfig.KEY_NEUTRALIZE_ADVANCE),
            HookItem("阻止超时锁屏", "拦截管控服务的超时处理，达到整机时长限制或超出可用时段后不锁屏", HookConfig.KEY_NO_TIMEOUT_LOCK),
        ),
    )
    val searchQuery = query.trim()
    val visibleGroups = groups.mapNotNull { (name, entries) ->
        val matching = entries.filter { entry ->
            val relatedText = when (entry.key) {
                HookConfig.KEY_DEV_OPTIONS -> developerMenuTitle + " " + developerMenuSummary
                HookConfig.KEY_KEEP_BACKGROUND -> backgroundListTitle + " " + backgroundListSummary
                HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL -> tcpHistoryTitle + " " + tcpHistorySummary
                else -> ""
            }
            searchQuery.isEmpty() || entry.title.contains(searchQuery, ignoreCase = true) ||
                entry.summary.contains(searchQuery, ignoreCase = true) ||
                relatedText.contains(searchQuery, ignoreCase = true)
        }
        if (matching.isEmpty()) null else name to matching
    }
    if (visibleGroups.isEmpty()) {
        InfoCard("没有匹配的 Hook 功能")
    }
    visibleGroups.forEach { (name, entries) ->
        key(name) {
            Group(name) {
                entries.forEach { entry ->
                    key(entry.key) {
                        val available = scopes.isAvailable(entry.key)
                        var checked by remember(entry.key) {
                            mutableStateOf(
                                if (entry.key == HookConfig.KEY_AOSP_STATUS_ICONS)
                                    HookConfig.statusBarIconsEnabled(prefs.all)
                                else prefs.getBoolean(entry.key, HookConfig.defaultEnabled(entry.key)),
                            )
                        }
                        DisposableEffect(prefs, entry.key) {
                            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                if (key == entry.key) {
                                    a.runOnUiThread {
                                        checked = prefs.getBoolean(entry.key, HookConfig.defaultEnabled(entry.key))
                                    }
                                }
                            }
                            prefs.registerOnSharedPreferenceChangeListener(listener)
                            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                        }
                        fun saveHook(enabled: Boolean) {
                            checked = enabled
                            prefs.edit().putBoolean(entry.key, enabled).apply()
                            val effectMessage = when (entry.key) {
                                HookConfig.KEY_NOTIFICATION_LONG_PRESS,
                                HookConfig.KEY_SYSTEMUI_MONET,
                                HookConfig.KEY_AOSP_STATUS_ICONS -> "重启 SystemUI 后生效"
                                HookConfig.KEY_CLEAR_BLACKLIST -> "重启系统后生效"
                                HookConfig.KEY_STATUS_BAR_DATE -> "下一次时钟刷新后生效"
                                HookConfig.KEY_IOS_ICONS -> "重启桌面后生效"
                                HookConfig.KEY_IOS_SYSTEM_ICON_MASK -> "重启对应应用后生效"
                                else -> null
                            }
                            if (effectMessage != null && scopes.isAvailable(entry.key)) {
                                coroutineScope.launch {
                                    snackbar.newestSnackbarData()?.dismiss()
                                    snackbar.showSnackbar(effectMessage)
                                }
                            }
                            if (entry.key == HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL && enabled &&
                                com.lijinmu.arashi.notifications.NotificationPreferences.enabled(a, com.lijinmu.arashi.notifications.NotificationPreferences.TCP)) {
                                com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(a)
                            }

                        }
                        Box {
                            SwitchPreference(
                                checked = available && checked,
                                onCheckedChange = { enabled ->
                                    if (available) saveHook(enabled)
                                },
                                title = entry.title,
                                summary = entry.summary,
                                enabled = available,
                            )
                            if (!available) {
                                Box(
                                    Modifier.matchParentSize()
                                        .semantics { contentDescription = entry.title + "，不可用，点击查看原因" }
                                        .clickable {
                                            coroutineScope.launch {
                                                snackbar.newestSnackbarData()?.dismiss()
                                                snackbar.showSnackbar(scopes.unavailableReason(entry.key))
                                            }
                                        },
                                )
                            }
                        }
                        if (entry.key == HookConfig.KEY_BLOCK_TCP_LOGIN_FAIL) {
                            ArrowPreference(
                                title = tcpHistoryTitle,
                                summary = tcpHistorySummary,
                                onClick = onTcpHistory,
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
                                visible = available && checked,
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
                                    checked = available && injectDeveloperMenu,
                                    onCheckedChange = {
                                        if (available) {
                                            injectDeveloperMenu = it
                                            prefs.edit().putBoolean(HookConfig.KEY_DEV_OPTIONS_MENU, it).apply()
                                        }
                                    },
                                    title = developerMenuTitle,
                                    summary = developerMenuSummary,
                                    enabled = available,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = entry.key == HookConfig.KEY_KEEP_BACKGROUND && available && checked,
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
                                title = backgroundListTitle,
                                summary = backgroundListSummary,
                                onClick = {
                                    onBackgroundWhitelist()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    InfoCard("开关修改后自动保存")
}
}

private data class BackgroundChoice(val packageName: String, val label: String, val isSystem: Boolean? = null)

@Composable
private fun PruneMissingAppSelections(
    a: androidx.activity.ComponentActivity,
    selected: Set<String>,
    prefixed: Boolean,
    onPruned: (Set<String>) -> Unit,
    refreshKey: Any? = null,
) {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(a) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        a.lifecycle.addObserver(observer)
        onDispose { a.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(a, selected, prefixed, revision, refreshKey) {
        val missing = withContext(Dispatchers.IO) {
            com.lijinmu.arashi.settings.InstalledAppSelections.findMissing(
                a.packageManager, selected, prefixed)
        }
        // A selection change cancels the previous check; never replace new edits
        // with the result of an older snapshot.
        if (missing.isNotEmpty()) onPruned(selected - missing)
    }
}

@Composable
private fun AppPickerCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    summary: String,
    icon: Drawable?,
) {
    Card(Modifier.fillMaxWidth()) {
        // The icon is inside the preference's hit/press area, not a sibling.
        CheckboxPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            summary = summary,
            checkboxLocation = CheckboxLocation.End,
            startAction = if (icon == null) null else {
                {
                    AndroidView(
                        factory = { context ->
                            ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                        },
                        update = { it.setImageDrawable(icon) },
                        modifier = Modifier.padding(end = 16.dp).size(40.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun BackgroundWhitelistPage(a: ComponentActivity, back: () -> Unit = a::finish) {
    val prefs = a.getSharedPreferences(HookConfig.PREF_NAME, Context.MODE_PRIVATE)
    var selected by remember {
        mutableStateOf(prefs.getStringSet(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, emptySet()).orEmpty().toSet())
    }
    // Freeze selected-first ranking for this visit so checkbox changes never move rows.
    val selectedOnEntry = remember(a) { selected.toSet() }
    var query by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    val apps = rememberAppPickerData(a) {
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
    val choices = apps.choices
    val knownPackages = remember(choices) { choices.mapTo(HashSet<String>()) { it.packageName } }
    val missing = selected.filterNot { it in knownPackages || BackgroundPolicy.isExempt(it) }
        .sorted().map { BackgroundChoice(it, "未安装或不可见的应用") }
    val visible = (missing + choices).filter {
        val matchesCategory = showSystemApps || it.isSystem == false
        matchesCategory && (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
    }.sortedWith(
        compareBy<BackgroundChoice> { if (it.packageName in selectedOnEntry) 0 else 1 }
            .thenBy { if (it.isSystem == true) 1 else 0 }
            .thenBy { it.label.lowercase() }
            .thenBy { it.packageName },
    )
    fun save(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet(HookConfig.KEY_KEEP_BACKGROUND_WHITELIST, next).apply()
    }
    PruneMissingAppSelections(a, selected, prefixed = false, onPruned = ::save, refreshKey = apps.generation)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "后台保留白名单",
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    AppPickerFilterMenu(showSystemApps, onShowSystemAppsChange = { showSystemApps = it })
                },
            )
        },
    ) { innerPadding ->
        AppPickerRefreshList(
            data = apps,
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            item {
                IntroCard(
                    "选中的应用划卡后不会被强杀，不选择任何应用时默认全不杀，系统原有豁免的应用（微信、QQ等）不显示。选择后立即保存")
            }
            item {
                AppSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    label = "搜索应用名或包名",
                )
            }
            item {
                AppPickerStatus(if (selected.isEmpty()) "当前保留全部应用" else "已选择 ${selected.size} 个应用")
            }
            items(visible, key = { it.packageName }) { choice ->
                val icon = remember(choice.packageName, apps.generation) {
                    runCatching { a.packageManager.getApplicationIcon(choice.packageName) }
                        .getOrElse { a.packageManager.defaultActivityIcon }
                }
                AppPickerCheckbox(
                    checked = choice.packageName in selected,
                    onCheckedChange = { checked ->
                        save(if (checked) selected + choice.packageName else selected - choice.packageName)
                    },
                    title = choice.label,
                    summary = choice.packageName,
                    icon = icon,
                )
            }
            if (!apps.loading && visible.isEmpty()) {
                item { InfoCard("没有匹配的应用") }
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
private fun UninstallPage(
    a: ComponentActivity,
    back: () -> Unit = a::finish,
    onWhitelist: () -> Unit = { a.startActivity(Intent(a, UninstallListActivity::class.java).putExtra("blacklist", false)) },
    onBlacklist: () -> Unit = { a.startActivity(Intent(a, UninstallListActivity::class.java).putExtra("blacklist", true)) },
    onHistory: () -> Unit = { a.startActivity(Intent(a, UninstallHistoryActivity::class.java)) },
) = Page(a, "卸载验证", back) {
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
                if (enabled && com.lijinmu.arashi.notifications.NotificationPreferences.uninstallEnabled(a)) {
                    com.lijinmu.arashi.notifications.AppNotifications.requestPermissionIfNeeded(a)
                }
            },
            title = "启用卸载验证",
            summary = if (moduleActive) "每次卸载请求将会弹窗向你确认"
                else "未检测到 system_server 的 Hook，卸载验证暂时关闭。激活后会恢复此前的开启状态",
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
            onWhitelist()
        }
        Link("黑名单", "与白名单排斥，黑名单直接拒绝卸载。优先于白名单") {
            onBlacklist()
        }
    }
    Group("记录") {
        Link("卸载记录", "查看允许和拒绝的卸载验证记录") {
            onHistory()
        }
    }
}

@Composable
private fun UninstallHistoryPage(a: ComponentActivity, back: () -> Unit = a::finish) {
    var records by remember { mutableStateOf<List<UninstallHistoryStore.Record>>(emptyList()) }
    var request by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var initialLoading by remember { mutableStateOf(true) }
    var refreshingRequested by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var clearError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var readJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(request) {
        if (clearing) return@LaunchedEffect
        readJob = currentCoroutineContext().job
        loading = true
        try {
            val before = records.lastOrNull()?.id ?: Long.MAX_VALUE
            val batch = withContext(Dispatchers.IO) { UninstallHistoryStore.read(a, before, 101) }
            records = records + batch.take(100)
            hasMore = batch.size > 100
            loadError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadError = error.message ?: "读取记录失败"
        } finally {
            loading = false
            initialLoading = false
            refreshingRequested = false
        }
    }
    fun refreshRecords() {
        if (loading || clearing) return
        refreshingRequested = true
        records = emptyList()
        hasMore = true
        request++
    }
    fun clearRecords() {
        if (clearing) return
        clearing = true
        scope.launch {
            try {
                readJob?.cancelAndJoin()
                val (count, batch) = withContext(Dispatchers.IO) {
                    val count = UninstallHistoryStore.clear(a)
                    count to UninstallHistoryStore.read(a, Long.MAX_VALUE, 101)
                }
                records = batch.take(100)
                hasMore = batch.size > 100
                loadError = null
                clearError = null
                MiuixPages.showMessage(a, "清空完成", "已清空 $count 条卸载记录")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                clearError = error.message ?: "清空记录失败"
                MiuixPages.showMessage(a, "清空失败", clearError.orEmpty())
            } finally {
                clearing = false
                loading = false
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "卸载记录",
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefresh(
            isRefreshing = refreshingRequested || (loading && !initialLoading),
            onRefresh = ::refreshRecords,
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新完成"),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    IntroCard("记录已捕获的卸载请求，允许不代表系统最终卸载成功")
                }
                item {
                    LoadingTextButton(
                        "清空记录",
                        { confirmClear = true },
                        Modifier.fillMaxWidth(),
                        enabled = records.isNotEmpty() && !loading && !clearing,
                        loading = clearing,
                    )
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
                    item { LoadingCard("加载记录") }
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
        // Miuix overlays must share the Scaffold's popup host; siblings have no host.
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
                        clearRecords()
                    },
                    Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun UninstallListPage(a: ComponentActivity, blacklist: Boolean, back: () -> Unit = a::finish) {
    val prefKey = if (blacklist) UninstallConfig.BLACKLIST else UninstallConfig.WHITELIST
    val title = if (blacklist) "卸载黑名单" else "卸载白名单"
    val prefs = a.getSharedPreferences(UninstallConfig.PREF_NAME, Context.MODE_PRIVATE)
    var selected by remember(prefKey) {
        mutableStateOf(prefs.getStringSet(prefKey, mutableSetOf()).orEmpty().toSet())
    }
    // Freeze selected-first ranking for this visit so checkbox changes never move rows.
    val selectedOnEntry = remember(prefKey) { selected.toSet() }
    var query by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showUid by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val apps = rememberAppPickerData(a) {
        val manager = a.packageManager
        val installed = manager.getInstalledApplications(0)
        val applications = installed.map { info ->
            val label = runCatching { info.loadLabel(manager).toString() }.getOrDefault(info.packageName)
            val isSystem = (info.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            UninstallChoice(
                "pkg:" + info.packageName,
                label,
                info.packageName,
                if (isSystem) 2 else 1,
            )
        }.sortedWith(compareBy<UninstallChoice> { it.kind }.thenBy { it.title.lowercase() })
        val systemUids = listOf(
            "root" to 0, "daemon" to 1, "bin" to 2,
            "sys" to 3, "system" to 1000, "shell" to 2000,
        )
        val uids = systemUids.map { (name, uid) ->
            UninstallChoice("uid:" + uid, name, "UID " + uid, 3)
        }
        applications + uids
    }
    val choices = apps.choices
    val knownKeys = remember(choices) { choices.mapTo(HashSet<String>()) { it.key } }
    val manualChoices = selected.filterNot { it in knownKeys }.sorted().map { key ->
        if (key.startsWith("uid:")) {
            UninstallChoice(key, "UID " + key.removePrefix("uid:"), "手动添加", 3)
        } else {
            UninstallChoice(key, key.removePrefix("pkg:"), "手动添加", 0)
        }
    }
    val visible = (manualChoices + choices).filter { choice ->
        val inFilter = if (choice.kind == 3) showUid else showSystemApps || choice.kind == 1
        inFilter && (query.isBlank() || choice.title.contains(query, true) ||
            choice.summary.contains(query, true) || choice.key.contains(query, true))
    }.sortedWith(
        compareBy<UninstallChoice> { if (it.key in selectedOnEntry) 0 else 1 }
            .thenBy { it.kind }
            .thenBy { it.title.lowercase() }
            .thenBy { it.key },
    )
    fun save(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet(prefKey, next).apply()
    }
    PruneMissingAppSelections(a, selected, prefixed = true, onPruned = ::save, refreshKey = apps.generation)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    AppPickerFilterMenu(
                        showSystemApps,
                        onShowSystemAppsChange = { showSystemApps = it },
                        showUid = showUid,
                        onShowUidChange = { showUid = it },
                    )
                },
            )
        },
    ) { innerPadding ->
        AppPickerRefreshList(
            data = apps,
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            item {
                IntroCard(
                    if (blacklist) "选中的发起方直接拒绝卸载" else "选中的发起方直接允许卸载，并且黑名单优先",
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
                                // Reveal the added entry using its own filter without clearing selections.
                                if (entry.startsWith("uid:")) {
                                    showUid = true
                                } else if (choices.firstOrNull { it.key == entry }?.kind != 1) {
                                    showSystemApps = true
                                }
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
                AppPickerStatus("已选择 " + selected.size + " 项 · 当前显示 " + visible.size + " 项")
            }
            items(visible, key = { it.key }) { choice ->
                val icon = if (choice.key.startsWith("pkg:")) {
                    remember(choice.key, apps.generation) {
                        runCatching {
                            a.packageManager.getApplicationIcon(choice.key.removePrefix("pkg:"))
                        }.getOrElse { a.packageManager.defaultActivityIcon }
                    }
                } else null
                AppPickerCheckbox(
                    checked = choice.key in selected,
                    onCheckedChange = { checked ->
                        save(if (checked) selected + choice.key else selected - choice.key)
                    },
                    title = choice.title,
                    summary = choice.summary,
                    icon = icon,
                )
            }
            if (!apps.loading && visible.isEmpty()) {
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
private fun QuickCommandItem(title: String, summary: String, action: () -> Unit) =
    BasicComponent(title = title, summary = summary, onClick = action)

private data class QuickOption(val label: String, val execute: (QuickCommands.Completion) -> Unit)
private data class QuickAction(val title: String, val options: List<QuickOption>, val successMessageOnly: Boolean = false)

@Composable
private fun QuickPage(
    a: ComponentActivity,
    back: () -> Unit = a::finish,
    onZybosEnv: () -> Unit = { a.startActivity(Intent(a, com.lijinmu.arashi.tools.ZybosEnvController::class.java)) },
) {
    val commands = remember(a) { com.lijinmu.arashi.tools.QuickCommandController(a) }
    var allFrozen by remember { mutableStateOf(false) }
    var confirmFreeze by remember { mutableStateOf<Boolean?>(null) }
    var freezeRunning by remember { mutableStateOf(false) }
    var freezeQueryVersion by remember { mutableIntStateOf(0) }
    val componentSnackbar = remember { SnackbarHostState() }
    val componentScope = rememberCoroutineScope()
    fun refreshFrozen() {
        val version = ++freezeQueryVersion
        allFrozen = false
        commands.queryRecommendedFrozen { frozen, _ -> if (version == freezeQueryVersion) allFrozen = frozen }
    }
    DisposableEffect(a) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !freezeRunning) refreshFrozen()
        }
        a.lifecycle.addObserver(observer)
        refreshFrozen()
        onDispose { a.lifecycle.removeObserver(observer) }
    }
    var selected by remember { mutableStateOf<QuickAction?>(null) }
    var running by remember { mutableStateOf(false) }
    fun choose(
        title: String,
        successMessageOnly: Boolean = false,
        enabledLabel: String = "开启",
        disabledLabel: String = "关闭",
        command: (Boolean) -> String,
    ): QuickAction =
        QuickAction(title, listOf(true, false).map { enabled ->
            QuickOption(if (enabled) enabledLabel else disabledLabel) { done ->
                commands.run(title, command(enabled), done)
            }
        }, successMessageOnly)
    val completion = QuickCommands.Completion { success, output ->
        val message = if (success && selected?.successMessageOnly == true) "" else output
        running = false
        selected = null
        MiuixPages.showMessage(a, if (success) "执行完毕" else "执行失败", message)
    }
    Page(
        a, "快捷指令执行", back,
        snackbarHost = { SnackbarHost(componentSnackbar) },
        overlay = {
            OverlayDialog(
                show = confirmFreeze != null,
                title = if (confirmFreeze == false) "解冻推荐的作业帮应用" else "冻结推荐的作业帮应用",
                summary = if (freezeRunning) null else
                    "将会立即${if (confirmFreeze == false) "解冻 (pm enable)" else "冻结 (pm disable)"} 以下系统应用：ZpReport、PadMs、SlardarOsClient、日志服务、桌面 ErrorActivity",
                onDismissRequest = { if (!freezeRunning) confirmFreeze = null },
            ) {
                if (freezeRunning) Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                    Text("正在处理…")
                }
                if (!freezeRunning) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton("取消", { confirmFreeze = null }, Modifier.weight(1f))
                    TextButton("继续", {
                        val freeze = confirmFreeze
                        if (freeze != null && !freezeRunning) {
                            freezeRunning = true
                            freezeQueryVersion++
                            commands.setRecommendedFrozen(freeze) { _, result ->
                                freezeRunning = false
                                confirmFreeze = null
                                refreshFrozen()
                                componentScope.launch { componentSnackbar.showSnackbar(result, duration = SnackbarDuration.Long) }
                            }
                        }
                    }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
                }
            }
            OverlayDialog(
                show = selected != null,
                title = selected?.title.orEmpty(),
                onDismissRequest = { if (!running) selected = null },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (running) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                            Text("正在处理…")
                        }
                    } else {
                        selected?.options?.forEach { option ->
                            TextButton(option.label, {
                                if (!running) {
                                    running = true
                                    option.execute(completion)
                                }
                            }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
    ) {
        IntroCard("包含冻结应用、调整厂商设置等控制")
        Group("作业帮系统组件控制") {
            QuickCommandItem(
                if (allFrozen) "解冻推荐的作业帮应用" else "冻结推荐的作业帮应用",
                "ZpReport、PadMs、SlardarOsClient、日志服务、桌面 ErrorActivity",
            ) { if (!freezeRunning) confirmFreeze = !allFrozen }
        }
        Group("设置") {
            Link("作业帮学习机系统环境控制", "读取和修改 persist.sys.env_config") {
                onZybosEnv()
            }
            QuickCommandItem("special_enable_adb_debug", "这个厂商自定义设置并没有什么实际用处") {
                selected = choose("special_enable_adb_debug", successMessageOnly = true) { enabled ->
                    val value = if (enabled) "1" else "0"
                    "settings put global special_enable_adb_debug $value && " +
                        "[ \"\$(settings get global special_enable_adb_debug)\" = '$value' ] && " +
                        "echo 'special_enable_adb_debug = $value'"
                }
            }
            QuickCommandItem("允许 Shell 安装应用", "控制 sys.allow.install.package 属性") {
                selected = choose("允许 Shell 安装应用", successMessageOnly = true,
                    enabledLabel = "true", disabledLabel = "默认") { enabled ->
                    val value = if (enabled) "true" else ""
                    "setprop sys.allow.install.package '$value' && " +
                        "[ \"\$(getprop sys.allow.install.package)\" = '$value' ] && " +
                        "echo 'sys.allow.install.package = $value'"
                }
            }

        }
        Group("投屏") {
            QuickCommandItem("投屏", "调整无线显示和 SystemUI 投屏磁贴") {
                selected = choose("投屏") { enabled ->
                    "settings put global wifi_display_on ${if (enabled) 1 else 0} && am broadcast --user current -a com.android.systemui.cast.${if (enabled) "Add" else "Remove"} -p com.android.systemui"
                }
            }
        }
        InfoCard("Arashi 需要使用超级用户权限执行这些指令")
    }
}


@Composable
private fun SettingsPage(
    a: androidx.activity.ComponentActivity,
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
        Link("通知设置", "管理卸载验证与 TCP 拦截通知") {
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
        MiuixPages.showDetails(a, "关于作者", "首席开发者 : 李瑾沐 (H…Hey)\n小红书号 : failgacha")
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
}

@Composable
private fun NotificationSettingsPage(a: androidx.activity.ComponentActivity, back: () -> Unit) = Page(a, "通知设置", back) {
    val prefs = a.getSharedPreferences(com.lijinmu.arashi.notifications.NotificationPreferences.PREFS, Context.MODE_PRIVATE)
    IntroCard("通知开关仅控制消息提醒，不影响卸载验证、TCP 拦截与记录保存，点按通知可查看对应记录")
    Group("卸载验证") {
        listOf(
            com.lijinmu.arashi.notifications.NotificationPreferences.BLACKLIST to "黑名单拒绝",
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
                accept(withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.readConfig() })
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
                        accept(withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.applyConfig(base, selectedEnv, selectedTools, selectedTips) })
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
                        val json = withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.readFormattedProperty() }
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
                        accept(withContext(Dispatchers.IO) { com.lijinmu.arashi.tools.ZybosEnvController.resetConfig() })
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


