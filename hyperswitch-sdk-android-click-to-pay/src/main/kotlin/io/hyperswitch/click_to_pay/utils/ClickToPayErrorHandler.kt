package io.hyperswitch.click_to_pay.utils

import io.hyperswitch.click_to_pay.models.ClickToPayException
import org.json.JSONObject

/**
 * Centralized error handling utility for Click to Pay operations.
 * Provides consistent error detection and handling across all Click to Pay components.
 */
object ClickToPayErrorHandler {
    
    /**
     * Checks if the JSON response contains an error and handles it appropriately.
     * Logs the error and invokes the error callback if an error is present.
     *
     * @param data The JSON data object to check for errors
     * @param onError Callback invoked with error type and message if an error is found
     * @throws ClickToPayException if an error is detected in the response
     */
    fun handleErrorResponse(
        data: JSONObject,
        onError: (errorType: String, errorMessage: String) -> Unit
    ) {
        val error = data.optJSONObject("error")
        if (error != null) {
            val errorType = error.optString("type", "ERROR")
            val errorMessage = error.optString("message", "Unknown error")
            
            ClickToPayLogger.logError("Error - Type: $errorType, Message: $errorMessage")
            onError(errorType, errorMessage)
        }
    }
    
    /**
     * Checks if the JSON response contains an error and throws an exception if found.
     * This is a convenience method that combines error detection with exception throwing.
     *
     * @param data The JSON data object to check for errors
     * @param operationName The name of the operation for error messages
     * @param cleanupAction Optional cleanup action to run before throwing the exception
     * @throws ClickToPayException if an error is detected
     */
    fun checkAndThrowIfError(
        data: JSONObject,
        operationName: String,
        cleanupAction: (() -> Unit)? = null
    ) {
        handleErrorResponse(data) { errorType, errorMessage ->
            cleanupAction?.invoke()
            throw ClickToPayException(
                "Failed to $operationName - Type: $errorType, Message: $errorMessage",
                errorType
            )
        }
    }
}
