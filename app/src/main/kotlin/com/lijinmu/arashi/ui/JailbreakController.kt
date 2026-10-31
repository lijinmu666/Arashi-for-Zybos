package com.lijinmu.arashi.ui

import android.content.Context
import com.lijinmu.arashi.jailbreak.DirtyFragVrRunner
import com.lijinmu.arashi.logs.RuntimeLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the operation across navigation and Activity recreation; never auto-runs on boot. */
internal object JailbreakController {
    data class Result(val running: Boolean = false, val finished: Boolean = false,
                      val success: Boolean = false, val detail: String = "")
    private val busy = AtomicBoolean()
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(Result())
    val state = mutable.asStateFlow()

    fun start(context: Context, disableModules: Boolean = false, softReboot: Boolean = false) {
        if (!busy.compareAndSet(false, true)) return
        val app = context.applicationContext
        mutable.value = Result(running = true)
        worker.launch {
            val output = StringBuilder()
            fun append(line: String) {
                output.append(line)
                if (output.length > 16000) output.delete(0, output.length - 16000)
                RuntimeLogger.info("Jailbreak", line.trim())
            }
            try {
                check(android.os.Process.myUid() / 100000 == 0) { "请在设备主用户中运行越狱" }
                val exit = DirtyFragVrRunner.run(app, disableModules, softReboot, ::append)
                check(exit == 0 && DirtyFragVrRunner.handoffComplete()) {
                    "提权未完成（退出码 $exit），请查看详情；再次尝试前请重启设备"
                }
                mutable.value = Result(finished = true, success = true, detail = output.toString())
            } catch (failure: Exception) {
                append("\n${failure.message ?: failure.javaClass.simpleName}\n")
                mutable.value = Result(finished = true, detail = output.toString())
            } finally {
                busy.set(false)
            }
        }
    }
}
