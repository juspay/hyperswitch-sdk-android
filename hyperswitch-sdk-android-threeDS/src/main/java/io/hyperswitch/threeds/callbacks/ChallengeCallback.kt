package io.hyperswitch.threeds.callbacks

import androidx.annotation.Keep
import io.hyperswitch.threeds.models.ChallengeResult
import io.hyperswitch.threeds.models.ThreeDSError

/**
 * Callback interface for 3DS challenge flow operations.
 */
@Keep
interface ChallengeCallback {

    /**
     * Called when the challenge flow is completed successfully.
     *
     * @param result The challenge result containing authentication outcome
     */
    @Keep
    fun onChallengeSuccess(result: ChallengeResult)

    /**
     * Called when the challenge flow fails.
     *
     * @param error Details about the challenge failure
     */
    @Keep
    fun onChallengeFailure(error: ThreeDSError)

    /**
     * Called when the challenge flow is cancelled by the user.
     */
    @Keep
    fun onChallengeCancelled()

    /**
     * Called when the challenge UI times out.
     */
    @Keep
    fun onChallengeTimeout()
}
