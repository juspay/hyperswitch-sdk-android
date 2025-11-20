package io.hyperswitch.threeds.api

import androidx.annotation.Keep
import io.hyperswitch.threeds.models.ThreeDSError

/**
 * Result of 3DS operations.
 * Simple sealed class for clean when() statements.
 */
@Keep
sealed class ThreeDSResult<out T> {
    @Keep
    data class Success<T>(val value: T) : ThreeDSResult<T>()
    @Keep
    data class Error(val message: String, val error: ThreeDSError? = null) : ThreeDSResult<Nothing>()
}
