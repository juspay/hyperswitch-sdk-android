package io.hyperswitch.superposition

import android.util.Log
import io.hyperswitch.networking.HyperNetworking

object SuperpositionManager {

    private const val TAG = "SuperpositionManager"

    private var configUrl: String? = null
    @Volatile private var cachedConfig: SuperpositionConfig? = null

    fun initialise(configUrl: String) {
        this.configUrl = configUrl
        cachedConfig = null
    }

    fun fetchConfig() {
        val url = configUrl ?: return
        HyperNetworking.makeGetRequest(url, mapOf("profile_id" to "test-id")) { result ->
            result.onSuccess { json ->
                if (json.isNotBlank() && json != "success") {
                    cachedConfig = SuperpositionConfig(configJson = json)
                    Log.d(TAG, "Config fetched successfully")
                }
            }
            result.onFailure { e -> Log.e(TAG, "Config fetch failed: ${e.message}") }
        }
    }

    fun getCachedConfig(): SuperpositionConfig? = cachedConfig
}
