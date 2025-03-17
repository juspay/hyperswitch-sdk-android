package io.hyperswitch.logs

import android.content.Context

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val fileManager = LogFileManager(context)
    override fun uncaughtException(thread: Thread, throwable: Throwable) {

        fileManager.addLog(HyperLogManager.getAllLogsAsString())
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
