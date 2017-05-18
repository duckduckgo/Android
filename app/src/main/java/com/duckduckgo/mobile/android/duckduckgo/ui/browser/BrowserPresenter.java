package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import com.duckduckgo.mobile.android.duckduckgo.util.AppUrls;

/**
 * Created by fgei on 5/15/17.
 */

public class BrowserPresenter implements BrowserContract.Presenter {

    private BrowserContract.View browserView;
    private String displayedText;

    public BrowserPresenter(BrowserContract.View browserView) {
        this.browserView = browserView;
        this.browserView.setPresenter(this);
    }

    @Override
    public void start() {
        // TODO: 5/15/17 load previous session or load new page
        browserView.setCanGoBackEnabled(false);
        browserView.setCanGoForwardEnabled(false);
        browserView.setCanReloadEnabled(false);
    }

    @Override
    public void requestLoadUrl(String url) {
        displayedText = url;
        browserView.loadUrl(url);
    }

    @Override
    public void requestQuerySearch(String query) {
        displayedText = query;
        String url = AppUrls.getSearchUrl(query);
        requestLoadUrl(url);
    }

    @Override
    public void requestAssist() {
        browserView.clearSearchBar();
        browserView.focusSearchBar();
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
        browserView.setCanReloadEnabled(true);
    }

    @Override
    public void onPageFinished(String url) {
        checkIfHistoryNavigationPresent();
        displayedText = AppUrls.isDuckDuckGo(url) ? AppUrls.getQuery(url) : url;
        browserView.showTextInSearchBar(displayedText);
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
        browserView.setCanGoBackEnabled(canGoBack);
    }

    private void setCanGoForward(boolean canGoForward) {
        browserView.setCanGoForwardEnabled(canGoForward);
    }

    private void checkIfHistoryNavigationPresent() {
        setCanGoBack();
        setCanGoForward();
    }
}
