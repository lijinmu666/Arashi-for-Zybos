package com.lijinmu.arashi.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings as SettingsIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme

import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.lijinmu.arashi.ui.installerx.library.FloatingBottomBar
import com.lijinmu.arashi.ui.installerx.library.FloatingBottomBarMode
import androidx.compose.ui.unit.sp

internal val LocalNavigationContentPadding = compositionLocalOf { 28.dp }

/** The backdrop contains only page content, never the bar itself. */
@Composable
internal fun ArashiNavigation(a: ComponentActivity, content: @Composable (Int) -> Unit) {
    val prefs = remember(a) { a.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var style by remember { mutableIntStateOf(prefs.getInt("navigation_style", 0)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "navigation_style") style = prefs.getInt(key, 0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val states = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val backdrop = if (android.os.Build.VERSION.SDK_INT >= 33) rememberLayerBackdrop() else null
    val legacyLayer = rememberGraphicsLayer()
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    val labels = listOf("主页", "设置")
    val icons = listOf(MiuixIcons.Home, MiuixIcons.SettingsIcon)
    val select: (Int) -> Unit = { index ->
        scope.launch { pager.animateScrollToPage(index, animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f)) }
    }
    BackHandler(enabled = pager.currentPage != 0) { select(0) }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (style != 1) NavigationBar {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = pager.targetPage == index, onClick = { select(index) },
                            icon = icons[index], label = label,
                        )
                    }
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .onGloballyPositioned { pageOrigin = it.positionInRoot() }
                    .then(if (style == 1 && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                    .drawWithContent {
                        if (style == 1 && backdrop == null) {
                            legacyLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(legacyLayer)
                        } else drawContent()
                    },
                verticalAlignment = Alignment.Top,
            ) { page ->
                states.SaveableStateProvider(page) {
                    CompositionLocalProvider(LocalNavigationContentPadding provides if (style == 1) 124.dp else 28.dp) {
                        content(page)
                    }
                }
            }
        }
        if (style == 1 && backdrop != null) Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            FloatingBottomBar(
                items = labels,
                selectedIndex = { pager.targetPage },
                onSelected = select,
                backdrop = backdrop,
                mode = FloatingBottomBarMode.LiquidGlass,
                iconContent = { _, index -> Icon(icons[index], contentDescription = null) },
                labelContent = { label, _ ->
                    Text(label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false)
                },
            )
        }
        if (style == 1 && backdrop == null) LegacyGlassNavigationBar(
            selected = pager.targetPage, onSelected = select, backdrop = legacyLayer,
            pageOrigin = pageOrigin,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun LegacyGlassNavigationBar(
    selected: Int,
    onSelected: (Int) -> Unit,
    backdrop: androidx.compose.ui.graphics.layer.GraphicsLayer,
    pageOrigin: Offset,
    modifier: Modifier = Modifier,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val blur = remember(density) {
        val radius = with(density) { 20.dp.toPx() }
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }
    val position by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.8f, stiffness = 380f), label = "glassIndicator")
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val primary = MiuixTheme.colorScheme.primary
    val onSurface = MiuixTheme.colorScheme.onSurface
    Box(modifier) {
        Box(
            Modifier.width(280.dp).height(76.dp)
                .shadow(12.dp, CircleShape).clip(CircleShape)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .selectableGroup(),
        ) {
            Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = blur }) {
                val offset = origin - pageOrigin
                drawContext.canvas.save()
                drawContext.canvas.translate(-offset.x, -offset.y)
                drawLayer(backdrop)
                drawContext.canvas.restore()
            }
            Box(Modifier.matchParentSize()
                .background(surface.copy(alpha = 0.68f))
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), onSurface.copy(alpha = 0.08f))), CircleShape))
            Box(Modifier.padding(6.dp).offset(x = 134.dp * position)
                .width(134.dp).fillMaxHeight().clip(CircleShape)
                .background(primary.copy(alpha = 0.16f))
                .border(0.75.dp, Color.White.copy(alpha = 0.25f), CircleShape))
            Row(Modifier.fillMaxSize().padding(6.dp)) {
                listOf("主页", "设置").forEachIndexed { index, label ->
                    val tint by animateColorAsState(if (selected == index) primary else onSurface, label = "glassTabColor")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .selectable(selected == index, onClick = { onSelected(index) }, role = Role.Tab),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (index == 0) MiuixIcons.Home else MiuixIcons.SettingsIcon, contentDescription = null,
                            tint = tint, modifier = Modifier.size(24.dp))
                        Text(label, color = tint, style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }
        }
    }
}
