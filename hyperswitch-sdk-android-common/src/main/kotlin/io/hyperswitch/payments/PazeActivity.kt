package io.hyperswitch.payments

import android.annotation.SuppressLint
import android.app.Activity
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class PazeActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pazeRequest = intent.getStringExtra("pazeRequest") ?: "{}"
        val pazeJson = JSONObject(pazeRequest)

        val publishableKey = pazeJson.optString("publishable_key", "")
        val clientId = pazeJson.optString("client_id", "")
        val clientName = pazeJson.optString("client_name", "")
        val clientProfileId = pazeJson.optString("client_profile_id", "")
        val emailAddress = pazeJson.optString("email_address", "")
        val transactionAmount = pazeJson.optString("transaction_amount", "")
        val transactionCurrencyCode = pazeJson.optString("transaction_currency_code", "")
        val sessionId = pazeJson.optString("session_id", "")

        val pazeScriptUrl = if (publishableKey.startsWith("pk_snd"))
            "https://sandbox.digitalwallet.earlywarning.com/web/resources/js/digitalwallet-sdk.js"
        else
            "https://checkout.paze.com/web/resources/js/digitalwallet-sdk.js"

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.setSupportMultipleWindows(false)

        webView.addJavascriptInterface(PazeJSInterface(this), "PazeNative")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject the Paze SDK script and run the flow
                val js = buildPazeFlowScript(
                    pazeScriptUrl,
                    clientId,
                    clientName,
                    clientProfileId,
                    emailAddress,
                    transactionAmount,
                    transactionCurrencyCode,
                    sessionId
                )
                view?.evaluateJavascript(js, null)
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel()
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Load a blank page; once loaded, onPageFinished will inject the Paze SDK
        webView.loadData(
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'></head><body></body></html>",
            "text/html",
            "utf-8"
        )
    }

    private fun buildPazeFlowScript(
        pazeScriptUrl: String,
        clientId: String,
        clientName: String,
        clientProfileId: String,
        emailAddress: String,
        transactionAmount: String,
        transactionCurrencyCode: String,
        sessionId: String
    ): String {
        return """
            (function() {
                var script = document.createElement('script');
                script.src = '$pazeScriptUrl';
                script.onload = function() {
                    (async function() {
                        try {
                            await DIGITAL_WALLET_SDK.initialize({
                                client: {
                                    id: '$clientId',
                                    name: '$clientName',
                                    profileId: '$clientProfileId'
                                }
                            });

                            var canCheckout = await DIGITAL_WALLET_SDK.canCheckout({
                                emailAddress: '$emailAddress'
                            });

                            var transactionValue = {
                                transactionAmount: '$transactionAmount',
                                transactionCurrencyCode: '$transactionCurrencyCode'
                            };

                            await DIGITAL_WALLET_SDK.checkout({
                                acceptedPaymentCardNetworks: ['VISA', 'MASTERCARD'],
                                emailAddress: canCheckout.consumerPresent ? '$emailAddress' : '',
                                sessionId: '$sessionId',
                                actionCode: 'START_FLOW',
                                transactionValue: transactionValue,
                                shippingPreference: 'ALL'
                            });

                            var completeResponse = await DIGITAL_WALLET_SDK.complete({
                                transactionOptions: {
                                    billingPreference: 'ALL',
                                    merchantCategoryCode: 'US',
                                    payloadTypeIndicator: 'PAYMENT'
                                },
                                transactionId: '',
                                sessionId: '$sessionId',
                                transactionType: 'PURCHASE',
                                transactionValue: transactionValue
                            });

                            var responseStr = '';
                            if (completeResponse && completeResponse.completeResponse) {
                                responseStr = completeResponse.completeResponse;
                            } else if (typeof completeResponse === 'string') {
                                responseStr = completeResponse;
                            } else {
                                responseStr = JSON.stringify(completeResponse);
                            }

                            PazeNative.onSuccess(responseStr);
                        } catch(e) {
                            var errMsg = e.message || JSON.stringify(e) || 'Unknown error';
                            PazeNative.onError(errMsg);
                        }
                    })();
                };
                script.onerror = function() {
                    PazeNative.onError('Failed to load Paze SDK script');
                };
                document.head.appendChild(script);
            })();
        """.trimIndent()
    }

    /**
     * JavaScript interface to receive callbacks from the Paze SDK running in the WebView.
     */
    class PazeJSInterface(private val activity: PazeActivity) {
        @JavascriptInterface
        fun onSuccess(completeResponse: String) {
            PazeCallbackManager.executeCallback(mutableMapOf<String, Any?>().apply {
                put("paymentMethodData", completeResponse)
            })
            activity.finish()
        }

        @JavascriptInterface
        fun onError(errorMessage: String) {
            PazeCallbackManager.executeCallback(mutableMapOf<String, Any?>().apply {
                put("error", errorMessage)
            })
            activity.finish()
        }

        @JavascriptInterface
        fun onCancel() {
            PazeCallbackManager.executeCallback(mutableMapOf<String, Any?>().apply {
                put("error", "Cancel")
            })
            activity.finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        PazeCallbackManager.executeCallback(mutableMapOf<String, Any?>().apply {
            put("error", "Cancel")
        })
        super.onBackPressed()
    }
}
