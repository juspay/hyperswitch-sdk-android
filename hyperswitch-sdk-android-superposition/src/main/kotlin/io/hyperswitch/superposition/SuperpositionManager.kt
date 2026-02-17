package io.hyperswitch.superposition

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.hyperswitch.networking.HyperNetworking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object SuperpositionManager {

    enum class ConfigSource { NONE, SHARED_PREFS, NETWORK }

    private const val TAG = "SuperpositionManager"
    private const val PREFS_NAME = "hyperswitch_superposition"
    private const val KEY_CONFIG_JSON = "config_json"

    @Volatile
    private var configUrl: String? = null
    @Volatile
    private var cachedConfig: SuperpositionConfig? = null
    @Volatile
    private var configSource: ConfigSource = ConfigSource.NONE

    private var prefs: SharedPreferences? = null

    private val generation = AtomicInteger(0)
    private val fetchInProgress = AtomicBoolean(false)
    private val inFlightGeneration = AtomicInteger(-1)

    fun initialise(context: Context, configUrl: String) {
        this.configUrl = configUrl
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load from SharedPreferences immediately (available before network fetch completes)
        val savedJson = prefs?.getString(KEY_CONFIG_JSON, null)

        cachedConfig = if (!savedJson.isNullOrBlank()) {
            configSource = ConfigSource.SHARED_PREFS
            Log.d(TAG, "Loaded config from SharedPreferences: $savedJson")
            SuperpositionConfig(configJson = savedJson)
        } else {
            configSource = ConfigSource.NONE
            null
        }

        generation.incrementAndGet()
        fetchInProgress.set(false)
        inFlightGeneration.set(-1)
        Log.d(TAG, "Initialized with configUrl=$configUrl (generation=${generation.get()})")
    }

    fun fetchConfig() {
        val url = configUrl
        if (url == null) {
            Log.w(TAG, "Cannot fetch config: Manager not initialized")
            return
        }

        val currentGeneration = generation.get()
        if (!fetchInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Fetch already in progress, skipping duplicate request")
            return
        }
        inFlightGeneration.set(currentGeneration)

        HyperNetworking.makeGetRequest(
            urlString = url,
            headers = mapOf("profile_id" to "test-id")
        ) { result ->
            if (generation.get() == currentGeneration) {
                result.onSuccess { json ->
                    if (json.isBlank()) {
                        Log.e(TAG, "Config fetch returned empty or invalid body")
                    } else {
                        cachedConfig = SuperpositionConfig(configJson = json)
                        configSource = ConfigSource.NETWORK
                        // Persist to SharedPreferences for next app launch
                        prefs?.edit()?.putString(KEY_CONFIG_JSON, json)?.apply()
                        Log.d(TAG, "Config fetched and persisted (generation=$currentGeneration)")
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Config fetch failed: ${e.message}")
                }
            } else {
                Log.w(
                    TAG,
                    "Ignoring stale response (generation $currentGeneration, current ${generation.get()})"
                )
            }
            if (inFlightGeneration.compareAndSet(currentGeneration, -1)) {
                fetchInProgress.set(false)
            }
        }
    }

    fun getCachedConfig(): SuperpositionConfig? = cachedConfig

    fun getConfigSource(): ConfigSource = configSource

    fun isInitialized(): Boolean = configUrl != null

    fun clearCache() {
        cachedConfig = null
        configSource = ConfigSource.NONE
        prefs?.edit()?.remove(KEY_CONFIG_JSON)?.apply()
    }
}
