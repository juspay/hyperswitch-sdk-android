package io.hyperswitch.webview.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HSWebView extends WebView {
    protected @Nullable
    String injectedJS;
    protected @Nullable
    String injectedJSBeforeContentLoaded;
    protected static final String JAVASCRIPT_INTERFACE = "HSAndroidInterface";
    protected @Nullable
    HSWebViewBridge fallbackBridge;
    protected @Nullable
    WebViewCompat.WebMessageListener bridgeListener = null;

    /**
     * android.webkit.WebChromeClient fundamentally does not support JS injection into frames other
     * than the main frame, so these two properties are mostly here just for parity with iOS & macOS.
     */
    protected boolean injectedJavaScriptForMainFrameOnly = true;
    protected boolean injectedJavaScriptBeforeContentLoadedForMainFrameOnly = true;

    protected boolean messagingEnabled = false;
    protected @Nullable
    String messagingModuleName;
    protected @Nullable
    HSWebViewMessagingModule mMessagingJSModule;
    protected @Nullable
    HSWebViewClient mHSWebViewClient;
    protected boolean sendContentSizeChangeEvents = false;
    //    private OnScrollDispatchHelper mOnScrollDispatchHelper;
    protected boolean hasScrollEvent = false;
    protected boolean nestedScrollEnabled = false;
    protected ProgressChangedFilter progressChangedFilter;

    private final Callback callback;

    /**
     * WebView must be created with an context of the current activity
     * <p>
     * Activity Context is required for creation of dialogs internally by WebView
     * Reactive Native needed for access to ReactNative internal system functionality
     */
    public HSWebView(Context reactContext, Callback onMessage) {
        super(reactContext);
//        mMessagingJSModule = ((Context) this.getContext()).getApplicationContext().getJSModule(HSWebViewMessagingModule.class);
        progressChangedFilter = new ProgressChangedFilter();
        callback = onMessage;
    }

    public void setBasicAuthCredential(HSBasicAuthCredential credential) {
        mHSWebViewClient.setBasicAuthCredential(credential);
    }

    public void setSendContentSizeChangeEvents(boolean sendContentSizeChangeEvents) {
        this.sendContentSizeChangeEvents = sendContentSizeChangeEvents;
    }

    public void setHasScrollEvent(boolean hasScrollEvent) {
        this.hasScrollEvent = hasScrollEvent;
    }

    public void setNestedScrollEnabled(boolean nestedScrollEnabled) {
        this.nestedScrollEnabled = nestedScrollEnabled;
    }

//    @Override
//    public void onHostResume() {
//        // do nothing
//    }
//
//    @Override
//    public void onHostPause() {
//        // do nothing
//    }
//
//    @Override
//    public void onHostDestroy() {
//        cleanupCallbacksAndDestroy();
//    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.nestedScrollEnabled) {
            requestDisallowInterceptTouchEvent(true);
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);

        WritableMap map = Arguments.createMap();
        map.putDouble("width", w);
        map.putDouble("height", h);

        if (sendContentSizeChangeEvents) {
            dispatchEvent(this, map);
        }
    }

    protected @Nullable
    List<Map<String, String>> menuCustomItems;

    public void setMenuCustomItems(List<Map<String, String>> menuCustomItems) {
        this.menuCustomItems = menuCustomItems;
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        if(menuCustomItems == null ){
            return super.startActionMode(callback, type);
        }

        return super.startActionMode(new ActionMode.Callback2() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                for (int i = 0; i < menuCustomItems.size(); i++) {
                    menu.add(Menu.NONE, i, i, (menuCustomItems.get(i)).get("label"));
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                WritableMap wMap = Arguments.createMap();
                HSWebView.this.evaluateJavascript(
                        "(function(){return {selection: window.getSelection().toString()} })()",
                        selectionJson -> {
                            Map<String, String> menuItemMap = menuCustomItems.get(item.getItemId());
                            wMap.putString("label", menuItemMap.get("label"));
                            wMap.putString("key", menuItemMap.get("key"));
                            String selectionText = "";
                            try {
                                selectionText = new JSONObject(selectionJson).getString("selection");
                            } catch (JSONException ignored) {}
                            wMap.putString("selectedText", selectionText);
                             dispatchEvent(HSWebView.this, wMap);
                            mode.finish();
                        }
                );
                return true;
            }
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mode = null;
            }

            @Override
            public void onGetContentRect (ActionMode mode,
                                          View view,
                                          Rect outRect){
                if (callback instanceof ActionMode.Callback2) {
                    ((ActionMode.Callback2) callback).onGetContentRect(mode, view, outRect);
                } else {
                    super.onGetContentRect(mode, view, outRect);
                }
            }
        }, type);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        super.setWebViewClient(client);
        if (client instanceof HSWebViewClient) {
            mHSWebViewClient = (HSWebViewClient) client;
            mHSWebViewClient.setProgressChangedFilter(progressChangedFilter);
        }
    }

    WebChromeClient mWebChromeClient;
    @Override
    public void setWebChromeClient(WebChromeClient client) {
        this.mWebChromeClient = client;
        super.setWebChromeClient(client);
        if (client instanceof HSWebChromeClient) {
            ((HSWebChromeClient) client).setProgressChangedFilter(progressChangedFilter);
        }
    }

    public WebChromeClient getWebChromeClient() {
        return this.mWebChromeClient;
    }

    public @Nullable
    HSWebViewClient getHSWebViewClient() {
        return mHSWebViewClient;
    }

    public boolean getMessagingEnabled() {
        return this.messagingEnabled;
    }

    protected void createHSWebViewBridge(HSWebView webView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
            if (this.bridgeListener == null) {
                this.bridgeListener = new WebViewCompat.WebMessageListener() {
                    @Override
                    public void onPostMessage(@NonNull WebView view, @NonNull WebMessageCompat message, @NonNull Uri sourceOrigin, boolean isMainFrame, @NonNull JavaScriptReplyProxy replyProxy) {
                        HSWebView.this.onMessage(message.getData(), sourceOrigin.toString());
                    }
                };
                WebViewCompat.addWebMessageListener(
                        webView,
                        JAVASCRIPT_INTERFACE,
                        Set.of("*"),
                        this.bridgeListener
                );
            }
        } else {
            if (fallbackBridge == null) {
                fallbackBridge = new HSWebViewBridge(webView);
                addJavascriptInterface(fallbackBridge, JAVASCRIPT_INTERFACE);
            }
        }
        injectJavascriptObject();
    }

    private void injectJavascriptObject() {
        if (getSettings().getJavaScriptEnabled()) {
            String js = "(function(){\n" +
                    "    window." + JAVASCRIPT_INTERFACE + " = window." + JAVASCRIPT_INTERFACE + " || {};\n" +
                    "    window." + JAVASCRIPT_INTERFACE + ".injectedObjectJson = function () { return " + (injectedJavaScriptObject == null ? null : ("`" + injectedJavaScriptObject + "`")) + "; };\n" +
                    "})();";
            evaluateJavascriptWithFallback(js);
        }
    }

    @SuppressLint("AddJavascriptInterface")
    public void setMessagingEnabled(boolean enabled) {
        if (messagingEnabled == enabled) {
            return;
        }

        messagingEnabled = enabled;

        if (enabled) {
            createHSWebViewBridge(this);
        }
    }

    protected void evaluateJavascriptWithFallback(String script) {
        evaluateJavascript(script, null);
    }

    public void callInjectedJavaScript() {
        if (getSettings().getJavaScriptEnabled() &&
                injectedJS != null &&
                !TextUtils.isEmpty(injectedJS)) {
            evaluateJavascriptWithFallback("(function() {\n" + injectedJS + ";\n})();");
            injectJavascriptObject(); // re-inject the Javascript object in case it has been overwritten.
        }
    }

    public void callInjectedJavaScriptBeforeContentLoaded() {
        if (getSettings().getJavaScriptEnabled() &&
                injectedJSBeforeContentLoaded != null &&
                !TextUtils.isEmpty(injectedJSBeforeContentLoaded)) {
            evaluateJavascriptWithFallback("(function() {\n" + injectedJSBeforeContentLoaded + ";\n})();");
            injectJavascriptObject();  // re-inject the Javascript object in case it has been overwritten.
        }
    }

    protected String injectedJavaScriptObject = null;

    public void setInjectedJavaScriptObject(String obj) {
        this.injectedJavaScriptObject = obj;
        injectJavascriptObject();
    }

    public void onMessage(String message, String sourceUrl) {
        Context reactContext = getThemedReactContext();
        HSWebView mWebView = this;

        if (mHSWebViewClient != null) {
            WebView webView = this;
            webView.post(new Runnable() {
                @Override
                public void run() {
                    if (mHSWebViewClient == null) {
                        return;
                    }
                    WritableMap data = mHSWebViewClient.createWebViewEvent(webView, sourceUrl);
                    data.putString("data", message);

                    if (mMessagingJSModule != null) {
                        dispatchDirectMessage(data);
                    } else {
                         dispatchEvent(webView, data);
                    }
                }
            });
        } else {
            WritableMap eventData = Arguments.createMap();
            eventData.putString("data", message);

            if (mMessagingJSModule != null) {
                dispatchDirectMessage(eventData);
            } else {
                 dispatchEvent(this, eventData);
            }
        }
    }

    protected void dispatchDirectMessage(WritableMap data) {
        WritableNativeMap event = new WritableNativeMap();
        event.putMap("nativeEvent", data);
        event.putString("messagingModuleName", messagingModuleName);

        mMessagingJSModule.onMessage(event);
    }

    protected boolean dispatchDirectShouldStartLoadWithRequest(WritableMap data) {
        WritableNativeMap event = new WritableNativeMap();
        event.putMap("nativeEvent", data);
        event.putString("messagingModuleName", messagingModuleName);

        mMessagingJSModule.onShouldStartLoadWithRequest(event);
        return true;
    }

