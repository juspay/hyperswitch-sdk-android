package io.hyperswitch.airborne

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import `in`.juspay.airborne.HyperOTAServices
import `in`.juspay.airborne.LazyDownloadCallback
import `in`.juspay.airborne.ota.ApplicationManager
import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

@Keep
class AirborneOTA {
    private var applicationManager: ApplicationManager? = null
    private lateinit var tracker: HyperOtaLogger

    companion object {
        private const val TAG = "AirborneOTA"

        // Embedded EC P-256 public key (X.509 Base64) for asymmetric cryptographic signature verification
        private const val EMBEDDED_PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEH/ef22HPK/pxZtyLCgrex0UkB3qdc+kUlZxELErdsjsJxzlPMEozsdmLqGbi1mqVPMymO/N9CkER9yV5Z4Qi0Q=="

        val publicKey: PublicKey? by lazy {
            try {
                val keyBytes = Base64.decode(EMBEDDED_PUBLIC_KEY, Base64.DEFAULT)
                val keySpec = X509EncodedKeySpec(keyBytes)
                KeyFactory.getInstance("EC").generatePublic(keySpec)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode embedded public key", e)
                null
            }
        }

        fun verifyBundleSignature(bundleFile: File): Boolean {
            val key = publicKey ?: return false
            val sigFile = File("${bundleFile.absolutePath}.sig")
            if (!sigFile.exists() || sigFile.length() == 0L) {
                Log.w(TAG, "Signature file not found for bundle: ${bundleFile.absolutePath}")
                return false
            }
            return try {
                val sigBytes = sigFile.readBytes()
                val verifier = Signature.getInstance("SHA256withECDSA")
                verifier.initVerify(key)
                bundleFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        verifier.update(buffer, 0, bytesRead)
                    }
                }
                val valid = verifier.verify(sigBytes)
                if (valid) {
                    Log.i(TAG, "OTA bundle signature verified successfully.")
                } else {
                    Log.e(TAG, "OTA bundle signature verification FAILED!")
                }
                valid
            } catch (e: Exception) {
                Log.e(TAG, "Error during bundle signature verification", e)
                false
            }
        }
    }

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
                val bundleFile = File(path)
                val sigFile = File("${path}.sig")
                if (sigFile.exists()) {
                    if (verifyBundleSignature(bundleFile)) {
                        path
                    } else {
                        Log.e(TAG, "Bundle signature invalid! Reverting to bundled assets.")
                        "assets://hyperswitch.bundle"
                    }
                } else {
                    path
                }
            } else {
                "assets://hyperswitch.bundle"
            }
        } catch (_: Exception) {
            "assets://hyperswitch.bundle"
        }
    }
}
