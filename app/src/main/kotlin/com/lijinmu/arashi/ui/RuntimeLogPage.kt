package com.lijinmu.arashi.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.lijinmu.arashi.logs.RuntimeLogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RuntimeLogPage(a: ComponentActivity, back: () -> Unit) {
    val logPrefs = remember(a) { a.getSharedPreferences(RuntimeLogStore.PREFS, android.content.Context.MODE_PRIVATE) }
    var loggingEnabled by remember(logPrefs) { mutableStateOf(RuntimeLogStore.isEnabled(a)) }
    DisposableEffect(logPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == RuntimeLogStore.KEY_ENABLED) loggingEnabled = changed.getBoolean(key, false)
        }
        logPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { logPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var records by remember { mutableStateOf<List<RuntimeLogStore.Record>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var choosingExport by rememberSaveable { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val operations = remember { Mutex() }
    val snackbar = remember { SnackbarHostState() }
    val busy = initialLoading || loading || refreshing || clearing || exporting || choosingExport || !loggingEnabled

    suspend fun load(reset: Boolean) {
        operations.withLock {
            loading = true
            try {
                val before = if (reset) Long.MAX_VALUE else records.lastOrNull()?.id ?: Long.MAX_VALUE
                val batch = withContext(Dispatchers.IO) { RuntimeLogStore.read(a, before, 101) }
                records = if (reset) batch.take(100) else records + batch.take(100)
                hasMore = batch.size > 100
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "读取日志失败"
            } finally {
                loading = false
                initialLoading = false
            }
        }
    }

    fun refreshLogs() {
        // Mark the request before launching so repeated pull gestures cannot queue reads.
        if (initialLoading || loading || refreshing || clearing || exporting || choosingExport || !loggingEnabled) return
        refreshing = true
        scope.launch {
            try {
                load(reset = true)
            } finally {
                refreshing = false
            }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        choosingExport = false
        if (uri != null) {
            exporting = true
            scope.launch {
                try {
                    val count = operations.withLock {
                        withContext(Dispatchers.IO) {
                            val output = a.contentResolver.openOutputStream(uri, "wt")
                                ?: throw IOException("无法打开所选文件")
                            output.use { RuntimeLogStore.export(a, it) }
                        }
                    }
                    snackbar.showSnackbar("已导出 $count 条日志")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    snackbar.showSnackbar("导出失败：${failure.message ?: "无法写入文件"}")
                } finally {
                    exporting = false
                }
            }
        }
    }

    LaunchedEffect(a, loggingEnabled) {
        if (loggingEnabled) load(reset = true) else {
            records = emptyList()
            hasMore = false
            back()
        }
    }
    LaunchedEffect(a) {
        while (true) {
            delay(30_000)
            if (!a.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) continue
            operations.withLock {
                try {
                    withContext(Dispatchers.IO) { RuntimeLogStore.prune(a) }
                    val cutoff = System.currentTimeMillis() - RuntimeLogStore.RETENTION_MILLIS
                    records = records.filter { it.time > cutoff && it.time <= System.currentTimeMillis() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "清理过期日志失败"
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            SmallTopAppBar(
                title = "运行日志",
                navigationIcon = {
                    IconButton(onClick = back) { Icon(MiuixIcons.Back, contentDescription = "返回") }
                },
            )
        },
    ) { innerPadding ->
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = ::refreshLogs,
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新完成"),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    LogInfoCard("仅保留最近 12 小时的应用运行与 Hook 加载日志。导出包含全部保留日志，日志量过大时优先保留最新记录")
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(if (exporting) "正在导出…" else "导出", {
                            choosingExport = true
                            try {
                                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                                export.launch("Arashi-log-$stamp.txt")
                            } catch (failure: RuntimeException) {
                                choosingExport = false
                                scope.launch { snackbar.showSnackbar("无法打开文件保存界面：${failure.message.orEmpty()}") }
                            }
                        }, Modifier.weight(1f), enabled = !busy)
                        TextButton("清除", { confirmClear = true }, Modifier.weight(1f), enabled = !busy)
                    }
                }
                if (initialLoading || (loading && !refreshing) || clearing) item {
                    LogInfoCard(if (clearing) "正在清除日志…" else "正在读取日志…")
                }
                if (error != null) item { LogInfoCard("操作失败：${error.orEmpty()}") }
                items(records, key = { it.id }) { record ->
                    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
                    val timestamp = remember(record.time) {
                        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(record.time))
                    }
                    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${record.level} · ${record.tag}", style = MiuixTheme.textStyles.title3,
                                color = MiuixTheme.colorScheme.primary)
                            Text("$timestamp · ${record.process} (${record.pid})",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote1)
                            SelectionContainer {
                                Text(if (expanded) record.message else record.message.lineSequence().firstOrNull().orEmpty().take(240),
                                    style = MiuixTheme.textStyles.body1)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (record.message.contains('\n') || record.message.length > 240) {
                                    TextButton(if (expanded) "收起" else "查看详情", { expanded = !expanded })
                                }
                                TextButton("复制", {
                                    a.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                        ClipData.newPlainText("Arashi 运行日志", RuntimeLogStore.format(record)),
                                    )
                                    scope.launch { snackbar.showSnackbar("已复制日志") }
                                })
                            }
                        }
                    }
                }
                if (!initialLoading && !loading && records.isEmpty() && error == null) item {
                    LogInfoCard("最近 12 小时内暂无日志")
                }
                if (hasMore) item {
                    TextButton("加载更多", { scope.launch { load(reset = false) } }, Modifier.fillMaxWidth(), enabled = !busy)
                }
            }
        }
        OverlayDialog(
            show = confirmClear,
            title = "清除运行日志",
            summary = "清除后无法恢复，后续运行产生的新日志会继续记录",
            onDismissRequest = { confirmClear = false },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("取消", { confirmClear = false }, Modifier.weight(1f))
                TextButton("清除", {
                    confirmClear = false
                    clearing = true
                    scope.launch {
                        try {
                            val count = operations.withLock {
                                withContext(Dispatchers.IO) { RuntimeLogStore.clear(a) }
                            }
                            records = emptyList()
                            hasMore = false
                            error = null
                            snackbar.showSnackbar("已清除 $count 条日志")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            snackbar.showSnackbar("清除失败：${failure.message.orEmpty()}")
                        } finally {
                            clearing = false
                        }
                    }
                }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}

@Composable
private fun LogInfoCard(text: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
