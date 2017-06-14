package com.duckduckgo.mobile.android.duckduckgo.ui.tab.web;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;

/**
 * Created by fgei on 5/15/17.
 */

public class DDGWebViewClient extends WebViewClient {

    private BrowserPresenter browserPresenter;
    private String tabId;

    public DDGWebViewClient(@NonNull BrowserPresenter browserPresenter, @NonNull String tabId) {
        this.browserPresenter = browserPresenter;
        this.tabId = tabId;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return false;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        browserPresenter.onPageStarted(tabId, url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        browserPresenter.onPageFinished(tabId, url);
    }
}
