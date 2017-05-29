package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;
import com.duckduckgo.mobile.android.duckduckgo.util.UrlUtils;

/**
 * Created by fgei on 5/22/17.
 */

public class BrowserPresenterImpl implements BrowserPresenter {

    private static final int PROGRESS_COMPLETE = 100;

    private BrowserView browserView;
    private OmnibarView omnibarView;

    public BrowserPresenterImpl() {
    }

    @Override
    public void attachBrowserView(@NonNull BrowserView browserView) {
        this.browserView = browserView;
    }

    @Override
    public void attachOmnibarView(@NonNull OmnibarView omnibarView) {
        this.omnibarView = omnibarView;
    }

    @Override
    public void detachViews() {
        browserView = null;
        omnibarView = null;
    }

    @Override
    public void requestSearch(@Nullable String text) {
        if (text == null) return;
        if (UrlUtils.isUrl(text)) {
            String url = UrlUtils.getUrlWithScheme(text);
            requestLoadUrl(url);
        } else {
            requestQuerySearch(text);
        }
    }

    private void requestLoadUrl(@NonNull String url) {
        browserView.loadUrl(url);
    }

    private void requestQuerySearch(@NonNull String query) {
        String url = AppUrls.getSearchUrl(query);
        requestLoadUrl(url);
    }

    @Override
    public void requestAssist() {
        omnibarView.clearText();
        omnibarView.requestFocus();
    }

    @Override
    public void navigateHistoryForward() {
        browserView.goForward();
    }

    @Override
    public void navigateHistoryBackward() {
        browserView.goBack();
    }

    @Override
    public void refreshCurrentPage() {
        browserView.reload();
    }

    @Override
    public void onPageStarted(@Nullable String url) {
        omnibarView.setRefreshEnabled(true);
        String validUrl = url == null ? "" : url;
        displayTextForUrl(validUrl);
    }

    @Override
    public void onPageFinished(@Nullable String url) {
        setNavigationMenuButtonsEnabled();
    }

    @Override
    public void onProgressChanged(int newProgress) {
        if (omnibarView == null) return;
        if (newProgress < PROGRESS_COMPLETE) {
            omnibarView.showProgressBar();
        } else if (newProgress == PROGRESS_COMPLETE) {
            omnibarView.hideProgressBar();
        }
        omnibarView.onProgressChanged(newProgress);
    }

    @Override
    public boolean handleBackHistory() {
        if (browserView.canGoBack()) {
            navigateHistoryBackward();
            return true;
        }
        return false;
    }

    private void setCanGoBack() {
        setCanGoBack(browserView.canGoBack());
    }

    private void setCanGoForward() {
        setCanGoForward(browserView.canGoForward());
    }

    private void setCanGoBack(boolean canGoBack) {
        omnibarView.setBackEnabled(canGoBack);
    }

    private void setCanGoForward(boolean canGoForward) {
        omnibarView.setForwardEnabled(canGoForward);
    }

    private void setNavigationMenuButtonsEnabled() {
        setCanGoBack();
        setCanGoForward();
    }

    private void displayText(String textToDisplay) {
        omnibarView.displayText(textToDisplay);
    }

    private void displayTextForUrl(String url) {
        if (AppUrls.isDuckDuckGo(url)) {
            displayTextForDuckDuckGoUrl(url);
        } else {
            displayText(url);
        }
    }

    private void displayTextForDuckDuckGoUrl(String url) {
        String textToDisplay = url;
        String query = AppUrls.getQuery(url);
        if (query != null && query.length() > 0) {
            textToDisplay = query;
            displayText(textToDisplay);
        }
    }
}
