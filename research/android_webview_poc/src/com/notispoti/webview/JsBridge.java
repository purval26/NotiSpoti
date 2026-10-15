package com.notispoti.webview;

import android.webkit.JavascriptInterface;

public class JsBridge {

    public interface BridgeListener {
        void onBridgeMessage(String tag, String message);
    }

    private final BridgeListener listener;

    public JsBridge(BridgeListener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void reportStatus(String stage, String details) {
        if (listener != null) {
            listener.onBridgeMessage("JS_BRIDGE_STATUS", "[" + stage + "] " + details);
        }
    }

    @JavascriptInterface
    public void reportDrmSupport(boolean hasEme, boolean widevineSupported, String keySystem, String details) {
        if (listener != null) {
            listener.onBridgeMessage("JS_BRIDGE_DRM", 
                "EME: " + hasEme + 
                " | Widevine: " + widevineSupported + 
                " | KeySystem: " + keySystem + 
                " | Details: " + details);
        }
    }

    @JavascriptInterface
    public void reportDomAudit(String title, String currentUrl, boolean hasAppBanner, boolean hasPlayButton, boolean hasPlayerBar, String details) {
        if (listener != null) {
            listener.onBridgeMessage("JS_BRIDGE_DOM",
                "Title: \"" + title + "\"" +
                " | AppBanner: " + hasAppBanner +
                " | PlayButton: " + hasPlayButton +
                " | PlayerBar: " + hasPlayerBar +
                " | Extra: " + details);
        }
    }

    @JavascriptInterface
    public void log(String message) {
        if (listener != null) {
            listener.onBridgeMessage("JS_BRIDGE_LOG", message);
        }
    }
}
