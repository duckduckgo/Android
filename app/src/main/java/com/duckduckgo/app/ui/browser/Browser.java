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

package com.duckduckgo.app.ui.browser;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.AttrRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.widget.FrameLayout;

import com.duckduckgo.app.Injector;
import com.duckduckgo.app.ui.tab.DDGWebView;
import com.duckduckgo.app.ui.tab.web.DDGWebChromeClient;
import com.duckduckgo.app.ui.tab.web.DDGWebViewClient;

import java.util.HashMap;
import java.util.Map;

public class Browser extends FrameLayout implements BrowserView {

    private static final String EXTRA_WEB_VIEW_STATE = "extra_web_view_state";
    private static final String EXTRA_CURRENT_ID = "extra_current_id";

    private Map<String, DDGWebView> webViews = new HashMap<>();
    private String currentId;

    private BrowserPresenter browserPresenter;

    public Browser(@NonNull Context context) {
        super(context);
        init();
    }

    public Browser(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Browser(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Browser(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setSaveEnabled(true);
        browserPresenter = Injector.injectBrowserPresenter();
    }

    @Override
    public void createNewTab(@NonNull String id) {
        webViews.put(id, instantiateNewWebView(id));
    }

    @Override
    public void showTab(@NonNull String id) {
        removeAllViews();
        currentId = id;
        DDGWebView webView = webViews.get(id);
        if (webView == null) return;
        addView(webView);
        bringChildToFront(webView);
        webView.setVisibility(View.VISIBLE);
        browserPresenter.attachTabView(webView);
    }

    @Override
    public void deleteTab(@NonNull String id) {
        DDGWebView webViewToDelete = webViews.remove(id);
        destroyWebView(webViewToDelete);
        if (currentId.equals(id)) {
            currentId = null;
        }
    }

    @Override
    public void deleteAllTabs() {
        browserPresenter.detachTabView();
        destroy();
        webViews.clear();
    }

    @Override
    public void deleteAllPrivacyData() {
        deleteCookies();
        deleteLocalStorage();
        deleteWebViewDatabase();
    }

    @Override
    public void clearBrowser() {
        removeAllViews();
    }

    public void saveState(Bundle outState) {
        Bundle states = new Bundle();
        for (Map.Entry<String, DDGWebView> entry : webViews.entrySet()) {
            String key = entry.getKey();
            DDGWebView webView = entry.getValue();
            Bundle stateWebView = new Bundle();
            webView.saveState(stateWebView);
            states.putBundle(key, stateWebView);
        }
        outState.putString(EXTRA_CURRENT_ID, currentId);
        outState.putBundle(EXTRA_WEB_VIEW_STATE, states);
    }

    public void restoreState(Bundle savedInstanceState) {
        if (!savedInstanceState.containsKey(EXTRA_WEB_VIEW_STATE)) return;
        Bundle states = savedInstanceState.getBundle(EXTRA_WEB_VIEW_STATE);
        if (states == null) return;
        for (String key : states.keySet()) {
            Bundle stateWebView = states.getBundle(key);
            createNewTab(key);
            DDGWebView webView = getWebViewForTabId(key);
            if (webView == null) return;
            webView.restoreState(stateWebView);
        }
        if (!savedInstanceState.containsKey(EXTRA_CURRENT_ID)) return;
        currentId = savedInstanceState.getString(EXTRA_CURRENT_ID);
        if (currentId != null) {
            showTab(currentId);
        }
    }

    public void resume() {
        resumeWebView(getWebViewForTabId(currentId));
    }

    public void pause() {
        pauseWebView(getWebViewForTabId(currentId));
    }

    public void destroy() {
        for (DDGWebView webView : webViews.values()) {
            destroyWebView(webView);
        }
    }

    private void resumeWebView(WebView webView) {
        if (webView == null) return;
        webView.onResume();
        webView.resumeTimers();
    }

    private void pauseWebView(WebView webView) {
        if (webView == null) return;
        webView.onPause();
        webView.pauseTimers();
    }

    private void destroyWebView(WebView webView) {
        if (webView == null) return;
        webView.stopLoading();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.destroy();
        } else {
            webView = null;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private DDGWebView instantiateNewWebView(@NonNull String tabId) {
        DDGWebView webView = new DDGWebView(getContext());
        webView.setTabId(tabId);
        webView.setId(createViewId());
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new DDGWebViewClient(browserPresenter, tabId));
        webView.setWebChromeClient(new DDGWebChromeClient(browserPresenter, tabId));
        return webView;
    }

    @Nullable
    private DDGWebView getWebViewForTabId(String tabId) {
        return webViews.get(tabId);
    }

    private int createViewId() {
        if (Build.VERSION.SDK_INT >= 17) {
            return generateViewId();
        } else {
            return webViews.size() + 1;
        }
    }

    private void deleteCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
        } else {
            deleteCookiesPreApi21();
        }
    }

    @SuppressWarnings("deprecation")
    private void deleteCookiesPreApi21() {
        CookieManager.getInstance().removeAllCookie();
    }

    private void deleteLocalStorage() {
        WebStorage.getInstance().deleteAllData();
    }

    private void deleteWebViewDatabase() {
        WebViewDatabase.getInstance(getContext()).clearFormData();
        WebViewDatabase.getInstance(getContext()).clearHttpAuthUsernamePassword();
    }
}
