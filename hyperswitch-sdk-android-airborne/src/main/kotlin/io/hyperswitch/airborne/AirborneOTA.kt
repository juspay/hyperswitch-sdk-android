package io.hyperswitch.airborne

import android.content.Context
import androidx.annotation.Keep
import `in`.juspay.airborne.HyperOTAServices
import `in`.juspay.airborne.LazyDownloadCallback
import `in`.juspay.airborne.ota.ApplicationManager

@Keep
class AirborneOTA {
    private var applicationManager: ApplicationManager? = null
    private lateinit var tracker: HyperOtaLogger

    fun initAirborneOTA(
        context: Context,
        sdkVersion: String,
        url: String,
        appId: String,
        bundleName: String
    ) {
        try {
            this.tracker = HyperOtaLogger(sdkVersion)
            val hyperOTAServices = HyperOTAServices(
                context = context,
                workSpacePath = appId,
                appVersion = sdkVersion,
                releaseConfigTemplateUrl = url,
                trackerCallback = tracker
            )
            val manager = hyperOTAServices.createApplicationManager()
            this.applicationManager = manager
            manager.loadApplication(
                unSanitizedClientId = appId,
                lazyDownloadCallback = object : LazyDownloadCallback {
                    override fun fileInstalled(filePath: String, success: Boolean) {}

                    override fun lazySplitsInstalled(success: Boolean) {}
                }
            )
        } catch (e: Exception) {
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
     * constructor for HyperOTA with default Hyperswitch configuration.
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
        if (baseUrl.isEmpty()) {
            throw Exception("BaseURL shouldn't be empty")
        }
        val url = if (baseUrl.endsWith(".json") || baseUrl.contains("/release/")) {
            baseUrl
        } else {
            "$baseUrl/mobile-ota/android/${sdkVersion}/config.json"
        }
        val appId = if (baseUrl.contains("/release/")) {
            baseUrl.trimEnd('/').substringAfterLast('/')
        } else {
            "hyperswitch"
        }
        this.initAirborneOTA(
            context,
            sdkVersion,
            url,
            appId,
            "hyperswitch.bundle"
        )
    }

    fun getBundlePath(): String {
        return try {
            val path = applicationManager?.getIndexBundlePath()
            if (!path.isNullOrEmpty() && !path.contains("ios")) {
                path
            } else {
                "assets://hyperswitch.bundle"
            }
        } catch (_: Exception) {
            "assets://hyperswitch.bundle"
        }
    }
}
