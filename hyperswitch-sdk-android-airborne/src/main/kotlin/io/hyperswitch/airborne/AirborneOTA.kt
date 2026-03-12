package io.hyperswitch.airborne

import android.content.Context
import androidx.annotation.Keep
import `in`.juspay.hyperotareact.HyperOTAServicesReact

@Keep
class AirborneOTA {
    private lateinit var hyperOTAServicesReact: HyperOTAServicesReact
    private lateinit var tracker: AirborneLogger

    private fun initAirborneOTA(
        context: Context,
        sdkVersion: String,
        url: String,
        appId: String,
        bundleName: String
    ) {
        try {
            this.tracker = AirborneLogger(sdkVersion)
            hyperOTAServicesReact = HyperOTAServicesReact(
                context,
                appId,
                bundleName,
                sdkVersion,
                url,
                tracker
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Constructor for AirborneOTA with full configuration.
     *
     * @param context The Android application context
     * @param sdkVersion The version of the SDK being used
     * @param configUrl The URL to fetch the OTA configuration
     * @param appId The application identifier for OTA updates
     * @param bundleName The name of the bundle to be downloaded and installed
     */
    constructor(
        context: Context,
        sdkVersion: String,
        configUrl: String,
        appId: String,
        bundleName: String
    ) {
        this.initAirborneOTA(context, sdkVersion, configUrl, appId, bundleName)
    }

    /**
     * Constructor for AirborneOTA with default Hyperswitch configuration.
     * Automatically constructs the config URL, app ID, and bundle name.
     *
     * @param context The Android application context
     * @param sdkVersion The version of the SDK being used
     * @param baseUrl The base URL for the OTA service (config URL will be constructed as: baseUrl/mobile-ota/android/sdkVersion/config.json)
     */
    constructor(
        context: Context,
        sdkVersion: String,
        baseUrl: String
    ) {
        if (baseUrl == "") {
            throw Exception("BaseURL shouldn't be empty")
        }
        this.initAirborneOTA(
            context,
            sdkVersion,
            "$baseUrl/mobile-ota/android/${sdkVersion}/config.json",
            "hyperswitch",
            "hyperswitch.bundle"
        )
    }

    fun getBundlePath(): String {
        return try {
            hyperOTAServicesReact.getBundlePath().takeUnless { it.contains("ios") }
                ?: "assets://hyperswitch.bundle"
        } catch (_: Exception) {
            "assets://hyperswitch.bundle"
        }
    }
}
