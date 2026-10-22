package com.lijinmu.arashi.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lijinmu.arashi.xposed.HookConfig
import com.lijinmu.arashi.xposed.SystemUiThemeColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun SystemUiThemeColorPreference(prefs: SharedPreferences, enabled: Boolean) {
    var saved by remember(prefs) {
        mutableStateOf(prefs.getInt(HookConfig.KEY_SYSTEMUI_THEME_COLOR, SystemUiThemeColors.DEFAULT_COLOR))
    }
    var show by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HookConfig.KEY_SYSTEMUI_THEME_COLOR) {
                saved = prefs.getInt(key, SystemUiThemeColors.DEFAULT_COLOR)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    ArrowPreference(
        title = "SystemUI 颜色",
        summary = SystemUiThemeColors.format(saved) + " · 支持六位 RGB 色值",
        enabled = enabled,
        onClick = {
            input = SystemUiThemeColors.format(saved)
            error = false
            show = true
        },
    )
    OverlayDialog(
        show = show,
        title = "SystemUI 主题色",
        onDismissRequest = { show = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val parsed = SystemUiThemeColors.parse(input)
            val preview = parsed ?: SystemUiThemeColors.opaque(saved)
            Text(
                "快捷设置选中效果",
                color = Color(SystemUiThemeColors.foreground(preview)),
                modifier = Modifier.fillMaxWidth()
                    .background(Color(preview), RoundedCornerShape(16.dp)).padding(18.dp),
            )
            TextField(
                value = input,
                onValueChange = { input = it; error = false },
                label = "输入 #RRGGBB 色值",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error) Text("请输入六位十六进制颜色，例如 #C8B6FF", color = Color(0xFFE05252))
            val presets = listOf(
                "蓝色" to "#3D54D9", "淡紫" to "#C8B6FF", "粉色" to "#E773A2",
                "绿色" to "#36A866", "橙色" to "#F39C38", "白色" to "#FFFFFF",
            )
            presets.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, hex) ->
                        TextButton(name, { input = hex; error = false }, Modifier.weight(1f))
                    }
                }
            }
            Text("颜色仅用于系统界面，保存后请重启 SystemUI。关闭主题色开关并重启可恢复系统颜色")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton("取消", { show = false }, Modifier.weight(1f))
                TextButton(
                    "保存",
                    {
                        val color = SystemUiThemeColors.parse(input)
                        if (color == null) {
                            error = true
                        } else {
                            prefs.edit().putInt(HookConfig.KEY_SYSTEMUI_THEME_COLOR, color).apply()
                            saved = color
                            show = false
                        }
                    },
                    Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
