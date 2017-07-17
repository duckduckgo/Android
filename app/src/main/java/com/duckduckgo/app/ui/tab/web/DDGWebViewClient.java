/*
 * Copyright (c) 2017 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.ui.tab.web;

import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.duckduckgo.app.ui.browser.BrowserPresenter;

public class DDGWebViewClient extends WebViewClient {

    public static boolean externalBrowser = false;
    private BrowserPresenter browserPresenter;
    private String tabId;

    public DDGWebViewClient(@NonNull BrowserPresenter browserPresenter, @NonNull String tabId) {
        this.browserPresenter = browserPresenter;
        this.tabId = tabId;
    }

    //to open links in external browser, supports only lollipop & above
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

        if (!externalBrowser) return false;//if external browser useage is false
        Intent intent;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            intent = new Intent(Intent.ACTION_VIEW, request.getUrl()).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (request.getUrl().getHost().endsWith("duckduckgo.com")) {
                return false; //if url is duckduckgo.com then don't show the external browser dialog
            }
            view.getContext().startActivity(intent);
            return true;
        }

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

    /* handling external links as intents deprecated method
    @Deprecated
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {

        if( Uri.parse(url).getHost().endsWith("facebook.com") ) {
            return false;
        }

        return true;
    } */
}
