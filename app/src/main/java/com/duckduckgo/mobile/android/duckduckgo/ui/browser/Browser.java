package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

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
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.duckduckgo.mobile.android.duckduckgo.Injector;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.DDGWebView;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.web.DDGWebChromeClient;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.web.DDGWebViewClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fgei on 6/14/17.
 */

public class Browser extends FrameLayout implements BrowserView {

    private static final String EXTRA_WEB_VIEW_STATE = "extra_web_view_state";

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
        browserPresenter = Injector.injectBrowserPresenter();
    }

    @Override
    public void createNewTab(@NonNull String id) {
        webViews.put(id, instantiateNewWebView(id));
    }

    @Override
    public void showTab(@NonNull String id) {
        //browserPresenter.detachTabView();
        removeAllViews();
        currentId = id;
        DDGWebView webView = webViews.get(id);
        addView(webView);
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
    public void deleteAll() {
        browserPresenter.detachTabView();
        destroy();
        webViews.clear();
    }

    public void saveState(Bundle outState) {
        Bundle state = new Bundle();
        for(Map.Entry<String, DDGWebView> entry : webViews.entrySet()) {
            Bundle webViewState = new Bundle();
            entry.getValue().saveState(webViewState);
            state.putBundle(entry.getKey(), webViewState);
        }
        outState.putBundle(EXTRA_WEB_VIEW_STATE, state);
    }

    public void restoreState(Bundle savedInstanceState) {
        if(!savedInstanceState.containsKey(EXTRA_WEB_VIEW_STATE)) return;
        Bundle state = savedInstanceState.getBundle(EXTRA_WEB_VIEW_STATE);
        if(state == null) return;
        for(String key : state.keySet()) {
            createNewTab(key);
            DDGWebView webView = getWebViewForId(key);
            if(webView != null) {
                webView.restoreState(state.getBundle(EXTRA_WEB_VIEW_STATE));
            }
        }
    }

    public void resume() {
        browserPresenter.attachTabView(getWebViewForId(currentId));
        resumeWebView(getWebViewForId(currentId));
    }

    public void pause() {
        browserPresenter.detachTabView();
        pauseWebView(getWebViewForId(currentId));
    }

    public void destroy() {
        for (DDGWebView webView : webViews.values()) {
            destroyWebView(webView);
        }
    }

    private void resumeWebView(WebView webView) {
        if(webView == null) return;
        webView.onResume();
        webView.resumeTimers();
    }

    private void pauseWebView(WebView webView) {
        if(webView == null) return;
        webView.onPause();
        webView.pauseTimers();
    }

    private void destroyWebView(WebView webView) {
        if(webView == null) return;
        webView.stopLoading();
        webView.destroy();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private DDGWebView instantiateNewWebView(@NonNull String id) {
        DDGWebView webView = new DDGWebView(getContext());
        webView.setTabId(id);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new DDGWebViewClient(browserPresenter));
        webView.setWebChromeClient(new DDGWebChromeClient(browserPresenter));
        return webView;
    }

    //@Nullable
    private DDGWebView getWebViewForId(String id) {
        //if(id == null) return null;
        return webViews.get(id);
    }


}
