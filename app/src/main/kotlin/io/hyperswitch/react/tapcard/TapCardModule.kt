package io.hyperswitch.react.tapcard

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.lang.reflect.Proxy

/**
 * React Native bridge module for NFC EMV card reading using the TapCard public API via reflection.
 *
 * This module provides JavaScript access to Android's NFC capabilities for reading
 * EMV-compliant contactless payment cards. All NFC operations are delegated to TapCard via reflection.
 */
class TapCardModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private var tapCardInstance: Any? = null
    private var tapCardClass: Class<*>? = null
    private var tapCardConfigBuilderClass: Class<*>? = null
    private var cardResultClass: Class<*>? = null
    private var permissionResultClass: Class<*>? = null
    private var permissionErrorClass: Class<*>? = null


    init {
        try {
            tapCardClass = Class.forName("io.hyperswitch.tapcard.TapCard")
            tapCardConfigBuilderClass = Class.forName("io.hyperswitch.tapcard.TapCardConfigBuilder")
            cardResultClass = Class.forName("io.hyperswitch.tapcard.CardResult")
            permissionResultClass = Class.forName("io.hyperswitch.tapcard.PermissionResult")
            permissionErrorClass = Class.forName("io.hyperswitch.tapcard.PermissionError")
        } catch (e: ClassNotFoundException) {
            android.util.Log.w(NAME, "TapCard SDK not found - NFC functionality will be unavailable")
        }
    }

    override fun getName(): String = NAME

    /**
     * Checks if TapCard SDK is available.
     */
    private fun isTapCardAvailable(): Boolean = tapCardClass != null

    /**
     * Creates a TapCardConfig using reflection.
     */
    private fun createConfig(timeoutMs: Long = 30000, enableDebug: Boolean = true): Any? {
        return try {
            val builder = tapCardConfigBuilderClass?.getDeclaredConstructor()?.newInstance()
            builder?.let { b ->
                tapCardConfigBuilderClass?.getMethod("setTimeout", Long::class.java)?.invoke(b, timeoutMs)
                tapCardConfigBuilderClass?.getMethod("enableDebug", Boolean::class.java)?.invoke(b, enableDebug)
                tapCardConfigBuilderClass?.getMethod("tryDirectReadOnFailure", Boolean::class.java)?.invoke(b, true)
                tapCardConfigBuilderClass?.getMethod("continueOnTagNotSupported", Boolean::class.java)?.invoke(b, false)
                tapCardConfigBuilderClass?.getMethod("build")?.invoke(b)
            }
        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to create TapCardConfig", e)
            null
        }
    }

    /**
     * Parses config JSON and creates TapCardConfig via reflection.
     */
    private fun parseConfig(configJson: String): Any? {
        return try {
            val json = org.json.JSONObject(configJson)
            val timeoutMs = json.optLong("timeoutMs", 30000)
            val enableDebug = json.optBoolean("enableDebug", false)
            val tryDirectRead = json.optBoolean("tryDirectReadOnFailure", true)
            val continueOnUnsupported = json.optBoolean("continueOnTagNotSupported", false)

            val builder = tapCardConfigBuilderClass?.getDeclaredConstructor()?.newInstance()
            builder?.let { b ->
                tapCardConfigBuilderClass?.getMethod("setTimeout", Long::class.java)?.invoke(b, timeoutMs)
                tapCardConfigBuilderClass?.getMethod("enableDebug", Boolean::class.java)?.invoke(b, enableDebug)
                tapCardConfigBuilderClass?.getMethod("tryDirectReadOnFailure", Boolean::class.java)?.invoke(b, tryDirectRead)
                tapCardConfigBuilderClass?.getMethod("continueOnTagNotSupported", Boolean::class.java)?.invoke(b, continueOnUnsupported)
                tapCardConfigBuilderClass?.getMethod("build")?.invoke(b)
            }
        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to parse config", e)
            createConfig()
        }
    }

    /**
     * Initializes TapCard instance via reflection.
     */
    private fun initializeTapCard(config: Any? = null): Boolean {
        val activity = reactContext.currentActivity ?: return false

        return try {
            // Release existing instance if any
            tapCardInstance?.let { existing ->
                try {
                    tapCardClass?.getMethod("release")?.invoke(existing)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            val actualConfig = config ?: createConfig()
            tapCardInstance = if (actualConfig != null) {
                tapCardClass?.getConstructor(android.app.Activity::class.java, Class.forName("io.hyperswitch.tapcard.TapCardConfig"))?.newInstance(activity, actualConfig)
            } else {
                tapCardClass?.getConstructor(android.app.Activity::class.java)?.newInstance(activity)
            }
            tapCardInstance != null
        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to initialize TapCard", e)
            false
        }
    }

    /**
     * Helper to create WritableMap for callback results.
     */
    private fun createResultMap(vararg pairs: Pair<String, Any?>): WritableMap {
        val map = Arguments.createMap()
        for ((key, value) in pairs) {
            when (value) {
                is Boolean -> map.putBoolean(key, value)
                is String -> map.putString(key, value)
                is Int -> map.putInt(key, value)
                is Double -> map.putDouble(key, value)
                null -> map.putNull(key)
                else -> map.putString(key, value.toString())
            }
        }
        return map
    }

    /**
     * Checks if NFC hardware is available and enabled on the device.
     * Callback receives: { available: boolean }
     */
    @ReactMethod
    fun isAvailable(callback: Callback) {
        if (!isTapCardAvailable()) {
            callback.invoke(createResultMap("available" to false, "error" to "TapCard SDK not available"))
            return
        }

        val initialized = initializeTapCard()
        if (!initialized) {
            callback.invoke(createResultMap("available" to false, "error" to "Failed to initialize TapCard"))
            return
        }

        try {
            val available = tapCardClass?.getMethod("isAvailable")?.invoke(tapCardInstance) as? Boolean ?: false
            callback.invoke(createResultMap("available" to available))
        } catch (e: Exception) {
            callback.invoke(createResultMap("available" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Checks if NFC is currently enabled.
     * Callback receives: { enabled: boolean }
     */
    @ReactMethod
    fun isEnabled(callback: Callback) {
        // Same as isAvailable for TapCard
        isAvailable(callback)
    }

    /**
     * Checks NFC permission and availability status using TapCard API.
     * Callback receives: { success: boolean } or { success: false, error: string, code: string }
     */
    @ReactMethod
    fun checkPermissions(callback: Callback) {
        checkAndRequestPermission(callback)
    }

    /**
     * Requests NFC permission from the user.
     * Note: NFC permission is a normal permission, granted at install time.
     */
    @ReactMethod
    fun requestPermissions(callback: Callback) {
        checkPermissions(callback)
    }

    /**
     * Checks NFC permission using TapCard API via reflection.
     * Callback receives: { success: boolean } or { success: false, error: string, code: string }
     */
    @ReactMethod
    fun checkAndRequestPermission(callback: Callback) {
        if (!isTapCardAvailable()) {
            callback.invoke(createErrorResult("TapCard SDK not available", ERROR_SDK_NOT_AVAILABLE))
            return
        }

        val activity = reactContext.currentActivity
        if (activity == null) {
            callback.invoke(createErrorResult("Activity not available", ERROR_GENERIC))
            return
        }

        if (!initializeTapCard()) {
            callback.invoke(createErrorResult("Failed to initialize TapCard", ERROR_GENERIC))
            return
        }

        try {
            val permissionResultClass = Class.forName("io.hyperswitch.tapcard.PermissionResult")
            val permissionErrorClass = Class.forName("io.hyperswitch.tapcard.PermissionError")

            // Create callback proxy for PermissionResult
            val callbackProxy = Proxy.newProxyInstance(
                reactContext.classLoader,
                arrayOf(Class.forName("kotlin.jvm.functions.Function1"))
            ) { _, method, args ->
                if (method.name == "invoke") {
                    val result = args?.get(0)
                    handlePermissionResult(result, callback, permissionResultClass, permissionErrorClass)
                }
                null
            }

            tapCardClass?.getMethod("checkAndRequestPermission", Class.forName("kotlin.jvm.functions.Function1"))
                ?.invoke(tapCardInstance, callbackProxy)

        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to check permissions", e)
            callback.invoke(createErrorResult(e.message ?: "Unknown error", ERROR_GENERIC))
        }
    }

    private fun handlePermissionResult(
        result: Any?,
        callback: Callback,
        permissionResultClass: Class<*>,
        permissionErrorClass: Class<*>
    ) {
        try {
            // Check if result is PermissionResult.Success (object) or PermissionResult.Failed (data class)
            val resultClass = result?.javaClass
            val className = resultClass?.simpleName ?: ""

            if (className.contains("Success")) {
                callback.invoke(createResultMap("success" to true))
            } else {
                // Extract error code from PermissionResult.Failed
                val codeField = resultClass?.getDeclaredField("code")
                codeField?.isAccessible = true
                val errorCode = codeField?.get(result)
                val errorCodeName = errorCode?.javaClass?.getMethod("name")?.invoke(errorCode) as? String ?: "UNKNOWN"

                val errorMsg = when (errorCodeName) {
                    "PERMISSION_NOT_GRANTED" -> "NFC permission not granted"
                    "USER_DENIED" -> "User denied NFC permission"
                    "NFC_DISABLED" -> "NFC is disabled"
                    "NFC_NOT_AVAILABLE" -> "NFC not available on this device"
                    else -> "Permission check failed"
                }

                callback.invoke(createErrorResult(errorMsg, errorCodeName))
            }
        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to handle permission result", e)
            callback.invoke(createErrorResult("Permission check failed", ERROR_GENERIC))
        }
    }

    /**
     * Starts listening for NFC card taps.
     * Callback receives card data or error.
     */
    @ReactMethod
    fun startListening(callback: Callback) {
        if (!isTapCardAvailable()) {
            callback.invoke(createErrorResult("TapCard SDK not available", ERROR_SDK_NOT_AVAILABLE))
            return
        }

        val activity = reactContext.currentActivity
        if (activity == null) {
            callback.invoke(createErrorResult("Activity not available", ERROR_GENERIC))
            return
        }

        if (!initializeTapCard()) {
            callback.invoke(createErrorResult("Failed to initialize TapCard", ERROR_GENERIC))
            return
        }

        // First check permissions
        try {
            val permissionCallback = Proxy.newProxyInstance(
                reactContext.classLoader,
                arrayOf(Class.forName("kotlin.jvm.functions.Function1"))
            ) { _, method, args ->
                if (method.name == "invoke") {
                    val result = args?.get(0)
                    val resultClass = result?.javaClass
                    val className = resultClass?.simpleName ?: ""

                    if (className.contains("Success")) {
                        // Permission granted, start listening
                        startListeningInternal(callback)
                    } else {
                        // Permission failed
                        val codeField = resultClass?.getDeclaredField("code")
                        codeField?.isAccessible = true
                        val errorCode = codeField?.get(result)
                        val errorCodeName = errorCode?.javaClass?.getMethod("name")?.invoke(errorCode) as? String ?: "UNKNOWN"

                        val errorMsg = when (errorCodeName) {
                            "PERMISSION_NOT_GRANTED" -> "NFC permission not granted"
                            "USER_DENIED" -> "User denied NFC permission"
                            "NFC_DISABLED" -> "NFC is disabled"
                            "NFC_NOT_AVAILABLE" -> "NFC not available on this device"
                            else -> "Permission check failed"
                        }
                        callback.invoke(createErrorResult(errorMsg, errorCodeName))
                    }
                }
                null
            }

            tapCardClass?.getMethod("checkAndRequestPermission", Class.forName("kotlin.jvm.functions.Function1"))
                ?.invoke(tapCardInstance, permissionCallback)

        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to start listening", e)
            callback.invoke(createErrorResult(e.message ?: "Unknown error", ERROR_GENERIC))
        }
    }

    private fun startListeningInternal(callback: Callback) {
        try {
            val cardCallback = Proxy.newProxyInstance(
                reactContext.classLoader,
                arrayOf(Class.forName("kotlin.jvm.functions.Function1"))
            ) { _, method, args ->
                if (method.name == "invoke") {
                    val result = args?.get(0)
                    handleCardResult(result, callback)
                }
                null
            }

            tapCardClass?.getMethod("startListening", Class.forName("kotlin.jvm.functions.Function1"))
                ?.invoke(tapCardInstance, cardCallback)

            // Signal that listening has started successfully
            callback.invoke(createResultMap("success" to true))

        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to start listening internal", e)
            callback.invoke(createErrorResult(e.message ?: "Unknown error", ERROR_GENERIC))
        }
    }

    private fun handleCardResult(result: Any?, callback: Callback) {
        try {
            val resultClass = result?.javaClass
            val className = resultClass?.simpleName ?: ""

            when {
                className.contains("CardDetected") -> {
                    // Extract TapCardData from CardDetected
                    val dataField = resultClass?.getDeclaredField("data")
                    dataField?.isAccessible = true
                    val cardData = dataField?.get(result)

                    val cardDataClass = cardData?.javaClass
                    val cardNumber = getFieldValue(cardDataClass, cardData, "cardNumber")
                    val maskedCardNumber = getFieldValue(cardDataClass, cardData, "maskedCardNumber")
                    val expiryDate = getFieldValue(cardDataClass, cardData, "expiryDate")
                    val month = getFieldValue(cardDataClass, cardData, "month")
                    val year = getFieldValue(cardDataClass, cardData, "year")
                    val network = getFieldValue(cardDataClass, cardData, "network")
                    val cardholderName = getFieldValue(cardDataClass, cardData, "cardholderName")
                    val country = getFieldValue(cardDataClass, cardData, "country")

                    Log.i("Manideep", "Card detected: cardNumber=$cardNumber, expiry=$expiryDate")

                    // Emit event for JS to receive
                    emitEvent(EVENT_CARD_DETECTED, createResultMap(
                        "success" to true,
                        "cardNumber" to cardNumber,
                        "maskedCardNumber" to maskedCardNumber,
                        "expiryDate" to expiryDate,
                        "month" to month,
                        "year" to year,
                        "network" to network,
                        "cardholderName" to cardholderName,
                        "country" to country
                    ))
                }
                className.contains("TagNotSupported") -> {
                    emitEvent(EVENT_ERROR, createErrorResult("Tag not supported", "TAG_NOT_SUPPORTED"))
                }
                className.contains("NfcDisabled") -> {
                    emitEvent(EVENT_ERROR, createErrorResult("NFC disabled", ERROR_NFC_DISABLED))
                }
                className.contains("FailedToRead") -> {
                    val errorField = resultClass?.getDeclaredField("error")
                    errorField?.isAccessible = true
                    val error = errorField?.get(result)
                    val errorMessage = error?.javaClass?.getMethod("getMessage")?.invoke(error) as? String
                    emitEvent(EVENT_ERROR, createErrorResult(errorMessage ?: "Failed to read card", ERROR_READ_FAILED))
                }
                else -> {
                    emitEvent(EVENT_ERROR, createErrorResult("Unknown result type", ERROR_GENERIC))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to handle card result", e)
            emitEvent(EVENT_ERROR, createErrorResult("Failed to process card result", ERROR_GENERIC))
        }
    }

    /**
     * Emits an event to JavaScript.
     */
    private fun emitEvent(eventName: String, params: WritableMap) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, params)
    }

    private fun getFieldValue(clazz: Class<*>?, instance: Any?, fieldName: String): String? {
        return try {
            val field = clazz?.getDeclaredField(fieldName)
            field?.isAccessible = true
            field?.get(instance)?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Starts listening with custom configuration.
     */
    @ReactMethod
    fun startListeningWithConfig(configJson: String, callback: Callback) {
        if (!isTapCardAvailable()) {
            callback.invoke(createErrorResult("TapCard SDK not available", ERROR_SDK_NOT_AVAILABLE))
            return
        }

        val activity = reactContext.currentActivity
        if (activity == null) {
            callback.invoke(createErrorResult("Activity not available", ERROR_GENERIC))
            return
        }

        val config = parseConfig(configJson)
        if (!initializeTapCard(config)) {
            callback.invoke(createErrorResult("Failed to initialize TapCard with config", ERROR_GENERIC))
            return
        }

        // First check permissions
        try {
            val permissionCallback = Proxy.newProxyInstance(
                reactContext.classLoader,
                arrayOf(Class.forName("kotlin.jvm.functions.Function1"))
            ) { _, method, args ->
                if (method.name == "invoke") {
                    val result = args?.get(0)
                    val resultClass = result?.javaClass
                    val className = resultClass?.simpleName ?: ""

                    if (className.contains("Success")) {
                        startListeningInternal(callback)
                    } else {
                        val codeField = resultClass?.getDeclaredField("code")
                        codeField?.isAccessible = true
                        val errorCode = codeField?.get(result)
                        val errorCodeName = errorCode?.javaClass?.getMethod("name")?.invoke(errorCode) as? String ?: "UNKNOWN"

                        val errorMsg = when (errorCodeName) {
                            "PERMISSION_NOT_GRANTED" -> "NFC permission not granted"
                            "USER_DENIED" -> "User denied NFC permission"
                            "NFC_DISABLED" -> "NFC is disabled"
                            "NFC_NOT_AVAILABLE" -> "NFC not available on this device"
                            else -> "Permission check failed"
                        }
                        callback.invoke(createErrorResult(errorMsg, errorCodeName))
                    }
                }
                null
            }

            tapCardClass?.getMethod("checkAndRequestPermission", Class.forName("kotlin.jvm.functions.Function1"))
                ?.invoke(tapCardInstance, permissionCallback)

        } catch (e: Exception) {
            android.util.Log.e(NAME, "Failed to start listening with config", e)
            callback.invoke(createErrorResult(e.message ?: "Unknown error", ERROR_GENERIC))
        }
    }

    /**
     * Stops listening for NFC cards.
     */
    @ReactMethod
    fun stopListening(callback: Callback) {
        try {
            tapCardClass?.getMethod("stopListening")?.invoke(tapCardInstance)
            callback.invoke(createResultMap("success" to true))
        } catch (e: Exception) {
            callback.invoke(createResultMap("success" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Opens NFC settings on the device.
     */
    @ReactMethod
    fun openNfcSettings(callback: Callback) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            reactContext.startActivity(intent)
            callback.invoke(createResultMap("success" to true))
        } catch (e: Exception) {
            callback.invoke(createResultMap("success" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Releases TapCard resources.
     */
    @ReactMethod
    fun release(callback: Callback) {
        try {
            tapCardClass?.getMethod("release")?.invoke(tapCardInstance)
            tapCardInstance = null
            callback.invoke(createResultMap("success" to true))
        } catch (e: Exception) {
            callback.invoke(createResultMap("success" to false, "error" to (e.message ?: "Unknown error")))
        }
    }

    private fun createErrorResult(message: String, code: String): WritableMap {
        return createResultMap(
            "success" to false,
            "error" to message,
            "code" to code
        )
    }

    companion object {
        const val NAME = "TapCardModule"

        // Error codes
        const val ERROR_SDK_NOT_AVAILABLE = "SDK_NOT_AVAILABLE"
        const val ERROR_NFC_DISABLED = "NFC_DISABLED"
        const val ERROR_PERMISSION_DENIED = "PERMISSION_DENIED"
        const val ERROR_READ_FAILED = "READ_FAILED"
        const val ERROR_GENERIC = "GENERIC_ERROR"

        // Event names
        const val EVENT_CARD_DETECTED = "onCardDetected"
        const val EVENT_ERROR = "onError"

        /**
         * Checks if the TapCard SDK is available.
         */
        @JvmStatic
        fun isSdkAvailable(reactContext: ReactApplicationContext): Boolean {
            return try {
                Class.forName("io.hyperswitch.tapcard.TapCard")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }
}
