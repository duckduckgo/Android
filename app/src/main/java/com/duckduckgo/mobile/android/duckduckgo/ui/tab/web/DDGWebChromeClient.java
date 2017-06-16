package com.duckduckgo.mobile.android.duckduckgo.ui.tab.web;

import android.support.annotation.NonNull;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;

/**
 * Created by fgei on 5/15/17.
 */

public class DDGWebChromeClient extends WebChromeClient {

    private BrowserPresenter browserPresenter;
    private String tabId;

    public DDGWebChromeClient(@NonNull BrowserPresenter browserPresenter, @NonNull String tabId) {
        this.browserPresenter = browserPresenter;
        this.tabId = tabId;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        browserPresenter.onProgressChanged(tabId, newProgress);
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        browserPresenter.onReceiveTitle(tabId, title);
    }
}
