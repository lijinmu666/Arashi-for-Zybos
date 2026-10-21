package com.lijinmu.arashi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared Miuix orbiting-dot animation; operation names remain available to accessibility. */
@Composable
internal fun LoadingIndicator(
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    InfiniteProgressIndicator(
        modifier = modifier.semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        color = MiuixTheme.colorScheme.primary,
        size = size,
    )
}

@Composable
internal fun LoadingCard(description: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LoadingIndicator(description)
        }
    }
}

@Composable
internal fun LoadingTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    if (loading) {
        Button(onClick = {}, modifier = modifier, enabled = false) {
            LoadingIndicator(text, size = 20.dp)
        }
    } else {
        TextButton(text, onClick, modifier, enabled = enabled)
    }
}
