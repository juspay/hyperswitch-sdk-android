package io.hyperswitch.click_to_pay.utils

import io.hyperswitch.click_to_pay.BuildConfig
import io.hyperswitch.logs.EventName
import io.hyperswitch.logs.HSLog
import io.hyperswitch.logs.HyperLogManager
import io.hyperswitch.logs.LogCategory

/**
 * Centralized logging utility for Click to Pay operations.
 * Provides consistent logging format across all Click to Pay components.
 */
object ClickToPayLogger {
    
    /**
     * Logs an informational message for Click to Pay events.
     *
     * @param message The message to log
     */
    fun logInfo(message: String) {
        val log = HSLog.LogBuilder()
            .logType("INFO")
            .category(LogCategory.USER_EVENT)
            .eventName(EventName.CLICK_TO_PAY_FLOW)
            .value(message)
            .version(BuildConfig.VERSION_NAME)
        HyperLogManager.addLog(log.build())
    }
    
    /**
     * Logs an error message for Click to Pay events.
     *
     * @param message The error message to log
     */
    fun logError(message: String) {
        val log = HSLog.LogBuilder()
            .logType("ERROR")
            .category(LogCategory.USER_ERROR)
            .eventName(EventName.CLICK_TO_PAY_FLOW)
            .value(message)
            .version(BuildConfig.VERSION_NAME)
        HyperLogManager.addLog(log.build())
    }
}
