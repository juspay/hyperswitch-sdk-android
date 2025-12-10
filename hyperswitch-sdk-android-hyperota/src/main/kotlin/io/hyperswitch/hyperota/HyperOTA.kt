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

    constructor(context: Context,
                sdkVersion: String,
                actualURL : String,
                appId: String,
                bundleName: String
        ){
        this.initHyperOTA(context,sdkVersion,actualURL,appId,bundleName)

    }
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