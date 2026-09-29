package io.hyperswitch.airborne

import android.content.Context
import androidx.annotation.Keep
import `in`.juspay.airborne.HyperOTAServices
import `in`.juspay.airborne.ota.ApplicationManager
@Keep
class AirborneOTA {
    private var applicationManager : ApplicationManager? = null
    private var bundleName = "hyperswitch.bundle"
    private lateinit var tracker : HyperOtaLogger

    fun initAirborneOTA(context: Context,
                     sdkVersion: String,
                     url : String,
                     appId: String,
                     bundleName: String){
        this.bundleName = bundleName
        try {
            this.tracker = HyperOtaLogger(sdkVersion)
            applicationManager = HyperOTAServices(context, appId, sdkVersion, url, tracker)
                .createApplicationManager()
                .apply { loadApplication(appId) }
        }catch (e: Exception){
            e.printStackTrace()
        }
    }

    /**
     * constructor for HyperOTA with full configuration.
     *
     * @param context The Android application context
     * @param sdkVersion The version of the SDK being used
     * @param configUrl The URL to fetch the OTA configuration
     * @param appId The application identifier for OTA updates
     * @param bundleName The name of the bundle to be downloaded and installed
     */
    constructor(context: Context,
                sdkVersion: String,
                configUrl : String,
                appId: String,
                bundleName: String
        ){
        this.initAirborneOTA(context,sdkVersion,configUrl,appId,bundleName)
    }

    /**
     * constructor for HyperOTA with default Hyperswitch configuration.
     * Automatically constructs the config URL, app ID, and bundle name.
     *
     * @param context The Android application context
     * @param sdkVersion The version of the SDK being used
     * @param baseUrl The base URL for the OTA service (config URL will be constructed as: baseUrl/mobile-ota/android/sdkVersion/config.json)
     */
    constructor(context: Context,
                sdkVersion: String,
                baseUrl : String){
        if(baseUrl == "") {
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
            // Empty until an OTA package has been downloaded.
            applicationManager?.getIndexBundlePath()?.takeIf { it.isNotEmpty() }
                ?: "assets://$bundleName"
        } catch (_: Exception) {
            "assets://$bundleName"
        }

    }
}