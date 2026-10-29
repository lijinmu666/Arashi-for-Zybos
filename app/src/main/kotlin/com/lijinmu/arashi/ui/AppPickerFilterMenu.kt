package com.lijinmu.arashi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Top-right filter shared by every application picker. */
@Composable
internal fun AppPickerFilterMenu(
    showSystemApps: Boolean,
    onShowSystemAppsChange: (Boolean) -> Unit,
    showUid: Boolean? = null,
    onShowUidChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = if (showSystemApps || showUid == true) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurface
    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics {
                contentDescription = "应用筛选"
                stateDescription = (if (showSystemApps) "显示用户应用和系统应用" else "仅显示用户应用") +
                    (if (showUid == true) "，显示 UID" else "")
            },
        ) {
            Icon(
                imageVector = MiuixIcons.Filter,
                contentDescription = "应用筛选",
                tint = tint,
            )
        }
        OverlayDropdownPopup(
            entry = DropdownEntry(listOfNotNull(
                DropdownItem(
                    text = "显示系统应用",
                    selected = showSystemApps,
                    onClick = {
                        onShowSystemAppsChange(!showSystemApps)
                        expanded = false
                    },
                ),
                if (showUid != null) DropdownItem(
                    text = "显示UID",
                    selected = showUid,
                    onClick = {
                        onShowUidChange(!showUid)
                        expanded = false
                    },
                ) else null,
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
