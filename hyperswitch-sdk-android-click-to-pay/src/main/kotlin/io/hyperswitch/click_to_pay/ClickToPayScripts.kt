package io.hyperswitch.click_to_pay

/**
 * Centralized container for all JavaScript code used in Click to Pay WebView communication.
 *
 * This file contains all JS snippets that are evaluated in the WebView, keeping the main
 * launcher code clean and making the JavaScript logic easier to maintain and test.
 */
object ClickToPayScripts {

    /**
     * Generates the HTML page for SDK initialization.
     *
     * @param publishableKey The publishable API key
     * @param customBackendUrl Optional custom backend URL
     * @param customLogUrl Optional custom logging URL
     * @param requestId Unique request identifier for tracking
     * @param hyperLoaderUrl URL to load the HyperLoader.js script
     * @return HTML string containing the initialization page
     */
    fun createInitializationHtml(
        publishableKey: String,
        customBackendUrl: String?,
        customLogUrl: String?,
        requestId: String,
        hyperLoaderUrl: String
    ): String {
        val customBackendParam = customBackendUrl?.let { "customBackendUrl:'$customBackendUrl'," } ?: ""
        val customLogParam = customLogUrl?.let { "customLogUrl:'$customLogUrl'," } ?: ""

        return """<!DOCTYPE html>
<html>
<head>
<script>
function handleScriptError() {
    console.error('ClickToPay','Failed to load HyperLoader.js');
    window.HSAndroidInterface.postMessage(JSON.stringify({
        requestId:'$requestId',
        data:{error:{type:'ScriptLoadError',message:'Failed to load HyperLoader.js'}}
    }));
}
async function initHyper() {
    try {
        if (typeof Hyper === 'undefined') {
            window.HSAndroidInterface.postMessage(JSON.stringify({
                requestId:'$requestId',
                data:{error:{type:'HyperUndefinedError',message:'Hyper is not defined'}}
            }));
            return;
        }
        window.hyperInstance = Hyper.init('$publishableKey',{$customBackendParam$customLogParam});
        window.HSAndroidInterface.postMessage(JSON.stringify({
            requestId:'$requestId',
            data:{sdkInitialised:true}
        }));
    } catch (error) {
        window.HSAndroidInterface.postMessage(JSON.stringify({
            requestId:'$requestId',
            data:{error:{type:'HyperInitializationError',message:error.message}}
        }));
    }
}
</script>
<script src='$hyperLoaderUrl' onload='initHyper()' onerror='handleScriptError()' async></script>
</head>
<body></body>
</html>"""
    }

    /**
     * Initializes a new Click to Pay session.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @param request3DSAuthentication Whether to request 3DS authentication
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun initClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        request3DSAuthentication: Boolean,
        requestId: String
    ): String = """
        (async function() {
            try {
                const authenticationSession = window.hyperInstance.initAuthenticationSession({
                    clientSecret: '$clientSecret',
                    profileId: '$profileId',
                    authenticationId: '$authenticationId',
                    merchantId: '$merchantId'
                });
                window.ClickToPaySession = await authenticationSession.initClickToPaySession({
                    request3DSAuthentication: $request3DSAuthentication
                });
                const data = window.ClickToPaySession.error ? window.ClickToPaySession : {success: true};
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: data
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: 'InitClickToPaySessionError', message: error.message}}
                }));
            }
        })();
    """.trimIndent()

    /**
     * Gets the active Click to Pay session for an activity switch.
     *
     * @param clientSecret The client secret from the payment intent
     * @param profileId The merchant profile identifier
     * @param authenticationId The authentication session identifier
     * @param merchantId The merchant identifier
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun getActiveClickToPaySession(
        clientSecret: String,
        profileId: String,
        authenticationId: String,
        merchantId: String,
        requestId: String
    ): String = """
        (async function() {
            try {
                let authenticationSession = window.hyperInstance.initAuthenticationSession({
                    clientSecret: '$clientSecret',
                    profileId: '$profileId',
                    authenticationId: '$authenticationId',
                    merchantId: '$merchantId'
                });
                window.ClickToPaySession = await authenticationSession?.getActiveClickToPaySession();
                const data = window.ClickToPaySession.error ? window.ClickToPaySession : {success: true};
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: data
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: 'getActiveClickToPaySessionError', message: error.message}}
                }));
            }
        })();
    """.trimIndent()

    /**
     * Checks if a customer has an existing Click to Pay profile.
     *
     * @param email Optional customer email
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun isCustomerPresent(email: String?, requestId: String): String {
        val emailParam = email?.let { "email: '$email'" } ?: ""
        return """
            (async function() {
                try {
                    const isCustomerPresent = await window.ClickToPaySession.isCustomerPresent({$emailParam});
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: '$requestId',
                        data: isCustomerPresent
                    }));
                } catch (error) {
                    window.HSAndroidInterface.postMessage(JSON.stringify({
                        requestId: '$requestId',
                        data: {error: {type: 'IsCustomerPresentError', message: error.message}}
                    }));
                }
            })();
        """.trimIndent()
    }

    /**
     * Gets the user type (cards status).
     *
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun getUserType(requestId: String): String = """
        (async function() {
            try {
                const userType = await window.ClickToPaySession.getUserType();
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: userType
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: error.type || 'ERROR', message: error.message}}
                }));
            }
        })();
    """.trimIndent()

    /**
     * Gets the list of recognized cards for the customer.
     *
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun getRecognizedCards(requestId: String): String = """
        (async function() {
            try {
                const cards = await window.ClickToPaySession.getRecognizedCards();
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: cards
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: 'GetRecognizedCardsError', message: error.message}}
                }));
            }
        })();
    """.trimIndent()

    /**
     * Validates customer authentication with OTP.
     *
     * @param otpValue The OTP value entered by the customer
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun validateCustomerAuthentication(otpValue: String, requestId: String): String = """
        (async function() {
            try {
                const cards = await window.ClickToPaySession.validateCustomerAuthentication({value: '$otpValue'});
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: cards
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: error.type || 'ERROR', message: error.message}}
                }));
            }
        })();
    """.trimIndent()

    /**
     * Processes checkout with a selected card.
     *
     * @param srcDigitalCardId The selected card's digital ID
     * @param rememberMe Whether to remember the customer
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun checkoutWithCard(srcDigitalCardId: String, rememberMe: Boolean, requestId: String): String = """
        (async function() {
            try {
                const checkoutResponse = await window.ClickToPaySession.checkoutWithCard({
                    srcDigitalCardId: '$srcDigitalCardId',
                    rememberMe: $rememberMe
                });
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: checkoutResponse
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: 'CheckoutWithCardError', message: error.message}}
                }));
            }
        })();
    """.trimIndent()

    /**
     * Closes the Hyper instance.
     *
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun closeHyperInstance(requestId: String): String = """
        (async function() {
            try {
                await window.hyperInstance.deinit();
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {code: 'success'}
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: 'CloseInstanceFailed', message: error.message}}
                }));
            }
        })();
    """.trimIndent()

    /**
     * Signs out the customer and clears cookies.
     *
     * @param requestId Unique request identifier for tracking
     * @return JavaScript code to execute
     */
    fun signOut(requestId: String): String = """
        (async function() {
            try {
                const signOutResponse = await window.ClickToPaySession.signOut();
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: signOutResponse
                }));
            } catch (error) {
                window.HSAndroidInterface.postMessage(JSON.stringify({
                    requestId: '$requestId',
                    data: {error: {type: 'SignOutError', message: error.message}}
                }));
            }
        })();
    """.trimIndent()
}
