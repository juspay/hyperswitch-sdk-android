package io.hyperswitch.pmm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.uimanager.PixelUtil
import io.hyperswitch.BuildConfig
import io.hyperswitch.model.HyperswitchBaseConfiguration
import io.hyperswitch.model.HyperswitchConfiguration
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsheet.PaymentResult
import io.hyperswitch.paymentsheet.PaymentSheet
import io.hyperswitch.pmm.react.PMMProtocol
import io.hyperswitch.pmm.react.PaymentMethodManagementRuntime
import io.hyperswitch.sdk.HyperswitchInstance

/** [sdkAuthorization] comes from your server's payment method session. */
data class PaymentMethodManagementConfiguration(val sdkAuthorization: String)

/**
 * A payment method management session: the place management sheets and widgets come
 * from. Both are roots on the PMM React host — a realm apart from payments, with its
 * own bundle and native module.
 */
class PaymentMethodManagement internal constructor(
    private val activity: Activity,
    private val hyperswitchConfiguration: HyperswitchBaseConfiguration,
    private val sessionConfiguration: PaymentMethodManagementConfiguration,
) {

    private val sessionConfig = PaymentSessionConfiguration(sessionConfiguration.sdkAuthorization)

    init {
        /* Starts the PMM engine loading, so the first surface does not wait for it. */
        PaymentMethodManagementRuntime.get(activity.application).apply {
            follow(activity)
            warmUp()
        }
    }

    /**
     * Presents the management sheet over [activity]. [onResult] fires once, when the
     * sheet exits; a merchant-driven close reports [PaymentResult.Canceled].
     */
    fun presentSheet(
        configuration: PaymentSheet.Configuration? = null,
        onResult: (PaymentResult) -> Unit,
    ) {
        val health = PaymentMethodManagementRuntime.get(activity.application).health
        if (health.initFailure != null) {
            // Nothing to show: the sheet would never render.
            val result = PaymentResult.Failed(health.resultError())
            Handler(Looper.getMainLooper()).post { onResult(result) }
            return
        }
        PaymentMethodManagementResultBus.setCallback(onResult)
        activity.startActivity(
            Intent(activity, PaymentMethodManagementActivity::class.java)
                .putExtra(
                    PaymentMethodManagementActivity.EXTRA_PROPS,
                    props(configuration, PMMProtocol.SHEET_TYPE, from = null),
                )
        )
    }

    /**
     * An embeddable management view. Drive its save with
     * [PaymentMethodManagementWidget.tokenize] (web parity with
     * `hyper.confirmTokenization`) from any merchant-owned button; the view stays
     * mounted for retry until a result resolves.
     */
    fun createWidget(configuration: PaymentSheet.Configuration? = null): PaymentMethodManagementWidget =
        PaymentMethodManagementWidget(activity, props(configuration, PMMProtocol.WIDGET_TYPE, from = "nativeWidget"))

    /**
     * The props envelope every PMM root starts with — same shape payments surfaces
     * use, over the PMM types the PMM bundle routes on.
     */
    private fun props(configuration: PaymentSheet.Configuration?, type: String, from: String?): Bundle {
        val bundle = LaunchOptions(activity, BuildConfig.VERSION_NAME, hyperswitchConfiguration)
            .getBundle(configuration?.bundle, type, from, sessionConfig)
        applyFonts(configuration, bundle)
        return bottomInsetToDIPFromPixel(bundle)
    }

    private fun applyFonts(configuration: PaymentSheet.Configuration?, bundle: Bundle) {
        configuration?.appearance?.typography?.fontResId?.let {
            ReactFontManager.getInstance().addCustomFont(
                activity,
                activity.resources.getResourceName(it).toString().split("/")[1],
                it
            )
            bundle.getBundle("props")?.getBundle("configuration")?.getBundle("appearance")
                ?.getBundle("font")?.let { font ->
                    font.remove("fontResId")
                    font.putString(
                        "family",
                        activity.resources.getResourceName(it).toString().split("/")[1]
                    )
                }
        }

        configuration?.appearance?.primaryButton?.typography?.fontResId?.let {
            ReactFontManager.getInstance().addCustomFont(
                activity,
                activity.resources.getResourceName(it).toString().split("/")[1],
                it
            )
            bundle.getBundle("props")?.getBundle("configuration")?.getBundle("appearance")
                ?.getBundle("primaryButton")?.getBundle("typography")?.let { typography ->
                    typography.remove("fontResId")
                    typography.putString(
                        "family",
                        activity.resources.getResourceName(it).toString().split("/")[1]
                    )
                }
        }
    }

    private fun bottomInsetToDIPFromPixel(bundle: Bundle): Bundle {
        val propsBundle = bundle.getBundle("props")
        val sdkParamsBundle = propsBundle?.getBundle("sdkParams")
        sdkParamsBundle?.getFloat("topInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("topInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        sdkParamsBundle?.getFloat("leftInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("leftInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        sdkParamsBundle?.getFloat("rightInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("rightInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        sdkParamsBundle?.getFloat("bottomInset")?.let { dipValue ->
            sdkParamsBundle.putFloat("bottomInset", PixelUtil.toDIPFromPixel(dipValue))
        }
        return bundle
    }
}

/**
 * The completion of the currently presented sheet. An Intent cannot carry the callback,
 * so [PaymentMethodManagement.presentSheet] leaves it here, and
 * [PaymentMethodManagementActivity] resolves it once — on JS exit, back press or the
 * OS tearing the sheet down.
 */
internal object PaymentMethodManagementResultBus {
    private val lock = Any()
    private var pending: ((PaymentResult) -> Unit)? = null

    fun setCallback(callback: (PaymentResult) -> Unit) = synchronized(lock) { pending = callback }

    fun deliver(result: PaymentResult) {
        val callback = synchronized(lock) { pending.also { pending = null } } ?: return
        Handler(Looper.getMainLooper()).post { callback(result) }
    }
}

/** An instance made without a configuration has no key; the surfaces then say so themselves. */
fun HyperswitchInstance.initPaymentMethodManagement(
    configuration: PaymentMethodManagementConfiguration,
): PaymentMethodManagement =
    PaymentMethodManagement(activity, hsConfig ?: HyperswitchConfiguration(), configuration)
