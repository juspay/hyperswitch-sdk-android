package io.hyperswitch.threeds.api

import android.app.Activity
import android.os.Looper
import androidx.annotation.Keep
import io.hyperswitch.threeds.ThreeDSManager
import io.hyperswitch.threeds.callbacks.AuthParametersCallback
import io.hyperswitch.threeds.callbacks.ChallengeCallback
import io.hyperswitch.threeds.models.AuthenticationParameters
import io.hyperswitch.threeds.models.ThreeDSError
import io.hyperswitch.threeds.models.ChallengeParameters
import io.hyperswitch.threeds.models.ChallengeResult


@Keep
class ThreeDSTransaction internal constructor(
    private val transactionId: String,
    private val threeDSManager: ThreeDSManager
) {

    /**
     * @param callback Callback with ThreeDSResult
     */
    @Keep
    fun getAuthenticationRequestParameters(
        callback: (ThreeDSResult<AuthenticationRequestParameters>) -> Unit
    ) {
        threeDSManager.getAuthenticationRequestParameters(transactionId, object : AuthParametersCallback {
            override fun onAuthParametersSuccess(parameters: AuthenticationParameters) {
                val unifiedParams = AuthenticationRequestParameters(
                    sdkTransactionID = parameters.sdkTransactionId,
                    sdkAppID = parameters.sdkAppId,
                    sdkReferenceNumber = parameters.sdkReferenceNumber,
                    sdkEphemeralPublicKey = parameters.sdkEphemeralPublicKey,
                    deviceData = parameters.deviceData
                )
                callback(ThreeDSResult.Success(unifiedParams))
            }

            override fun onAuthParametersFailure(error: ThreeDSError) {
                callback(ThreeDSResult.Error(error.message, error))
            }
        })
    }

    /**
     * Perform challenge flow.
     *
     * **IMPORTANT**: This method MUST be called from the main (UI) thread.
     *
     * @param activity The Activity that will host the challenge UI
     * @param challengeParameters The challenge parameters from the backend response
     * @param challengeStatusReceiver Callback to receive challenge status updates
     * @param timeout Timeout in minutes (minimum 5)
     * @throws IllegalStateException if not called from main thread
     */
    fun doChallenge(
        activity: Activity,
        challengeParameters: ChallengeParameters,
        challengeStatusReceiver: ChallengeStatusReceiver,
        timeout: Int
    ) {
        // Enforce main thread requirement
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException(
                "doChallenge() must be called from the main thread. " +
                        "Current thread: ${Thread.currentThread().name}. " +
                        "Use withContext(Dispatchers.Main) { } or runOnUiThread { } to switch to main thread."
            )
        }

        val internalCallback = object : ChallengeCallback {
            override fun onChallengeSuccess(result: ChallengeResult) {
                challengeStatusReceiver.completed(CompletionEvent(result.transactionId))
            }

            override fun onChallengeCancelled() {
                challengeStatusReceiver.cancelled()
            }

            override fun onChallengeFailure(error: ThreeDSError) {
                when (error.code) {
                    "CHALLENGE_TIMEOUT" -> challengeStatusReceiver.timedout()
                    "PROTOCOL_ERROR" -> challengeStatusReceiver.protocolError(
                        ProtocolErrorEvent(ErrorMessage(error.message))
                    )
                    else -> challengeStatusReceiver.runtimeError(
                        RuntimeErrorEvent(error.message)
                    )
                }
            }

            override fun onChallengeTimeout() {
                challengeStatusReceiver.timedout()
            }
        }

        threeDSManager.doChallenge(activity, challengeParameters, timeout, internalCallback)
    }
}
