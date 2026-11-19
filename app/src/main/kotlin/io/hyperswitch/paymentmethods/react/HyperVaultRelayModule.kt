package io.hyperswitch.paymentmethods.react

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import io.hyperswitch.paymentmethods.CardWidget
import io.hyperswitch.react.SurfaceOwners
import io.hyperswitch.react.codegen.NativeHyperVaultRelaySpec
import org.json.JSONObject

/**
 * HyperVaultRelay (`src/specs/NativeHyperVaultRelay.ts` in hyperswitch-client-core), on the
 * payments host: a CardWidget's line to the card form it pays with, whose hosted form runs on
 * the Payment Methods host. The widget is found by its root tag, like every payments surface;
 * what passes through (the vault's details down, the token reply up) is not read here.
 */
internal class HyperVaultRelayModule(
    private val rct: ReactApplicationContext,
) : NativeHyperVaultRelaySpec(rct) {

    override fun openVaultForm(rootTag: Double, descriptorJson: String) =
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            (owner as? CardWidget)?.openVaultForm(descriptorJson)
        }

    override fun tokenizeVaultForm(rootTag: Double, formId: String, promise: Promise) =
        SurfaceOwners.resolve(rct, rootTag.toInt()) { owner ->
            val widget = owner as? CardWidget
            if (widget == null) {
                promise.resolve(JSONObject(mapOf("ok" to false, "message" to "No card form is open for this widget.")).toString())
            } else {
                widget.tokenizeVaultForm(formId) { reply -> promise.resolve(reply) }
            }
        }
}
