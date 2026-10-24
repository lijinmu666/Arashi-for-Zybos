package com.lijinmu.arashi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppPickerFilterRow(
    status: String,
    categories: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.footnote1,
        )
        Box {
            Button(
                onClick = { expanded = !expanded },
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = categories[selectedIndex],
                    style = MiuixTheme.textStyles.footnote1,
                )
                Spacer(Modifier.width(8.dp))
                DropdownArrowEndAction(MiuixTheme.colorScheme.onSurfaceVariantActions)
            }
            OverlayDropdownPopup(
                entry = DropdownEntry(categories.mapIndexed { index, category ->
                    DropdownItem(
                        text = category,
                        selected = index == selectedIndex,
                        onClick = { onSelectedIndexChange(index) },
                    )
                }),
                show = expanded,
                onDismiss = { expanded = false },
                onDismissFinished = {},
                maxHeight = null,
                dropdownColors = DropdownDefaults.dropdownColors(),
                renderInRootScaffold = true,
            )
        }
    }
}
