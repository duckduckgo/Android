package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;
import com.duckduckgo.mobile.android.duckduckgo.util.UrlUtils;

/**
 * Created by fgei on 5/22/17.
 */

public class BrowserPresenterImpl implements BrowserPresenter {

    private BrowserView browserView;
    private OmnibarView omnibarView;

    public BrowserPresenterImpl() {
    }

    @Override
    public void attachBrowserView(BrowserView browserView) {
        this.browserView = browserView;
    }

    @Override
    public void attachOmnibarView(OmnibarView omnibarView) {
        this.omnibarView = omnibarView;
    }

    @Override
    public void detach() {
        this.browserView = null;
        this.omnibarView = null;
    }

    @Override
    public void requestSearch(String text) {
        if(UrlUtils.isUrl(text)) {
            String url = UrlUtils.getUrlWithScheme(text);
            requestLoadUrl(url);
        } else {
            requestQuerySearch(text);
        }
    }

    private void requestLoadUrl(String url) {
        browserView.loadUrl(url);
    }

    private void requestQuerySearch(String query) {
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
    public void onPageStarted(String url) {
        omnibarView.setRefreshEnabled(true);
        String query = AppUrls.isDuckDuckGo(url) ? AppUrls.getQuery(url) : "";
        String displayedText = query != null && query.length() > 0 ? query : url;
        omnibarView.displayText(displayedText);
    }

    @Override
    public void onPageFinished(String url) {
        setNavigationMenuButtonsEnabled();
    }

    @Override
    public void onProgressChanged(int newProgress) {
        if(omnibarView == null) return;
        if(newProgress < 100) {
            omnibarView.showProgressBar();
        } else if(newProgress == 100) {
            omnibarView.hideProgressBar();
        }
        omnibarView.onProgressChanged(newProgress);
    }

    @Override
    public boolean handleBackHistory() {
        if(browserView.canGoBack()) {
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
}
