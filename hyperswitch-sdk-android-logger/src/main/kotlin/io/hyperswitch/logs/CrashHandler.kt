package io.hyperswitch.logs

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.os.StatFs
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone

class CrashHandler(private val context: Context, private val sdkVersion: String, private val sessionId: String = "") :
    Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val fileManager = LogFileManager(context)
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val storageStat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()

        val stackTrace = throwable.stackTrace
        val topFrame = stackTrace.firstOrNull()?.className ?: ""
        val crashSource = when {
            topFrame.startsWith("io.hyperswitch") -> "sdk"
            topFrame.startsWith("org.chromium") || topFrame.startsWith("android.webkit") -> "webview"
            topFrame.startsWith("kotlinx.coroutines") -> "coroutines_runtime"
            else -> "host_app"
        }

        val obj = mapOf(
            "label" to "crash_detected",
            "value" to throwable.toString(),
            "category" to "crash",
            "subcategory" to "sdk_crash",

            // Crash attribution
            "crash_source" to crashSource,
            "crash_class" to throwable.javaClass.simpleName,
            "top_frame" to topFrame,

            // Thread info
            "thread_name" to thread.name,
            "thread_id" to thread.id,
            "is_main_thread" to (thread == Looper.getMainLooper().thread),

            // SDK context
            "sdk_version" to sdkVersion,
            "session_id" to sessionId,
            "timestamp_utc" to System.currentTimeMillis(),
            "timezone" to TimeZone.getDefault().id,

            // Device info
            "device" to mapOf(
                "device_model" to Build.MODEL,
                "device_manufacturer" to Build.MANUFACTURER,
                "os_version" to Build.VERSION.RELEASE,
                "api_level" to Build.VERSION.SDK_INT,
                "device_arch" to (System.getProperty("os.arch") ?: "unknown")
            ),

            // Memory state
            "memory" to mapOf(
                "available_ram_mb" to (memInfo.availMem / 1048576),
                "total_ram_mb" to (memInfo.totalMem / 1048576),
                "is_low_memory" to memInfo.lowMemory
            ),

            // Storage state
            "storage" to mapOf(
                "free_storage_mb" to (storageStat?.let { it.availableBlocksLong * it.blockSizeLong / 1048576 } ?: -1)
            )
        )
        try {
            val jsonData = JSONObject(obj).toString()
            val log = HSLog.LogBuilder().logType("error").category(LogCategory.USER_EVENT)
                .eventName(EventName.CRASH_EVENT).value(jsonData).version(sdkVersion).sessionId(sessionId)
            HyperLogManager.addLog(log.build())
        } catch (_: JSONException) {
        }
        try{
            fileManager.addLog(HyperLogManager.getAllLogsAsString())
        }catch (_: Exception){
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
