# TapCard Integration Guide

TapCard is the public-facing NFC card reading API for the Hyperswitch Android SDK. It enables merchants to read payment card details via NFC contactless technology.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Error Handling](#error-handling)
- [Lifecycle Management](#lifecycle-management)
- [Best Practices](#best-practices)

## Overview

TapCard provides a simplified interface for NFC card reading with:

- Support for 15+ card networks (Visa, Mastercard, Amex, Discover, RuPay, JCB, UnionPay, etc.)
- Automatic fallback strategies for different card profiles
- Type-safe result handling with sealed classes
- Thread-safe implementation with atomic operations
- Configurable timeout and debug logging

## Prerequisites

1. **NFC Hardware**: Device must have NFC capability
2. **Android Permissions**: Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="false" />
```

## Installation

Add the TapCard module dependency to your app's `build.gradle`:

```kotlin
dependencies {
    implementation(project(":hyperswitch-sdk-android-tapcard"))
}
```

## Quick Start

```kotlin
import io.hyperswitch.tapcard.TapCard
import io.hyperswitch.tapcard.TapCardConfigBuilder
import io.hyperswitch.tapcard.CardResult
import io.hyperswitch.tapcard.PermissionResult

class PaymentActivity : AppCompatActivity() {
    private var tapCard: TapCard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TapCard with configuration
        val config = TapCardConfigBuilder()
            .setTimeout(30_000)  // 30 second timeout
            .enableDebug(BuildConfig.DEBUG)
            .build()

        tapCard = TapCard(this, config)

        // Check permissions and start listening
        tapCard?.checkAndRequestPermission { result ->
            when (result) {
                is PermissionResult.Success -> {
                    startCardReading()
                }
                is PermissionResult.Failed -> {
                    handlePermissionError(result.code)
                }
            }
        }
    }

    private fun startCardReading() {
        tapCard?.startListening { result ->
            when (result) {
                is CardResult.CardDetected -> {
                    val cardData = result.data
                    println("Card: ${cardData.maskedCardNumber}")
                    println("Network: ${cardData.network}")
                    println("Expiry: ${cardData.expiryDate}")
                    // Process payment with card details
                }
                CardResult.TagNotSupported -> {
                    println("Unsupported card/tag detected")
                }
                CardResult.NfcDisabled -> {
                    println("NFC is disabled")
                }
                is CardResult.FailedToRead -> {
                    println("Read failed: ${result.error.message}")
                }
            }
        }
    }

    private fun handlePermissionError(code: PermissionError) {
        when (code) {
            PermissionError.PERMISSION_NOT_GRANTED -> {
                // NFC permission not granted (rare, it's a normal permission)
            }
            PermissionError.USER_DENIED -> {
                // User denied permission
            }
            PermissionError.NFC_DISABLED -> {
                // Prompt user to enable NFC in settings
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
            PermissionError.NFC_NOT_AVAILABLE -> {
                // Device doesn't have NFC hardware
            }
        }
    }

    override fun onPause() {
        super.onPause()
        tapCard?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        tapCard?.release()
        tapCard = null
    }
}
```

## Configuration

### TapCardConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `timeoutMs` | Long | 30000 | Maximum time to wait for card reading (milliseconds) |
| `enableDebug` | Boolean | false | Enable debug logging for troubleshooting |
| `tryDirectReadOnFailure` | Boolean | true | Attempt direct record reading when normal EMV flow fails |
| `continueOnTagNotSupported` | Boolean | false | Keep listening when non-supported tag is detected |

### Builder Pattern

```kotlin
val config = TapCardConfigBuilder()
    .setTimeout(60_000)           // 60 second timeout
    .enableDebug(true)            // Enable debug logs
    .tryDirectReadOnFailure(true) // Enable fallback reading
    .continueOnTagNotSupported(true) // Continue on unsupported tags
    .build()
```

## API Reference

### TapCard

#### Constructor

```kotlin
TapCard(activity: Activity, config: TapCardConfig = TapCardConfig())
```

#### Methods

##### `isAvailable(): Boolean`
Checks if NFC hardware is present and enabled.

```kotlin
if (tapCard.isAvailable()) {
    // NFC is ready to use
}
```

##### `checkAndRequestPermission(callback: (PermissionResult) -> Unit)`
Checks NFC availability and invokes callback with result.

```kotlin
tapCard.checkAndRequestPermission { result ->
    when (result) {
        is PermissionResult.Success -> { /* Ready to scan */ }
        is PermissionResult.Failed -> { /* Handle error */ }
    }
}
```

##### `startListening(callback: (CardResult) -> Unit)`
Starts listening for NFC card taps. Callback is invoked on main thread.

```kotlin
tapCard.startListening { result ->
    when (result) {
        is CardResult.CardDetected -> {
            val data = result.data
            // Access card details:
            // data.cardNumber          // Full PAN (use carefully)
            // data.maskedCardNumber    // Masked PAN (e.g., "4111 **** **** 1111")
            // data.network             // Card network name (e.g., "Visa")
            // data.expiryDate          // Format: "MM/YY"
            // data.month               // Expiry month: "MM"
            // data.year                // Expiry year: "YY"
            // data.cardholderName      // Cardholder name if available
            // data.country             // Issuer country code (ISO 3166-1 alpha-3)
        }
        // ... handle other results
    }
}
```

##### `stopListening()`
Stops listening for NFC cards. Safe to call even if not listening.

```kotlin
override fun onPause() {
    super.onPause()
    tapCard.stopListening()
}
```

##### `release()`
Releases all resources. Call when activity/fragment is destroyed.

```kotlin
override fun onDestroy() {
    super.onDestroy()
    tapCard.release()
}
```

## Error Handling

### PermissionResult

Sealed class representing permission check outcomes:

```kotlin
sealed class PermissionResult {
    object Success : PermissionResult()
    data class Failed(val code: PermissionError) : PermissionResult()
}
```

### PermissionError

```kotlin
enum class PermissionError {
    PERMISSION_NOT_GRANTED,  // Manifest permission not granted
    USER_DENIED,             // User denied permission (legacy)
    NFC_DISABLED,            // NFC is turned off in settings
    NFC_NOT_AVAILABLE        // Device lacks NFC hardware
}
```

### CardResult

Sealed class representing card reading outcomes:

```kotlin
sealed class CardResult {
    data class CardDetected(val data: TapCardData) : CardResult()
    object TagNotSupported : CardResult()
    object NfcDisabled : CardResult()
    data class FailedToRead(val error: TapCardException) : CardResult()
}
```

### TapCardData

Data class containing extracted card information:

| Property | Type | Description |
|----------|------|-------------|
| `cardNumber` | String? | Full Primary Account Number (PAN) |
| `maskedCardNumber` | String | Masked PAN for display |
| `expiryDate` | String? | Expiry in MM/YY format |
| `month` | String? | Expiry month (MM) |
| `year` | String? | Expiry year (YY) |
| `network` | String? | Card network name (Visa, Mastercard, etc.) |
| `country` | String? | Issuer country code |
| `cardholderName` | String? | Cardholder name if available |

## Lifecycle Management

Proper lifecycle management is crucial to prevent memory leaks and ensure clean NFC state:

```kotlin
class PaymentActivity : AppCompatActivity() {
    private var tapCard: TapCard? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize TapCard once
        tapCard = TapCard(this, TapCardConfig())
    }

    override fun onResume() {
        super.onResume()
        // Start listening when activity is visible
        tapCard?.checkAndRequestPermission { result ->
            if (result is PermissionResult.Success) {
                tapCard?.startListening(::handleCardResult)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Always stop listening when activity loses focus
        tapCard?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release resources when activity is destroyed
        tapCard?.release()
        tapCard = null
    }

    private fun handleCardResult(result: CardResult) {
        // Handle result...
        // Note: Listening automatically stops when a result is received
    }
}
```

## Best Practices

### 1. Always Check NFC Availability

```kotlin
if (!tapCard.isAvailable()) {
    // Show NFC disabled or unavailable message
    return
}
```

### 2. Handle All Result Types

```kotlin
tapCard.startListening { result ->
    when (result) {
        is CardResult.CardDetected -> { /* Process card */ }
        CardResult.TagNotSupported -> { /* Show unsupported message */ }
        CardResult.NfcDisabled -> { /* NFC turned off mid-read */ }
        is CardResult.FailedToRead -> { /* Log error */ }
    }
}
```

### 3. Use Continue-on-Unsupported for Better UX

```kotlin
val config = TapCardConfigBuilder()
    .continueOnTagNotSupported(true) // Keep listening after unsupported tag
    .build()
```

This allows users to tap multiple cards without restarting the session.

### 4. Never Log Full Card Numbers

```kotlin
// BAD - Don't do this
Log.d("Card", "Number: ${data.cardNumber}")

// GOOD - Use masked version
Log.d("Card", "Number: ${data.maskedCardNumber}")
```

### 5. Handle NFC Settings

```kotlin
when (result.code) {
    PermissionError.NFC_DISABLED -> {
        AlertDialog.Builder(this)
            .setTitle("NFC Disabled")
            .setMessage("Please enable NFC to scan cards")
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    // ...
}
```

### 6. Test with Real Cards

Different card networks and issuers may behave differently. Always test with:
- Multiple card networks (Visa, Mastercard, Amex, etc.)
- Different card generations (contactless-only vs dual-interface)
- Various issuer banks

## Supported Card Networks

| Network | AID Prefix | Status |
|---------|-----------|--------|
| Visa | A000000003 | Supported |
| Mastercard | A000000004 | Supported |
| American Express | A000000025 | Supported |
| Discover | A000000152 | Supported |
| RuPay | A000000524 | Supported |
| JCB | A000000065 | Supported |
| UnionPay | A000000333 | Supported |
| Interac | A000000277 | Supported |
| MIR | A000000658 | Supported |
| Troy | A000000559 | Supported |
| Verve | A000000371 | Supported |
| Bancontact | A000000340 | Supported |
| GIM-UEMOA | B000000069 | Supported |
| TWINT | A000000806 | Supported |

## Troubleshooting

### "Card reading error: No AID in PPSE"

The card doesn't advertise its application via PPSE. Try:
- Direct AID selection is attempted automatically
- Some transit/loyalty cards may not work

### "Tag not supported"

The detected tag is not an EMV payment card. Common causes:
- Transport cards (may use different protocols)
- Access cards
- NFC tags/stickers

### "Connection lost"

Card was removed too quickly or read was interrupted. Ensure:
- Card stays near the NFC antenna during read
- No metal objects interfere with signal
- Device NFC antenna is clear

### Timeout errors

Card took too long to respond. Try:
- Increasing timeout with `setTimeout()`
- Ensuring card is properly positioned
- Removing card and tapping again

## Migration from Internal API

If migrating from direct `TapCardReader` usage:

```kotlin
// OLD - Internal API
val reader = TapCardReader(activity, config)
reader.addListener(listener)
reader.startReading()

// NEW - Public API
val tapCard = TapCard(activity, config)
tapCard.startListening { result ->
    // Handle result
}
```

The public API handles:
- Listener lifecycle automatically
- Thread-safe callback delivery
- Proper cleanup on result
- Simplified result types

## License

Copyright (c) 2024 Hyperswitch. All rights reserved.
