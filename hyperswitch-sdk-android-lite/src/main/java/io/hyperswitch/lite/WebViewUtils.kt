package io.hyperswitch.lite

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import io.hyperswitch.paymentsession.LaunchOptions
import io.hyperswitch.paymentsession.SDKInterface
import io.hyperswitch.paymentsheet.PaymentSheet

/**
 * A utility class for managing a WebViewFragment for presenting payment sheets.
 *
 * This class handles the initialization, presentation, and interaction with the WebViewFragment
 * used for displaying payment sheets.
 */


class WebViewUtils(private val activity: Activity) : SDKInterface {

    private val launchOptions = LaunchOptions(activity)
    private val TAG = "webFragment"

    private var webFragment: WebViewFragment? = null

    init {
        when (activity) {
            is FragmentActivity -> {
                val manager = activity.supportFragmentManager
                webFragment = manager.findFragmentByTag(TAG) as? WebViewFragment
                    ?: WebViewFragment().also {
                        manager.beginTransaction()
                            .add(android.R.id.content, it, TAG)
                            .detach(it)
                            .commit()
                    }
            }
            else -> {
                WebViewHostActivityManager.preload(activity)
            }
        }
    }

    override fun initializeReactNativeInstance() {}
    override fun recreateReactContext() {}

    override fun presentSheet(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration?
    ): Boolean {
        val requestBody = launchOptions.getJson(paymentIntentClientSecret, configuration).toString()

        return if (activity is FragmentActivity) {
            val manager = activity.supportFragmentManager
            val fragment = webFragment ?: WebViewFragment().also {
                manager.beginTransaction().add(android.R.id.content, it, TAG).commit()
            }

            fragment.setRequestBody(requestBody)

            manager.beginTransaction()
                .attach(fragment)
                .addToBackStack(TAG)
                .commit()

            true
        } else {
            WebViewHostActivityManager.launch(activity, requestBody)
            false
        }
    }

    override fun presentSheet(configurationMap: Map<String, Any?>): Boolean {
        val requestBody = launchOptions.getJson(configurationMap).toString()

        return if (activity is FragmentActivity) {
            val manager = activity.supportFragmentManager
            val fragment = webFragment ?: WebViewFragment().also {
                manager.beginTransaction().add(android.R.id.content, it, TAG).commit()
            }

            fragment.setRequestBody(requestBody)

            manager.beginTransaction()
                .attach(fragment)
                .addToBackStack(TAG)
                .commit()

            true
        } else {
            WebViewHostActivityManager.launch(activity, requestBody)
            false
        }
    }
}
