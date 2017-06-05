package com.duckduckgo.mobile.android.duckduckgo.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.webkit.WebView;

import java.util.Collection;

/**
 * Created by fgei on 6/5/17.
 */

public class WebViewUtils {
    private WebViewUtils() {
    }

    public static void resume(@Nullable WebView webView) {
        if (webView == null) return;
        webView.onResume();
        webView.resumeTimers();
    }

    public static void pause(@Nullable WebView webView) {
        if (webView == null) return;
        webView.onPause();
        webView.pauseTimers();
    }

    public static void destroy(@Nullable WebView webView) {
        if (webView == null) return;
        webView.onPause();
        webView.pauseTimers();
        webView.destroy();
    }

    public static void destroy(@NonNull Collection<WebView> webViews) {
        for (WebView webView : webViews) {
            destroy(webView);
        }
    }
}
