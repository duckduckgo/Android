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

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.duckduckgo.app.ui.browser.BrowserPresenter;

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

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        super.onReceivedIcon(view, icon);
        browserPresenter.onReceivedIcon(tabId, icon);
    }
}
