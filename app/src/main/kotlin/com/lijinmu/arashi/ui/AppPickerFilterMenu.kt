package com.lijinmu.arashi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Top-right filter shared by every application picker. */
@Composable
internal fun AppPickerFilterMenu(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = if (showSystemApps) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurfaceVariantActions
    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics {
                contentDescription = "应用筛选"
                stateDescription = if (showSystemApps) "显示用户应用和系统应用" else "仅显示用户应用"
            },
        ) {
            Canvas(Modifier.size(24.dp)) {
                val unit = size.minDimension / 24f
                val stroke = 1.8f * unit
                fun slider(y: Float, knob: Float) {
                    drawLine(tint, Offset(3f * unit, y * unit), Offset((knob - 3f) * unit, y * unit),
                        strokeWidth = stroke, cap = StrokeCap.Round)
                    drawLine(tint, Offset((knob + 3f) * unit, y * unit), Offset(21f * unit, y * unit),
                        strokeWidth = stroke, cap = StrokeCap.Round)
                    drawCircle(tint, 2.6f * unit, Offset(knob * unit, y * unit), style = Stroke(stroke))
                }
                slider(7f, 15f)
                slider(17f, 9f)
            }
        }
        OverlayDropdownPopup(
            entry = DropdownEntry(listOf(
                DropdownItem(
                    text = "显示系统应用",
                    selected = showSystemApps,
                    onClick = {
                        onShowSystemAppsChange(!showSystemApps)
                        expanded = false
                    },
                ),
            )),
            show = expanded,
            onDismiss = { expanded = false },
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = true,
        )
    }
}

@Composable
internal fun AppPickerStatus(status: String) {
    Text(
        text = status,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        color = MiuixTheme.colorScheme.primary,
        style = MiuixTheme.textStyles.footnote1,
    )
}
