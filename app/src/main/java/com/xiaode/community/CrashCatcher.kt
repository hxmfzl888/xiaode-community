package com.xiaode.community

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常捕获：把崩溃堆栈写入 filesDir/crash.log，
 * 并用 Toast 提示用户发日志给开发者，避免“莫名其妙直接闪退”。
 *
 * 同时提供诊断日志的读取 / 导出 / 分享能力，方便用户把日志交给开发者。
 */
object CrashCatcher {
    private const val LOG_NAME = "crash.log"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val model = Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.SDK_INT
                val text = "\n===== $ts =====\nDevice: $model\nThread: ${t.name}\n${e.javaClass.name}: ${e.message}\n$sw\n"
                val f = File(app.filesDir, LOG_NAME)
                f.appendText(text)
            } catch (_: Throwable) {}
            try {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(app, "应用出错：${e.javaClass.simpleName}\n崩溃日志已保存", Toast.LENGTH_LONG).show()
                }
                Thread.sleep(1500)
            } catch (_: Throwable) {}
            def?.uncaughtException(t, e)
        }
    }

    /** 日志文件 */
    fun logFile(ctx: Context): File = File(ctx.filesDir, LOG_NAME)

    /** 读取全部日志文本（不存在则返回空串） */
    fun readLog(ctx: Context): String {
        return try {
            val f = logFile(ctx)
            if (f.exists()) f.readText() else ""
        } catch (_: Throwable) {
            ""
        }
    }

    /** 是否有日志 */
    fun hasLog(ctx: Context): Boolean = try {
        val f = logFile(ctx)
        f.exists() && f.length() > 0
    } catch (_: Throwable) {
        false
    }

    /** 清空日志 */
    fun clearLog(ctx: Context) {
        try {
            val f = logFile(ctx)
            if (f.exists()) f.delete()
        } catch (_: Throwable) {}
    }

    /** 把日志导出到 App 外部存储目录，返回导出的文件（失败返回 null） */
    fun exportLog(ctx: Context): File? {
        return try {
            if (!hasLog(ctx)) return null
            val dir = File(ctx.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val out = File(dir, "xiaode_crash_$ts.log")
            out.writeText(readLog(ctx))
            out
        } catch (_: Throwable) {
            null
        }
    }

    /** 分享日志文件 */
    fun shareLog(ctx: Context) {
        val file = exportLog(ctx) ?: run {
            Toast.makeText(ctx, "暂无崩溃日志", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                ctx,
                ctx.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "小德社区 崩溃日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "分享崩溃日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) {
            Toast.makeText(ctx, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
