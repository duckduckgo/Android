package com.duckduckgo.mobile.android.duckduckgo.ui.browser.web;

import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;

/**
 * Created by fgei on 5/15/17.
 */

public class DDGWebChromeClient extends WebChromeClient {

    private BrowserPresenter browserPresenter;

    public DDGWebChromeClient(BrowserPresenter browserPresenter) {
        this.browserPresenter = browserPresenter;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        browserPresenter.onProgressChanged(newProgress);
    }
}
