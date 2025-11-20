package io.hyperswitch.threeds.trident

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import io.hyperswitch.threeds.callbacks.AuthParametersCallback
import io.hyperswitch.threeds.callbacks.ChallengeCallback
import io.hyperswitch.threeds.callbacks.InitializationCallback
import io.hyperswitch.threeds.callbacks.TransactionCallback
import io.hyperswitch.threeds.models.*
import io.hyperswitch.threeds.provider.ThreeDSProvider
import `in`.juspay.trident.core.FileHelper
import `in`.juspay.trident.core.Logger
import `in`.juspay.trident.core.SdkHelper
import `in`.juspay.trident.core.ThreeDS2Service
import `in`.juspay.trident.core.Transaction
import `in`.juspay.trident.data.ChallengeParameters as TridentChallengeParameters
import `in`.juspay.trident.data.ChallengeStatusReceiver
import `in`.juspay.trident.data.CompletionEvent
import `in`.juspay.trident.data.ProtocolErrorEvent
import `in`.juspay.trident.data.RuntimeErrorEvent
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap


/**
 * Trident implementation of ThreeDSProvider using Juspay's Trident SDK.
 * Provides 3DS authentication capabilities through the Trident SDK.
 */
@Keep
class TridentThreeDSProvider : ThreeDSProvider {

    private var isInitialized = false
    private var configuration: ThreeDSConfiguration? = null
    private var threeDS2Service: ThreeDS2Service? = null
    @Volatile
    private var currentTransaction: Transaction? = null
    private var context: Context? = null
    private val transactions = ConcurrentHashMap<String, Transaction>()

    // SDK Helper implementation
    private val sdkHelper = object : SdkHelper {
        override val logger = object : Logger {
            override fun addLogToPersistedQueue(logLine: JSONObject) {
                // Log to persistent queue if needed
            }

            override fun track(logLine: JSONObject) {
                // Track logs if needed
                Log.d("logg",logLine.toString())
            }
        }

        override val fileHelper = object : FileHelper {
            override fun renewFile(endpoint: String, fileName: String, startTime: Long) {
                // Renew file if needed
            }

            override fun readFromFile(fileName: String): String {
                return fileName
            }
        }
    }

    override fun initialize(
        context: Context,
        configuration: ThreeDSConfiguration,
        callback: InitializationCallback
    ) {
        this.configuration = configuration
        this.context = context

        try {
            // Create Trident SDK instance with helper
            threeDS2Service = ThreeDS2Service.createNewInstance(sdkHelper)

            // Set up configuration parameters
            TridentConfigurator.setConfigParameters()

            // Initialize Trident SDK with context and proper config parameters
            threeDS2Service?.initialise(context, TridentConfigurator.configParams, "en-US", null)

            Log.d("TridentProvider", "Trident SDK initialized successfully")

            // Mark as initialized only after successful initialization
            isInitialized = true
            callback.onInitializationSuccess()

        } catch (e: Exception) {
            // Check if it's already initialized - this is acceptable
            if (e.message?.contains("ThreeDS2Service is already initialized", ignoreCase = true) == true) {
                Log.d("TridentProvider", "Trident SDK already initialized")
                isInitialized = true
                callback.onInitializationSuccess()
            } else {
                Log.e("TridentProvider", "Failed to initialize Trident SDK: ${e.message}")
                isInitialized = false
                callback.onInitializationFailure(
                    ThreeDSError(
                        code = "INIT_FAILED",
                        message = "Failed to initialize Trident SDK: ${e.message}",
                        details = e.toString(),
                        errorType = ErrorType.PROVIDER_ERROR
                    )
                )
            }
        }
    }

    /**
     * Check if the Trident SDK is properly initialized and ready for transactions
     */
    private fun isTridentSDKReady(): Boolean {
        return threeDS2Service != null && context != null && isInitialized
    }

    override fun isInitialized(): Boolean = isInitialized

    override fun getSDKVersion(): String {
        return threeDS2Service?.getSDKVersion() ?: "trident-1.0.8-rc.04"
    }

    override fun getProviderType(): ThreeDSProviderType =
        ThreeDSProviderType.TRIDENT

