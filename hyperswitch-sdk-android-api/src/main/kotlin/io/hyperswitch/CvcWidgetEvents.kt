package io.hyperswitch

object CvcWidgetEvents {

    /**
     * CVC status event - emitted when CVC field state changes.
     * Event type: "cvcStatusChange"
     * Payload: PaymentEventData.CvcStatus
     *
     * Fields:
     * - isCvcFocused: Boolean           Whether the CVC field is focused
     * - isCvcBlur: Boolean              Whether the CVC field has lost focus
     * - isCvcEmpty: Boolean             Whether the CVC field is empty
     */
    object CvcStatusChange : EventType("cvcStatusChange")
}
