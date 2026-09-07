package io.hyperswitch.lite

import android.app.Activity
import androidx.core.view.WindowCompat
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.PresentationInterface
import io.hyperswitch.model.PaymentSessionConfiguration
import io.hyperswitch.paymentsheet.PaymentSheet

/**
 * A utility class for managing a WebViewFragment for presenting payment sheets.
 *
 * This class handles the initialization, presentation, and interaction with the WebViewFragment
 * used for displaying payment sheets.
 */
class WebViewUtils(
    private val activity: Activity,
) : PresentationInterface {
    /**
     * The WebViewFragment used for displaying payment sheets.
     *
     * This fragment is lazily initialized and added to the activity's content view.
     */
    private val webFragment: WebViewFragment =
        (activity.fragmentManager.findFragmentByTag("webFragment") as? WebViewFragment)
            ?: WebViewFragment().also {
                activity.fragmentManager
                    .beginTransaction()
                    .add(android.R.id.content, it, "webFragment")
                    .detach(it)
                    .commit()
            }

    /**
     * The LaunchOptions used for configuring the payment sheet.
     */
    private val launchOptions = LaunchOptions(activity, BuildConfig.VERSION_NAME)

    /**
     * Presents a payment sheet with the given payment intent client secret and configuration.
     *
     * @param paymentIntentClientSecret The client secret of the payment intent.
     * @param configuration The configuration for the payment sheet.
     */
    override fun presentSheet(
        sessionConfig: PaymentSessionConfiguration?,
        configuration: PaymentSheet.Configuration?,
    ): Boolean {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val transaction = activity.fragmentManager.beginTransaction()
        transaction.attach(webFragment).commit()
        webFragment.setRequestBody(
            launchOptions
                .getJson(
                    sessionConfig,
                    configuration,
                ).toString(),
        )
        return true
    }

    /**
     * Presents a payment sheet with the given configuration map.
     *
     * @param configurationMap The configuration map forthe payment sheet.
     */
    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean {
        webFragment.setRequestBody(launchOptions.getJson(configurationMap).toString())
        val transaction = activity.fragmentManager.beginTransaction()
        transaction.attach(webFragment).commit()
        return true
    }
}
