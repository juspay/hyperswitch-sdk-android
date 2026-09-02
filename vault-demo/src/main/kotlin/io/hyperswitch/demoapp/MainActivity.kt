package io.hyperswitch.vault.demo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Handler
import io.hyperswitch.vault.core.Environment
import io.hyperswitch.vault.core.FieldState
import io.hyperswitch.vault.core.HyperswitchVault
import io.hyperswitch.vault.widget.BaseVaultFieldView
import io.hyperswitch.vault.widget.HyperswitchCardNumberEditText
import io.hyperswitch.vault.widget.HyperswitchCardVerificationCodeEditText
import io.hyperswitch.vault.widget.HyperswitchExpirationDateEditText
import org.json.JSONException
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Vault
    // -------------------------------------------------------------------------

    private var collect: HyperswitchVault? = null

    private val vaultFields = mutableListOf<BaseVaultFieldView>()

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private lateinit var cardNumberField: HyperswitchCardNumberEditText
    private lateinit var expiryField: HyperswitchExpirationDateEditText
    private lateinit var cvcField: HyperswitchCardVerificationCodeEditText

    private lateinit var tokeniseButton: Button
    private lateinit var reloadButton: Button

    private lateinit var statusView: TextView

    private lateinit var cardStatusView: TextView
    private lateinit var expiryStatusView: TextView
    private lateinit var cvcStatusView: TextView

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupViews()
        setupListeners()
        setInitialUiState()

        Log.i(
            TAG,
            "[VAULT-TEST] onCreate: fetching vault authorization"
        )

        fetchVaultAuthorization()
    }

    override fun onDestroy() {
        destroyVaultSession()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // UI Setup
    // -------------------------------------------------------------------------

    private fun setupViews() {

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(16),
                dp(32),
                dp(16),
                dp(24)
            )
        }

        // ---------------------------------------------------------------------
        // Title
        // ---------------------------------------------------------------------

        val titleView = TextView(this).apply {
            text = "Hyperswitch Vault"
            textSize = 24f
        }

        container.addView(titleView)

        // ---------------------------------------------------------------------
        // Description
        // ---------------------------------------------------------------------

        val descriptionView = TextView(this).apply {
            text = "Enter your card details below"
            textSize = 14f

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        }

        container.addView(descriptionView)

        // ---------------------------------------------------------------------
        // Card Number
        // ---------------------------------------------------------------------

        cardNumberField = HyperswitchCardNumberEditText(this)

        addField(
            field = cardNumberField,
            name = "Card Number",
            container = container
        )

        // ---------------------------------------------------------------------
        // Expiry
        // ---------------------------------------------------------------------

        expiryField = HyperswitchExpirationDateEditText(this)

        addField(
            field = expiryField,
            name = "Expiry",
            container = container
        )

        // ---------------------------------------------------------------------
        // CVC
        // ---------------------------------------------------------------------

        cvcField = HyperswitchCardVerificationCodeEditText(this)

        addField(
            field = cvcField,
            name = "CVC",
            container = container
        )

        // ---------------------------------------------------------------------
        // Field Status
        // ---------------------------------------------------------------------

        container.addView(
            createSectionTitle("Field Status")
        )

        cardStatusView = createStatusView(
            "Card Number: Waiting..."
        )

        expiryStatusView = createStatusView(
            "Expiry: Waiting..."
        )

        cvcStatusView = createStatusView(
            "CVC: Waiting..."
        )

        container.addView(cardStatusView)
        container.addView(expiryStatusView)
        container.addView(cvcStatusView)

        // ---------------------------------------------------------------------
        // Vault Status
        // ---------------------------------------------------------------------

        container.addView(
            createSectionTitle("Vault Status")
        )

        statusView = TextView(this).apply {
            text = "Initializing..."
            textSize = 14f

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        }

        container.addView(statusView)

        // ---------------------------------------------------------------------
        // Tokenise Button
        // ---------------------------------------------------------------------

        tokeniseButton = Button(this).apply {
            text = "Tokenise"
            isEnabled = false
        }

        container.addView(
            tokeniseButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
            }
        )

        // ---------------------------------------------------------------------
        // Reload Button
        // ---------------------------------------------------------------------

        reloadButton = Button(this).apply {
            text = "Reload Session"
            isEnabled = true
        }

        container.addView(reloadButton)

        // ---------------------------------------------------------------------
        // Root
        // ---------------------------------------------------------------------

        setContentView(
            ScrollView(this).apply {
                addView(container)
            }
        )
    }

    private fun setupListeners() {

        tokeniseButton.setOnClickListener {
            tokenise()
        }

        reloadButton.setOnClickListener {
            reloadSession()
        }
    }

    // -------------------------------------------------------------------------
    // UI Helpers
    // -------------------------------------------------------------------------

    private fun createSectionTitle(
        text: String
    ): TextView {

        return TextView(this).apply {
            this.text = text
            textSize = 16f

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
                bottomMargin = dp(8)
            }
        }
    }

    private fun createStatusView(
        text: String
    ): TextView {

        return TextView(this).apply {
            this.text = text
            textSize = 14f

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        }
    }

    private fun addField(
        field: BaseVaultFieldView,
        name: String,
        container: LinearLayout
    ) {

        field.fieldName = name

        field.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56)
        ).apply {
            topMargin = dp(12)
        }

        vaultFields.add(field)

        container.addView(field)
    }

    private fun dp(value: Int): Int {
        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    // -------------------------------------------------------------------------
    // Initial UI State
    // -------------------------------------------------------------------------

    private fun setInitialUiState() {
        statusView.text = "Fetching vault session..."

        cardStatusView.text = "Card Number: Waiting..."
        expiryStatusView.text = "Expiry: Waiting..."
        cvcStatusView.text = "CVC: Waiting..."

        tokeniseButton.isEnabled = false
        reloadButton.isEnabled = true
    }

    // -------------------------------------------------------------------------
    // Vault Session
    // -------------------------------------------------------------------------

    private fun fetchVaultAuthorization() {

        setStatus("Fetching vault session...")

        tokeniseButton.isEnabled = false
        reloadButton.isEnabled = false

        Log.i(
            TAG,
            "[VAULT-TEST] Requesting $SERVER_URL/vault-session"
        )

        Fuel.get("$SERVER_URL/vault-session")
            .responseString(
                object : Handler<String?> {

                    override fun success(value: String?) {

                        try {

                            val json = value?.let {
                                JSONObject(it)
                            } ?: throw JSONException(
                                "Empty vault session response"
                            )

                            val sdkAuthorization = json
                                .getJSONObject("vault_details")
                                .getJSONObject("vault_data")
                                .getString("sdk_authorization")

                            Log.i(
                                TAG,
                                "[VAULT-TEST] Vault authorization received"
                            )

                            runOnUiThread {
                                onAuthorizationReady(
                                    sdkAuthorization
                                )
                            }

                        } catch (e: JSONException) {

                            Log.e(
                                TAG,
                                "[VAULT-TEST] Invalid vault session response",
                                e
                            )

                            runOnUiThread {

                                reloadButton.isEnabled = true

                                setStatus(
                                    "Invalid vault session response"
                                )
                            }
                        }
                    }

                    override fun failure(
                        error: FuelError
                    ) {

                        Log.e(
                            TAG,
                            "[VAULT-TEST] Vault session request failed: " +
                                    error.message,
                            error
                        )

                        runOnUiThread {

                            reloadButton.isEnabled = true

                            setStatus(
                                "Could not reach vault server"
                            )
                        }
                    }
                }
            )
    }

    // -------------------------------------------------------------------------
    // Authorization Ready
    // -------------------------------------------------------------------------

    private fun onAuthorizationReady(
        sdkAuthorization: String
    ) {

        Log.i(
            TAG,
            "[VAULT-TEST] Initializing HyperswitchVault"
        )

        // Remove the previous session.
        destroyVaultSession()

        val newCollect = HyperswitchVault(
            this,
            sdkAuthorization,
            Environment.INTEG
        )

        collect = newCollect

        setupVaultListeners(newCollect)

        // Bind all existing fields to the new session.
        vaultFields.forEach { field ->
            newCollect.bindView(field)
        }

        reloadButton.isEnabled = true

        setStatus(
            "Vault session ready"
        )

        // The fields are empty at this point.
        updateFieldStates()

        Log.i(
            TAG,
            "[VAULT-TEST] Vault session initialized successfully"
        )
    }

    // -------------------------------------------------------------------------
    // Vault Listeners
    // -------------------------------------------------------------------------

    private fun setupVaultListeners(
        collect: HyperswitchVault
    ) {

        collect.setOnFieldStateChangeListener { state ->

            Log.i(
                TAG,
                "[VAULT-TEST] Field state changed: $state"
            )

            runOnUiThread {
                updateFieldStates()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Field State
    // -------------------------------------------------------------------------

    private fun updateFieldStates() {
        val currentCollect = collect ?: return

        val states = currentCollect.getFieldStates()

        Log.i(
            TAG,
            "[VAULT-TEST] getFieldStates(): ${states.size}"
        )

        states.forEach { state ->
            Log.i(
                TAG,
                "[VAULT-TEST] FieldState -> " +
                        "fieldName=${state.fieldName}, " +
                        "empty=${state.isEmpty}, " +
                        "valid=${state.isValid}"
            )
        }

        // Match fields using their actual fieldName.
        states.forEach { state ->
            when {
                isCardNumberField(state.fieldName) -> {
                    cardStatusView.text = buildStatusMessage(
                        "Card Number",
                        state.isEmpty,
                        state.isValid
                    )
                }

                isExpiryField(state.fieldName) -> {
                    expiryStatusView.text = buildStatusMessage(
                        "Expiry",
                        state.isEmpty,
                        state.isValid
                    )
                }

                isCvcField(state.fieldName) -> {
                    cvcStatusView.text = buildStatusMessage(
                        "CVC",
                        state.isEmpty,
                        state.isValid
                    )
                }

                else -> {
                    Log.w(
                        TAG,
                        "[VAULT-TEST] Unknown field state: ${state.fieldName}"
                    )
                }
            }
        }

        tokeniseButton.isEnabled = areAllFieldsValid(states)
    }

    private fun normalizeFieldName(
        fieldName: String?
    ): String {
        return fieldName
            ?.trim()
            ?.lowercase()
            ?.replace(" ", "")
            ?.replace("_", "")
            ?.replace("-", "")
            ?: ""
    }

    private fun isCardNumberField(
        fieldName: String?
    ): Boolean {
        return when (normalizeFieldName(fieldName)) {
            "cardnumber",
            "cardnumberedittext" -> true

            else -> false
        }
    }

    private fun isExpiryField(
        fieldName: String?
    ): Boolean {
        return when (normalizeFieldName(fieldName)) {
            "expiry",
            "expiration",
            "expirationdate",
            "expirationdateedittext",
            "expirydate" -> true

            else -> false
        }
    }

    private fun isCvcField(
        fieldName: String?
    ): Boolean {
        return when (normalizeFieldName(fieldName)) {
            "cvc",
            "cvv",
            "verificationcode",
            "cardverificationcode",
            "cardverificationcodeedittext" -> true

            else -> false
        }
    }
    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private fun buildStatusMessage(
        fieldName: String,
        isEmpty: Boolean,
        isValid: Boolean
    ): String {
        return when {
            isEmpty ->
                "$fieldName: Not entered"

            !isValid ->
                "$fieldName: Invalid"

            else ->
                "$fieldName: Valid"
        }
    }

    private fun areAllFieldsValid(
        states: Collection<FieldState>
    ): Boolean {
        return states.isNotEmpty() &&
                states.all { state ->
                    !state.isEmpty && state.isValid
                }
    }
    // -------------------------------------------------------------------------
    // Tokenise
    // -------------------------------------------------------------------------

    private fun tokenise() {

        val currentCollect = collect

        if (currentCollect == null) {

            setStatus(
                "Vault session is not ready"
            )

            return
        }

        val states = currentCollect.getFieldStates()

        // Always validate immediately before tokenisation.
        if (!areAllFieldsValid(states)) {

            Log.i(
                TAG,
                "[VAULT-TEST] Tokenise blocked - invalid fields"
            )

            updateFieldStates()

            setStatus(
                "Please enter valid card details"
            )

            return
        }

        Log.i(
            TAG,
            "[VAULT-TEST] Starting tokenisation"
        )

        tokeniseButton.isEnabled = false
        reloadButton.isEnabled = false

        setStatus(
            "Tokenising..."
        )

        currentCollect.tokenise { result ->

            Log.i(
                TAG,
                "[VAULT-TEST] Tokenise result: $result"
            )

            runOnUiThread {

                tokeniseButton.isEnabled =
                    areAllFieldsValid(
                        currentCollect.getFieldStates()
                    )

                reloadButton.isEnabled = true

                setStatus(
                    "Tokenisation completed:\n$result"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Reload
    // -------------------------------------------------------------------------

    private fun reloadSession() {

        Log.i(
            TAG,
            "[VAULT-TEST] Reloading vault session"
        )

        tokeniseButton.isEnabled = false
        reloadButton.isEnabled = false

        setStatus(
            "Reloading vault session..."
        )

        resetFieldStatus()

        destroyVaultSession()

        fetchVaultAuthorization()
    }

    // -------------------------------------------------------------------------
    // Destroy Vault Session
    // -------------------------------------------------------------------------

    private fun destroyVaultSession() {

        collect?.let { previousCollect ->

            Log.i(
                TAG,
                "[VAULT-TEST] Unbinding vault fields"
            )

            vaultFields.forEach { field ->

                try {

                    previousCollect.unbindView(
                        field
                    )

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "[VAULT-TEST] " +
                                "Failed to unbind ${field.fieldName}",
                        e
                    )
                }
            }
        }

        collect = null
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    private fun setStatus(
        message: String
    ) {

        runOnUiThread {
            statusView.text = message
        }
    }

    private fun resetFieldStatus() {
        cardStatusView.text = "Card Number: Waiting..."
        expiryStatusView.text = "Expiry: Waiting..."
        cvcStatusView.text = "CVC: Waiting..."
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private companion object {

        const val TAG = "VaultDemo"

        /**
         * Android emulator -> host machine.
         *
         * For a physical device, replace this with the
         * IP address of the machine running mockServer.js.
         */
        const val SERVER_URL =
            "http://10.0.2.2:5252"
    }
}