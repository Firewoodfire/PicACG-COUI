package com.picacg.coui

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File

/**
 * 崩溃兜底。
 *
 * 首次启动时给线程装一个未捕获异常处理器，把堆栈写到 `files/last_crash.txt`。
 * 下次启动如果发现这个文件还很新，就自动打开「兼容模式」：
 * 关掉背景模糊、COUI 大标题顶栏和浮动底栏，改用最朴素的 Compose 组件。
 *
 * 目的很直接——就算某个 ROM 上 COUI 的某个组件崩了，用户也能进得去、能看到日志，
 * 而不是一直卡在启动闪退里。
 */
class PicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}

object CrashGuard {

    private const val FILE_NAME = "last_crash.txt"

    /** 24 小时内的崩溃才算「刚崩过」，再久就不自动降级了。 */
    private const val RECENT_MS = 24L * 60 * 60 * 1000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(app.filesDir, FILE_NAME).writeText(
                    buildString {
                        append("time=").append(System.currentTimeMillis()).append('\n')
                        append("thread=").append(thread.name).append('\n')
                        append(Log.getStackTraceString(error))
                    },
                )
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else kotlin.system.exitProcess(10)
        }
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun recent(ctx: Context): Boolean {
        val f = file(ctx)
        return f.exists() && System.currentTimeMillis() - f.lastModified() < RECENT_MS
    }

    fun read(ctx: Context): String = runCatching {
        val f = file(ctx)
        if (f.exists()) f.readText() else ""
    }.getOrDefault("")

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }
}
