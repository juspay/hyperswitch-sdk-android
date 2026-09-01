package io.hyperswitch.vault.demo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel.reset
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.vault.core.Environment
import io.hyperswitch.vault.core.HyperswitchCollect
import io.hyperswitch.vault.widget.BaseVaultFieldView
import io.hyperswitch.vault.widget.HyperswitchCardNumberEditText
import io.hyperswitch.vault.widget.HyperswitchCardVerificationCodeEditText
import io.hyperswitch.vault.widget.HyperswitchExpirationDateEditText
import io.hyperswitch.vault.core.*
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var collect: HyperswitchCollect? = null
    private val vaultFields = mutableListOf<BaseVaultFieldView>()
    private lateinit var tokeniseButton: Button
    private lateinit var reloadButton: Button
    private lateinit var statesView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(16))
        }

        addField(HyperswitchCardNumberEditText(this), "Card Number", container)
        addField(HyperswitchExpirationDateEditText(this), "MM/YY", container)
        addField(HyperswitchCardVerificationCodeEditText(this), "CVC", container)

        tokeniseButton = Button(this).apply {
            text = "Tokenise"
            isEnabled = false
            setOnClickListener {
                collect?.tokenise() { result ->
                        Log.i("Manideep", result.toString())
                }
            }
        }
        container.addView(tokeniseButton)

        reloadButton = Button(this).apply {
            text = "Reload Session"
            isEnabled = false
            setOnClickListener { reloadSession() }
        }
        container.addView(reloadButton)

        statesView = TextView(this).apply {
            textSize = 12f
            text = "Fetching vault session…"
        }
        container.addView(statesView)

        setContentView(ScrollView(this).apply { addView(container) })

        Log.i(TAG, "[VAULT-TEST] onCreate done, fields added; fetching vault sdk authorization")

        fetchVaultAuthorization()
    }

    /**
     * The vault sdk_authorization is NOT the payments one: it is carried inside
     * the session response (`vault_details.vault_data.sdk_authorization`),
     * minted by the two-call flow of react-native-hyperswitch-vault's example
     * merchant-server (POST /payments -> POST /payments/session_tokens).
     * mockServer.js (client-core) serves exactly that shape at `/vault-session`.
     * Everything else in the session body is SDK-facing only; the demo never
     * decodes or logs it.
     */
    private fun fetchVaultAuthorization() {
        reset().get("$SERVER_URL/vault-session")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = value?.let { JSONObject(it) } ?: return
                        val sdkAuthorization = json
                            .getJSONObject("vault_details")
                            .getJSONObject("vault_data")
                            .getString("sdk_authorization")
                        runOnUiThread { onAuthorizationReady(sdkAuthorization) }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Vault session missing vault_details.vault_data.sdk_authorization", e)
                        setStatus("Bad vault session response")
                    }
                }

                override fun failure(error: FuelError) {
                    Log.e(TAG, "Vault session request failed: ${error.message}")
                    setStatus("Could not reach mock server at $SERVER_URL")
                }
            })
    }

    private fun onAuthorizationReady(sdkAuthorization: String) {
        // Drop the previous session: unbind every field so the next
        // bindView re-emits initialProps with the fresh sdk_authorization.
        collect?.let { previous ->
            vaultFields.forEach(previous::unbindView)
        }

        // Environment must match the host mockServer.js minted the session
        // against: INTEG -> integ.hyperswitch.io servers, JS vault host
        // dev.hyperswitch.io/api (the "#integration" environment).
        val collect = HyperswitchCollect(this, sdkAuthorization, Environment.INTEG)
        this.collect = collect

        vaultFields.forEach(collect::bindView)
        collect.setOnFieldStateChangeListener { state ->
            Log.i(TAG, "[VAULT-TEST] didChange: $state")
        }

        tokeniseButton.isEnabled = true
        reloadButton.isEnabled = true
        setStatus("Vault session ready")
        Log.i(TAG, "[VAULT-TEST] collect initialised with backend sdk authorization")
    }

    private fun reloadSession() {
        setStatus("Reloading vault session…")
        tokeniseButton.isEnabled = false
        fetchVaultAuthorization()
    }

    private fun addField(field: BaseVaultFieldView, name: String, container: LinearLayout) {
        field.fieldName = name
        field.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(12) }
        vaultFields.add(field)
        container.addView(field)
    }

    private fun logStates() {
        val states = collect?.getFieldStates() ?: return
        Log.i(TAG, "[VAULT-TEST] getFieldStates() -> ${states.size} states")
        states.forEach { Log.i(TAG, "[VAULT-TEST] state: $it") }
        statesView.text = states.joinToString("\n\n") {
            "${it.fieldName} empty=${it.isEmpty} valid=${it.isValid}"
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread { statesView.text = message }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "VaultDemo"

        /** Emulator reaches the host machine's mockServer.js here (iOS simulator uses localhost). */
        const val SERVER_URL = "http://10.0.2.2:5252"
    }
}
