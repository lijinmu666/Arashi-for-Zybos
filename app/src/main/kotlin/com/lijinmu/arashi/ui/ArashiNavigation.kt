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
    val backdrop = rememberLayerBackdrop()
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
                    .then(if (style == 1) Modifier.layerBackdrop(backdrop) else Modifier),
                verticalAlignment = Alignment.Top,
            ) { page ->
                states.SaveableStateProvider(page) {
                    CompositionLocalProvider(LocalNavigationContentPadding provides if (style == 1) 124.dp else 28.dp) {
                        content(page)
                    }
                }
            }
        }
        if (style == 1) Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            FloatingBottomBar(
                items = labels,
                selectedIndex = { pager.targetPage },
                onSelected = select,
                backdrop = backdrop,
                mode = if (android.os.Build.VERSION.SDK_INT >= 33) FloatingBottomBarMode.LiquidGlass else FloatingBottomBarMode.Blur,
                iconContent = { _, index -> Icon(icons[index], contentDescription = null) },
                labelContent = { label, _ ->
                    Text(label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false)
                },
            )
        }
    }
}
