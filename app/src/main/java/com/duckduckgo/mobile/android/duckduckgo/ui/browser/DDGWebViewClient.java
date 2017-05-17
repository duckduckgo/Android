package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.graphics.Bitmap;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Created by fgei on 5/15/17.
 */

public class DDGWebViewClient extends WebViewClient {

    private BrowserContract.Presenter browserPresenter;

    public DDGWebViewClient(BrowserContract.Presenter browserPresenter) {
        this.browserPresenter = browserPresenter;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        browserPresenter.onPageStarted(url);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        browserPresenter.onPageFinished(url);
    }
}
