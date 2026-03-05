package io.hyperswitch.superposition

import android.util.Log
import io.hyperswitch.networking.HyperNetworking

object SuperpositionManager {

    private const val TAG = "SuperpositionManager"

    private var host: String? = null
    private var profileId: String? = null
    private var publishableKey: String? = null
    @Volatile private var cachedConfig: SuperpositionConfig? = null

    fun initialise(host: String, profileId: String, publishableKey: String) {
        this.host = host
        this.profileId = profileId
        this.publishableKey = publishableKey
        cachedConfig = null
    }

    fun fetchConfig() {
        val h = host ?: return
        val pid = profileId ?: return
        val pk = publishableKey ?: return

        val url = "$h/v1/sdk/configs/$pid/web/sandbox.json"
        HyperNetworking.makeGetRequest(url, mapOf("Content-Type" to "application/json", "api-key" to pk)) { result ->
            result.onSuccess { json ->
                if (json.isNotBlank()) {
                    cachedConfig = SuperpositionConfig(configJson = json)
                    Log.d(TAG, "Config fetched successfully")
                }
            }
            result.onFailure { e -> Log.e(TAG, "Config fetch failed: ${e.message}") }
        }
    }

    fun getCachedConfig(): SuperpositionConfig? = cachedConfig
}
