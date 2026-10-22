package com.lijinmu.arashi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text

/** Keep the last successful snapshot visible while refreshing, including after a failure. */
internal class AppPickerData<T> {
    var choices by mutableStateOf(emptyList<T>())
        internal set
    var loading by mutableStateOf(true)
        internal set
    var initialized by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var generation by mutableIntStateOf(0)
        internal set
    internal var request by mutableIntStateOf(0)

    fun refresh() {
        if (loading) return
        loading = true
        request++
    }
}

@Composable
internal fun <T> rememberAppPickerData(key: Any, load: () -> List<T>): AppPickerData<T> {
    val data = remember(key) { AppPickerData<T>() }
    val currentLoad by rememberUpdatedState(load)
    LaunchedEffect(data, data.request) {
        data.error = null
        try {
            data.choices = withContext(Dispatchers.IO) { currentLoad() }
            data.generation++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            data.error = failure.message ?: "无法读取应用列表"
        } finally {
            data.initialized = true
            data.loading = false
        }
    }
    return data
}

@Composable
internal fun <T> AppPickerRefreshList(
    data: AppPickerData<T>,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    PullToRefresh(
        isRefreshing = data.loading && data.initialized,
        onRefresh = data::refresh,
        modifier = modifier,
        refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新完成"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (data.loading && !data.initialized) {
                item(key = "picker-loading") { LoadingCard("加载应用") }
            }
            data.error?.let { error ->
                item(key = "picker-error") { Text("加载失败：$error，下拉可重试") }
            }
            content()
        }
    }
}
