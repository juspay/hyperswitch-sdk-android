package io.hyperswitch.logs

import android.content.Context
import android.content.Context.MODE_PRIVATE

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val fileManager = LogFileManager(context)
    override fun uncaughtException(thread: Thread, throwable: Throwable) {

        fileManager.addLog(HyperLogManager.getAllLogsAsString())
        defaultHandler?.uncaughtException(thread, throwable)
    }
}