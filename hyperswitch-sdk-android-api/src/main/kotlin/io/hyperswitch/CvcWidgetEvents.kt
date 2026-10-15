package io.hyperswitch

import kotlinx.parcelize.Parcelize

object CvcWidgetEvents {

    /**
     * CVC status event - emitted when the CVC turns empty/filled or complete/incomplete.
     * Event type: "cvcStatusChange"
     * Payload: PaymentEventData.CvcStatus, nested as `cvcStatus`
     *
     * Fields:
     * - isCvcEmpty: Boolean             Whether the CVC field is empty
     * - isCvcComplete: Boolean          Whether the CVC passes length validation
     */
    @Parcelize
    object CvcStatusChange : EventType("cvcStatusChange")
}
