package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

/**
 * Created by fgei on 5/22/17.
 */

public interface BrowserPresenter {
    void attachBrowserView(BrowserView browserView);
    void attachOmnibarView(OmnibarView omnibarView);
    void detach();
    void requestSearch(String text);
    void requestAssist();
    void navigateHistoryForward();
    void navigateHistoryBackward();
    void refreshCurrentPage();
    void onPageStarted(String url);
    void onPageFinished(String url);
    void onProgressChanged(int newProgress);
    boolean handleBackHistory();
}
