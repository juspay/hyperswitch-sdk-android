package io.hyperswitch.webview.utils;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Message;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HSWebChromeClient extends WebChromeClient /*implements LifecycleEventListener*/ {
    protected static final FrameLayout.LayoutParams FULLSCREEN_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);

    protected static final int FULLSCREEN_SYSTEM_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    protected static final int COMMON_PERMISSION_REQUEST = 3;

    protected HSWebView mWebView;

    protected View mVideoView;
    protected WebChromeClient.CustomViewCallback mCustomViewCallback;

    /*
     * - Permissions -
     * As native permissions are asynchronously handled by the PermissionListener, many fields have
     * to be stored to send permissions results to the webview
     */

    // Webview camera & audio permission callback
    protected PermissionRequest permissionRequest;
    // Webview camera & audio permission already granted
    protected List<String> grantedPermissions;

    // Webview geolocation permission callback
    protected GeolocationPermissions.Callback geolocationPermissionCallback;
    // Webview geolocation permission origin callback
    protected String geolocationPermissionOrigin;

    // true if native permissions dialog is shown, false otherwise
    protected boolean permissionsRequestShown = false;
    // Pending Android permissions for the next request
    protected List<String> pendingPermissions = new ArrayList<>();

    protected HSWebView.ProgressChangedFilter progressChangedFilter = null;
    protected boolean mAllowsProtectedMedia = false;

    protected boolean mHasOnOpenWindowEvent = false;

    public HSWebChromeClient(HSWebView webView) {
        this.mWebView = webView;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {

        final WebView newWebView = new WebView(view.getContext());

        WebSettings settings = newWebView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        settings.setBuiltInZoomControls(true);

        settings.setDisplayZoomControls(false);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptThirdPartyCookies(newWebView, true);

        newWebView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

//        if(mHasOnOpenWindowEvent) {
//            newWebView.setWebViewClient(new WebViewClient(){
//                @Override
//                public boolean shouldOverrideUrlLoading (WebView subview, String url) {
//                    WritableMap event = Arguments.createMap();
//                    event.putString("targetUrl", url);
//
//                    ((HSWebView) view).dispatchEvent(event);
//
//                    return true;
//                }
//            });
//        }

        newWebView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading (WebView subview, String url) {
                WritableMap event = Arguments.createMap();
                event.putString("targetUrl", url);
                ((HSWebView) view).dispatchEvent(event);

                // Actually load the URL!
                subview.loadUrl(url);
                return true;
            }
        });

        newWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onCloseWindow(WebView window) {
                // Remove the WebView from parent when window.close() is called
                ViewGroup parent = (ViewGroup) window.getParent();
                if (parent != null) {
                    parent.removeView(window);
                }
                window.destroy();
            }
        });

        ViewGroup parent = (ViewGroup) view.getParent().getParent();
        if (parent != null) {
            WindowInsets windowInsets = parent.getRootView().getRootWindowInsets();
            if (windowInsets != null) {
                Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.statusBars()
                                | WindowInsets.Type.displayCutout()
                                | WindowInsets.Type.navigationBars()
                                | WindowInsets.Type.captionBar()
                );
                parent.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            }
            parent.addView(newWebView);
        }

        final WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWebView);
        resultMsg.sendToTarget();

        return true;
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage message) {
        if (BuildConfig.DEBUG) {
            return super.onConsoleMessage(message);
        }
        // Ignore console logs in non debug builds.
        return true;
    }

    @Override
    public void onProgressChanged(WebView webView, int newProgress) {
        super.onProgressChanged(webView, newProgress);
        final String url = webView.getUrl();
        if (progressChangedFilter.isWaitingForCommandLoadUrl()) {
            return;
        }
//        int reactTag = HSWebViewWrapper.getReactTagFromWebView(webView);
        WritableMap event = Arguments.createMap();
//        event.putDouble("target", reactTag);
        event.putString("title", webView.getTitle());
        event.putString("url", url);
        event.putBoolean("canGoBack", webView.canGoBack());
        event.putBoolean("canGoForward", webView.canGoForward());
        event.putDouble("progress", (float) newProgress / 100);

        this.mWebView.dispatchEvent(event);
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {

        grantedPermissions = new ArrayList<>();

        ArrayList<String> requestedAndroidPermissions = new ArrayList<>();
        for (String requestedResource : request.getResources()) {
            String androidPermission = null;

            if (requestedResource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                androidPermission = Manifest.permission.RECORD_AUDIO;
            } else if (requestedResource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                androidPermission = Manifest.permission.CAMERA;
            } else if(requestedResource.equals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                if (mAllowsProtectedMedia) {
                    grantedPermissions.add(requestedResource);
                } else {
                    /**
                     * Legacy handling (Kept in case it was working under some conditions (given Android version or something))
                     *
                     * Try to ask user to grant permission using Activity.requestPermissions
                     *
                     * Find more details here: https://github.com/react-native-webview/react-native-webview/pull/2732
                     */
                    androidPermission = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID;
                }            }
            // TODO: RESOURCE_MIDI_SYSEX, RESOURCE_PROTECTED_MEDIA_ID.
            if (androidPermission != null) {
                if (ContextCompat.checkSelfPermission(this.mWebView.getThemedReactContext(), androidPermission) == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(requestedResource);
                } else {
                    requestedAndroidPermissions.add(androidPermission);
                }
            }
        }

        // If all the permissions are already granted, send the response to the WebView synchronously
        if (requestedAndroidPermissions.isEmpty()) {
            request.grant(grantedPermissions.toArray(new String[0]));
            grantedPermissions = null;
            return;
        }

        // Otherwise, ask to Android System for native permissions asynchronously

        this.permissionRequest = request;

        requestPermissions(requestedAndroidPermissions);
    }


    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {

        if (ContextCompat.checkSelfPermission(this.mWebView.getThemedReactContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            /*
             * Keep the trace of callback and origin for the async permission request
             */
            geolocationPermissionCallback = callback;
            geolocationPermissionOrigin = origin;

            requestPermissions(Collections.singletonList(Manifest.permission.ACCESS_FINE_LOCATION));

        } else {
            callback.invoke(origin, true, false);
        }
    }

//    private PermissionAwareActivity getPermissionAwareActivity() {
//        Activity activity = this.mWebView.getThemedReactContext().getCurrentActivity();
//        if (activity == null) {
//            throw new IllegalStateException("Tried to use permissions API while not attached to an Activity.");
//        } else if (!(activity instanceof PermissionAwareActivity)) {
//            throw new IllegalStateException("Tried to use permissions API but the host Activity doesn't implement PermissionAwareActivity.");
//        }
//        return (PermissionAwareActivity) activity;
//    }

    private synchronized void requestPermissions(List<String> permissions) {

        /*
         * If permissions request dialog is displayed on the screen and another request is sent to the
         * activity, the last permission asked is skipped. As a work-around, we use pendingPermissions
         * to store next required permissions.
         */

        if (permissionsRequestShown) {
            pendingPermissions.addAll(permissions);
            return;
        }

//        PermissionAwareActivity activity = getPermissionAwareActivity();
//        permissionsRequestShown = true;
//
//        activity.requestPermissions(
//                permissions.toArray(new String[0]),
//                COMMON_PERMISSION_REQUEST,
//                webviewPermissionsListener
//        );
//
//        // Pending permissions have been sent, the list can be cleared
//        pendingPermissions.clear();
    }


//    private PermissionListener webviewPermissionsListener = (requestCode, permissions, grantResults) -> {
//
//        permissionsRequestShown = false;
//
//        /*
//         * As a "pending requests" approach is used, requestCode cannot help to define if the request
//         * came from geolocation or camera/audio. This is why shouldAnswerToPermissionRequest is used
//         */
//        boolean shouldAnswerToPermissionRequest = false;
//
//        for (int i = 0; i < permissions.length; i++) {
//
//            String permission = permissions[i];
//            boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
//
//            if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)
//                    && geolocationPermissionCallback != null
//                    && geolocationPermissionOrigin != null) {
//
//                if (granted) {
//                    geolocationPermissionCallback.invoke(geolocationPermissionOrigin, true, false);
//                } else {
//                    geolocationPermissionCallback.invoke(geolocationPermissionOrigin, false, false);
//                }
//
//                geolocationPermissionCallback = null;
//                geolocationPermissionOrigin = null;
//            }
//
//            if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
//                if (granted && grantedPermissions != null) {
//                    grantedPermissions.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
//                }
//                shouldAnswerToPermissionRequest = true;
//            }
//
//            if (permission.equals(Manifest.permission.CAMERA)) {
//                if (granted && grantedPermissions != null) {
//                    grantedPermissions.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
//                }
//                shouldAnswerToPermissionRequest = true;
//            }
//
//            if (permission.equals(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
//                if (granted && grantedPermissions != null) {
//                    grantedPermissions.add(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);
//                }
//                shouldAnswerToPermissionRequest = true;
//            }
//        }
//
//        if (shouldAnswerToPermissionRequest
//                && permissionRequest != null
//                && grantedPermissions != null) {
//            permissionRequest.grant(grantedPermissions.toArray(new String[0]));
//            permissionRequest = null;
//            grantedPermissions = null;
//        }
//
//        if (!pendingPermissions.isEmpty()) {
//            requestPermissions(pendingPermissions);
//            return false;
//        }
//
//        return true;
//    };

    protected void openFileChooser(ValueCallback<Uri> filePathCallback, String acceptType) {
//        this.mWebView.getThemedReactContext().getNativeModule(HSWebViewModule.class).startPhotoPickerIntent(filePathCallback, acceptType);
    }

    protected void openFileChooser(ValueCallback<Uri> filePathCallback) {
//        this.mWebView.getThemedReactContext().getNativeModule(HSWebViewModule.class).startPhotoPickerIntent(filePathCallback, "");
    }

    protected void openFileChooser(ValueCallback<Uri> filePathCallback, String acceptType, String capture) {
//        this.mWebView.getThemedReactContext().getNativeModule(HSWebViewModule.class).startPhotoPickerIntent(filePathCallback, acceptType);
    }

//    @Override
//    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
//        String[] acceptTypes = fileChooserParams.getAcceptTypes();
//        boolean allowMultiple = fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
//
//        return this.mWebView.getThemedReactContext().getNativeModule(HSWebViewModule.class).startPhotoPickerIntent(filePathCallback, acceptTypes, allowMultiple, fileChooserParams.isCaptureEnabled());
//    }

//    @Override
//    public void onHostResume() {
//        if (mVideoView != null && mVideoView.getSystemUiVisibility() != FULLSCREEN_SYSTEM_UI_VISIBILITY) {
//            mVideoView.setSystemUiVisibility(FULLSCREEN_SYSTEM_UI_VISIBILITY);
//        }
//    }
//
//    @Override
//    public void onHostPause() { }
//
//    @Override
//    public void onHostDestroy() { }

//    protected ViewGroup getRootView() {
//        return this.mWebView.getThemedReactContext().getCurrentActivity().findViewById(android.R.id.content);
//    }

    public void setProgressChangedFilter(HSWebView.ProgressChangedFilter filter) {
        progressChangedFilter = filter;
    }

    /**
     * Set whether or not protected media should be allowed
     * /!\ Setting this to false won't revoke permission already granted to the current webpage.
     * In order to do so, you'd need to reload the page /!\
     */
    public void setAllowsProtectedMedia(boolean enabled) {
        mAllowsProtectedMedia = enabled;
    }

    public void setHasOnOpenWindowEvent(boolean hasEvent) {
        mHasOnOpenWindowEvent = hasEvent;
    }
}