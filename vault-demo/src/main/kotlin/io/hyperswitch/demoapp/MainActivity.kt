package io.hyperswitch.vault.demo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.hyperswitch.vault.core.Environment
import io.hyperswitch.vault.core.HyperswitchCollect
import io.hyperswitch.vault.widget.BaseVaultFieldView
import io.hyperswitch.vault.widget.HyperswitchCardNumberEditText
import io.hyperswitch.vault.widget.HyperswitchCardVerificationCodeEditText
import io.hyperswitch.vault.widget.HyperswitchExpirationDateEditText

class MainActivity : AppCompatActivity() {

    private lateinit var collect: HyperswitchCollect
    private lateinit var statesView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        collect = HyperswitchCollect(this, "sdk_auth_demo", Environment.SANDBOX)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(16))
        }

        addField(HyperswitchCardNumberEditText(this), "card_number", container)
        addField(HyperswitchExpirationDateEditText(this), "exp_date", container)
        addField(HyperswitchCardVerificationCodeEditText(this), "cvc", container)

        val button = Button(this).apply {
            text = "Tokenise"
            setOnClickListener { collect.tokenise() }
        }
        container.addView(button)

        statesView = TextView(this).apply { textSize = 12f }
        container.addView(statesView)

        setContentView(ScrollView(this).apply { addView(container) })

        collect.setOnFieldStateChangeListener { state ->
            Log.i(TAG, "[VAULT-TEST] didChange: $state")
        }

        Log.i(TAG, "[VAULT-TEST] onCreate done, fields added")
    }

    private fun addField(field: BaseVaultFieldView, name: String, container: LinearLayout) {
        field.fieldName = name
        field.placeholder = name
        field.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(12) }
        collect.bindView(field)
        container.addView(field)
    }

    private fun logStates() {
        val states = collect.getFieldStates()
        Log.i(TAG, "[VAULT-TEST] getFieldStates() -> ${states.size} states")
        states.forEach { Log.i(TAG, "[VAULT-TEST] state: $it") }
        statesView.text = states.joinToString("\n\n") {
            "${it.fieldName}: content='${it.content}' empty=${it.isEmpty} valid=${it.isValid}"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "VaultDemo"
    }
}
