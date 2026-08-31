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
     * The vault sdk_authorization is NOT the payments one: it is minted by a
     * payment-method-session. mockServer.js (client-core) mints one at
     * POST /v1/payment-method-sessions and serves it here.
     */
    private fun fetchVaultAuthorization() {
        reset().get("$SERVER_URL/create-payment-method-session")
            .responseString(object : Handler<String?> {
                override fun success(value: String?) {
                    try {
                        val json = value?.let { JSONObject(it) } ?: return
                        val sdkAuthorization = json.getString("sdkAuthorization")
                        runOnUiThread { onAuthorizationReady(sdkAuthorization) }
                    } catch (e: JSONException) {
                        Log.e(TAG, "Failed to parse vault session response", e)
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
        val collect = HyperswitchCollect(this, sdkAuthorization, Environment.SANDBOX)
        this.collect = collect

        vaultFields.forEach(collect::bindView)
        collect.setOnFieldStateChangeListener { state ->
            Log.i(TAG, "[VAULT-TEST] didChange: $state")
        }

        tokeniseButton.isEnabled = true
        setStatus("Vault session ready")
        Log.i(TAG, "[VAULT-TEST] collect initialised with backend sdk authorization")
    }

    private fun addField(field: BaseVaultFieldView, name: String, container: LinearLayout) {
        field.fieldName = name
        field.placeholder = name
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
            "${it.fieldName}: content='${it.content}' empty=${it.isEmpty} valid=${it.isValid}"
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
