package com.arashi.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arashi.tcp.TcpHistoryActivity
import com.arashi.tcp.TcpHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TcpHistoryPage(a: TcpHistoryActivity) {
    var records by remember { mutableStateOf<List<TcpHistoryStore.Record>>(emptyList()) }
    var request by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var clearing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Serialize pagination, refresh and deletion so an old read cannot refill a cleared list.
    LaunchedEffect(request) {
        loading = true
        try {
            if (clearing) {
                val count = withContext(Dispatchers.IO) { TcpHistoryStore.clear(a) }
                records = emptyList()
                hasMore = false
                MiuixPages.showMessage(a, "清空完成", "已清空 $count 条 TCP 拦截记录")
            } else {
                val before = records.lastOrNull()?.id ?: Long.MAX_VALUE
                val batch = withContext(Dispatchers.IO) { TcpHistoryStore.read(a, before, 101) }
                records = records + batch.take(100)
                hasMore = batch.size > 100
            }
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "操作失败"
            if (clearing) MiuixPages.showMessage(a, "清空失败", error.orEmpty())
        }
        loading = false
        clearing = false
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "TCP 拦截记录",
                navigationIcon = {
                    IconButton(onClick = a::finish) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TcpInfoCard("记录已拦截的 login.fail 通知，错误码来自广播，响应字段在捕获到对应登录流程时显示。记录不包含登录令牌和设备密钥")
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton("刷新", {
                        records = emptyList()
                        hasMore = true
                        request++
                    }, Modifier.weight(1f), enabled = !loading)
                    TextButton("清空记录", { confirmClear = true }, Modifier.weight(1f),
                        enabled = records.isNotEmpty() && !loading)
                }
            }
            items(records, key = { it.id }) { record ->
                var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
                val time = remember(record.time) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.time))
                }
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("已拦截 · " + (record.errorCode ?: "未知错误码"),
                            style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.primary)
                        Text(time, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text("触发路径：" + (record.source ?: "未识别"))
                        Text("错误类型：" + if (record.errorType < 0) "未提供" else record.errorType.toString())
                        TextButton(if (expanded) "收起详情" else "查看详情", { expanded = !expanded })
                        if (expanded) {
                            SelectionContainer {
                                Text("进程：${record.process ?: "未知"} (${record.pid})\n" +
                                    record.details.orEmpty() + "\n\n调用栈：\n" + record.stack.orEmpty(),
                                    style = MiuixTheme.textStyles.footnote1)
                            }
                            TextButton("复制详情", {
                                a.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                    ClipData.newPlainText("TCP 拦截记录", "$time\n错误码：${record.errorCode}\n" +
                                        "错误类型：${record.errorType}\n触发路径：${record.source}\n" +
                                        "进程：${record.process} (${record.pid})\n" +
                                        record.details.orEmpty() + "\n\n" + record.stack.orEmpty()),
                                )
                                android.widget.Toast.makeText(a, "已复制详情", android.widget.Toast.LENGTH_SHORT).show()
                            })
                        }
                    }
                }
            }
            if (loading) item { TcpInfoCard("正在加载记录…") }
            if (error != null) item { TcpInfoCard("操作失败：" + error.orEmpty()) }
            if (records.isEmpty() && !loading && error == null) item { TcpInfoCard("暂无 TCP 拦截记录") }
            if (hasMore && !loading && error == null) item {
                TextButton("加载更多", { request++ }, Modifier.fillMaxWidth())
            }
        }
    }
    MiuixPages.MessageDialog(a)
    OverlayDialog(
        show = confirmClear,
        title = "清空 TCP 拦截记录",
        summary = "确定清空全部 TCP 拦截记录吗？",
        onDismissRequest = { confirmClear = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton("取消", { confirmClear = false }, Modifier.weight(1f))
            TextButton("清空", {
                confirmClear = false
                if (!loading) {
                    clearing = true
                    request++
                }
            }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TcpInfoCard(text: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
