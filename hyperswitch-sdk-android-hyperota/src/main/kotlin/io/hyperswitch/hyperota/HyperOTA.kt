package io.hyperswitch.hyperota

import android.content.Context
import `in`.juspay.hyperota.LazyDownloadCallback
import `in`.juspay.hyperotareact.HyperOTAReact

class HyperOTA {
    private lateinit var hyperOTAReact : HyperOTAReact
    private lateinit var tracker : HyperOtaLogger

    fun initHyperOTA(context: Context,
                     sdkVersion: String,
                     url : String,
                     appId: String,
                     bundleName: String){
        try {
            this.tracker = HyperOtaLogger(sdkVersion)
            HyperOTAReact(
                context,
                appId,
                bundleName,
                sdkVersion,
                url,
                mapOf(
                    "Content-Encoding" to "br, gzip"
                ),
                object : LazyDownloadCallback {
                    override fun fileInstalled(filePath: String, success: Boolean) {
                    }

                    override fun lazySplitsInstalled(success: Boolean) {
                    }
                },
                tracker,
            )
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
        this.initHyperOTA(context,sdkVersion,configUrl,appId,bundleName)
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
        this.initHyperOTA(
            context,
            sdkVersion,
            "$baseUrl/mobile-ota/android/${sdkVersion}/config.json",
            "hyperswitch",
            "hyperswitch.bundle"
        )
    }

    fun getBundlePath(): String {
        return try {
            hyperOTAReact.getBundlePath().takeUnless { it.contains("ios") }
                ?: "assets://hyperswitch.bundle"
        }catch (_ : Exception){
            "assets://hyperswitch.bundle"
        }
    }
}