//    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
//        super.onScrollChanged(x, y, oldX, oldY);
//
//        if (!hasScrollEvent) {
//            return;
//        }
//
//        if (mOnScrollDispatchHelper == null) {
//            mOnScrollDispatchHelper = new OnScrollDispatchHelper();
//        }
//
//        if (mOnScrollDispatchHelper.onScrollChanged(x, y)) {
//            ScrollEvent event = ScrollEvent.obtain(
//                    HSWebViewWrapper.getReactTagFromWebView(this),
//                    ScrollEventType.SCROLL,
//                    x,
//                    y,
//                    mOnScrollDispatchHelper.getXFlingVelocity(),
//                    mOnScrollDispatchHelper.getYFlingVelocity(),
//                    this.computeHorizontalScrollRange(),
//                    this.computeVerticalScrollRange(),
//                    this.getWidth(),
//                    this.getHeight());
//
//            dispatchEvent(this, event);
//        }
//    }

    protected void dispatchEvent(ReadableMap event) {
        dispatchEvent(this, event);
    }
    protected void dispatchEvent(WebView webView, ReadableMap event) {
        Context reactContext = getThemedReactContext();
//        int reactTag = HSWebViewWrapper.getReactTagFromWebView(webView);
//        UIManagerHelper.getEventDispatcherForReactTag(reactContext, reactTag).dispatchEvent(event);
        System.out.println(event.toHashMap());

        callback.invoke(event.toHashMap());
    }

    protected void cleanupCallbacksAndDestroy() {
        setWebViewClient(null);
        destroy();
    }

    @Override
    public void destroy() {
        if (mWebChromeClient != null) {
            mWebChromeClient.onHideCustomView();
        }
        super.destroy();
    }

    public Context getThemedReactContext() {
        return this.getContext();
    }
//
//    public ReactApplicationContext getReactApplicationContext() {
//        return this.getThemedReactContext().getReactApplicationContext();
//    }

    protected class HSWebViewBridge {
        private String TAG = "HSWebViewBridge";
        HSWebView mWebView;

        HSWebViewBridge(HSWebView c) {
            mWebView = c;
        }

        /**
         * This method is called whenever JavaScript running within the web view calls:
         * - window[JAVASCRIPT_INTERFACE].postMessage
         */
        @JavascriptInterface
        public void postMessage(String message) {
            if (mWebView.getMessagingEnabled()) {
                // Post to main thread because `mWebView.getUrl()` requires to be executed on main.
                mWebView.post(() -> mWebView.onMessage(message, mWebView.getUrl()));
            } else {
                Log.w(TAG, "HSAndroidInterface.postMessage method was called but messaging is disabled. Pass an onMessage handler to the WebView.");
            }
        }
    }


    protected static class ProgressChangedFilter {
        private boolean waitingForCommandLoadUrl = false;

        public void setWaitingForCommandLoadUrl(boolean isWaiting) {
            waitingForCommandLoadUrl = isWaiting;
        }

        public boolean isWaitingForCommandLoadUrl() {
            return waitingForCommandLoadUrl;
        }
    }
}