    override fun createTransaction(
        request: TransactionRequest,
        callback: TransactionCallback
    ) {
        if (!isTridentSDKReady()) {
            val errorMessage = when {
                !isInitialized -> "Provider not initialized"
                threeDS2Service == null -> "ThreeDS2Service is null - SDK service not properly initialized"
                context == null -> "Context not set - Call initializeWithContext() first"
                else -> "Trident SDK not ready for transactions"
            }

            callback.onTransactionFailure(
                ThreeDSError(
                    code = "SDK_NOT_READY",
                    message = errorMessage,
                    details = "Ensure provider is initialized and context is set before creating transactions",
                    errorType = ErrorType.PROVIDER_ERROR
                )
            )
            return
        }

        try {
            // Get directory server ID based on card network
            val directoryServerId = when (request.cardNetwork?.uppercase()) {
                "VISA" -> ThreeDS2Service.getDirectoryServerId("VISA")
                "MASTERCARD", "MC" -> ThreeDS2Service.getDirectoryServerId("MASTERCARD")
                "AMEX", "AMERICAN_EXPRESS" -> ThreeDS2Service.getDirectoryServerId("AMEX")
                else -> ThreeDS2Service.getDirectoryServerId("VISA") // Default to VISA
            }

            Log.d("TridentProvider", "Creating transaction with directory server ID: $directoryServerId, message version: ${request.messageVersion}")

            // Create transaction using Trident SDK with callback
            threeDS2Service?.createTransaction(
                directoryServerID = directoryServerId,
                messageVersion = request.messageVersion,
                onTransaction = { sdkTransaction ->
                    Log.d("TridentProvider", "Transaction created successfully")
                    currentTransaction = sdkTransaction

                    // Store transaction for later retrieval
                    val transactionId = "txn_${System.currentTimeMillis()}"
                    transactions[transactionId] = sdkTransaction

                    // Get authentication request parameters immediately
                    val aReq = sdkTransaction.getAuthenticationRequestParameters()

                    if (aReq != null) {
                        Log.d("TridentProvider", "Authentication request parameters obtained successfully")
                        callback.onTransactionSuccess(
                            TransactionResponse(
                                transactionId = transactionId,
                                sdkTransactionId = aReq.sdkTransactionID,
                                serverTransactionId = aReq.sdkTransactionID, // Use SDK transaction ID as server transaction ID
                                acsTransactionId = null,
                                dsTransactionId = null,
                                messageVersion = request.messageVersion,
                                deviceData = aReq.deviceData,
                                sdkAppId = aReq.sdkAppID,
                                sdkEncryptionData = "", // Not available in Trident SDK
//                                sdkEphemeralPublicKey = transformEphemeralKeyJson(aReq.sdkEphemeralPublicKey)?:"",
                                sdkEphemeralPublicKey = aReq.sdkEphemeralPublicKey?:"",

                                sdkReferenceNumber = aReq.sdkReferenceNumber
                            )
                        )
                    } else {
                        Log.e("TridentProvider", "Authentication request parameters were null")
                        callback.onTransactionFailure(
                            ThreeDSError(
                                code = "TRANSACTION_FAILED",
                                message = "Failed to get authentication parameters",
                                details = "Authentication request parameters were null",
                                errorType = ErrorType.PROVIDER_ERROR
                            )
                        )
                    }
                }
            )

        } catch (e: Exception) {
            Log.e("TridentProvider", "Exception in createTransaction: ${e.message}", e)
            callback.onTransactionFailure(
                ThreeDSError(
                    code = "TRANSACTION_ERROR",
                    message = "Transaction creation error: ${e.message}",
                    details = e.toString(),
                    errorType = ErrorType.PROVIDER_ERROR
                )
            )
        }
    }

    override fun getAuthenticationRequestParameters(
        transactionId: String,
        callback: AuthParametersCallback
    ) {
        if (!isInitialized) {
            callback.onAuthParametersFailure(
                ThreeDSError(
                    code = "NOT_INITIALIZED",
                    message = "Provider not initialized",
                    details = null,
                    errorType = ErrorType.PROVIDER_ERROR
                )
            )
            return
        }

        try {
            // Get the transaction by ID from our stored transactions
            val transaction = transactions[transactionId] ?: currentTransaction

            if (transaction != null) {
                val result = transaction.getAuthenticationRequestParameters()
                if (result != null) {
                    callback.onAuthParametersSuccess(
                        AuthenticationParameters(
                            deviceData = result.deviceData,
                            sdkTransactionId = result.sdkTransactionID,
                            sdkAppId = result.sdkAppID,
                            sdkEncryptionData = "", // Not available in Trident SDK
//                            sdkEphemeralPublicKey =  transformEphemeralKeyJson(result.sdkEphemeralPublicKey) ?: "",
                            sdkEphemeralPublicKey =  result.sdkEphemeralPublicKey ?: "",

                            sdkReferenceNumber = result.sdkReferenceNumber,
                            messageVersion = result.messageVersion,
                            threeDSCompInd = "Y"
                        )
                    )
                } else {
                    callback.onAuthParametersFailure(
                        ThreeDSError(
                            code = "AUTH_PARAMS_FAILED",
                            message = "Failed to get authentication parameters",
                            details = "Trident SDK returned null parameters",
                            errorType = ErrorType.PROVIDER_ERROR
                        )
                    )
                }
            } else {
                callback.onAuthParametersFailure(
                    ThreeDSError(
                        code = "TRANSACTION_NOT_FOUND",
                        message = "Transaction not found",
                        details = "No transaction found with ID: $transactionId",
                        errorType = ErrorType.PROVIDER_ERROR
                    )
                )
            }

        } catch (e: Exception) {
            callback.onAuthParametersFailure(
                ThreeDSError(
                    code = "AUTH_PARAMS_ERROR",
                    message = "Authentication parameters error: ${e.message}",
                    details = e.toString(),
                    errorType = ErrorType.PROVIDER_ERROR
                )
            )
        }
    }

