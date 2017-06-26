package com.duckduckgo.mobile.android.duckduckgo.ui.tab.web;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;

/**
 *    Copyright 2017 DuckDuckGo
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
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
        browserPresenter.onHistoryChanged(tabId, view.canGoBack(), view.canGoForward());
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        browserPresenter.onPageFinished(tabId, url);
        browserPresenter.onHistoryChanged(tabId, view.canGoBack(), view.canGoForward());
    }
}