    override fun doChallenge(
        activity: Activity,
        parameters: ChallengeParameters,
        timeout: Int,
        callback: ChallengeCallback
    ) {
        if (!isInitialized) {
            callback.onChallengeFailure(
                ThreeDSError(
                    code = "NOT_INITIALIZED",
                    message = "Provider not initialized",
                    details = null,
                    errorType = ErrorType.PROVIDER_ERROR
                )
            )
            return
        }

        try {
            // Use current transaction for challenge
            val transaction = currentTransaction

            if (transaction != null) {
                // Create Trident challenge parameters using the correct constructor
                val tridentChallengeParams = TridentChallengeParameters(
                    threeDSServerTransactionID = parameters.threeDSServerTransactionId ?: "",
                    acsTransactionID = parameters.acsTransactionId ?: "",
                    acsRefNumber = parameters.acsReferenceNumber ?: "",
                    acsSignedContent = parameters.acsSignedContent ?: "",
                    threeDSRequestorAppURL = parameters.threeDSRequestorAppURL
                )
                
                // Create challenge status receiver
                val challengeStatusReceiver = object : ChallengeStatusReceiver {
                    override fun completed(completionEvent: CompletionEvent) {
                        callback.onChallengeSuccess(
                            ChallengeResult(
                                transactionId = completionEvent.sdkTransactionId ?: "",
                                status = AuthenticationStatus.Y,
                                authenticationValue = completionEvent.transactionStatus ?: "",
                                eci = completionEvent.transactionStatus ?: ""
                            )
                        )
                    }

                    override fun cancelled() {
                        callback.onChallengeCancelled()
                    }

                    override fun timedout() {
                        callback.onChallengeFailure(
                            ThreeDSError(
                                code = "CHALLENGE_TIMEOUT",
                                message = "Challenge timed out",
                                details = "Challenge flow timed out",
                                errorType = ErrorType.CHALLENGE_ERROR
                            )
                        )
                    }

                    override fun protocolError(protocolErrorEvent: ProtocolErrorEvent) {
                        callback.onChallengeFailure(
                            ThreeDSError(
                                code = "PROTOCOL_ERROR",
                                message = "Protocol error during challenge",
                                details = protocolErrorEvent.errorMessage?.errorDescription ?: "Unknown protocol error",
                                errorType = ErrorType.CHALLENGE_ERROR
                            )
                        )
                    }

                    override fun runtimeError(runtimeErrorEvent: RuntimeErrorEvent) {
                        callback.onChallengeFailure(
                            ThreeDSError(
                                code = "RUNTIME_ERROR",
                                message = "Runtime error during challenge",
                                details = runtimeErrorEvent.errorMessage ?: "Unknown runtime error",
                                errorType = ErrorType.CHALLENGE_ERROR
                            )
                        )
                    }
                }

                // Use timeout parameter directly (already in minutes)
                val timeoutInMinutes = timeout.coerceAtLeast(5)
                transaction.doChallenge(
                    activity,
                    tridentChallengeParams,
                    challengeStatusReceiver,
                    timeoutInMinutes,
                    "{}" // bankDetails
                )

            } else {
                callback.onChallengeFailure(
                    ThreeDSError(
                        code = "TRANSACTION_NOT_FOUND",
                        message = "Transaction not found for challenge",
                        details = "No current transaction available",
                        errorType = ErrorType.PROVIDER_ERROR
                    )
                )
            }

        } catch (e: Exception) {
            callback.onChallengeFailure(
                ThreeDSError(
                    code = "CHALLENGE_ERROR",
                    message = "Challenge error: ${e.message}",
                    details = e.toString(),
                    errorType = ErrorType.PROVIDER_ERROR
                )
            )
        }
    }

    override fun cleanup() {
        try {
            // Cleanup Trident SDK resources
            transactions.clear()
            currentTransaction = null
            threeDS2Service = null
            context = null
            isInitialized = false
            configuration = null
        } catch (e: Exception) {
            // Log cleanup error but don't throw
            Log.w("TridentProvider", "Error during cleanup: ${e.message}")
        }
    }

    override fun supportsConfiguration(configuration: ThreeDSConfiguration): Boolean {
        return true
    }
}
