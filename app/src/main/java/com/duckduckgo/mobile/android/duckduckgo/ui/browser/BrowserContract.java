package com.duckduckgo.mobile.android.duckduckgo.ui.browser;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.ui.base.BasePresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.base.BaseView;

/**
 * Created by fgei on 5/15/17.
 */

public interface BrowserContract {

    interface Presenter extends BasePresenter {
        void requestLoadUrl(String url);
        void requestQuerySearch(String query);
        void requestAssist();
        void navigateHistoryForward();
        void navigateHistoryBackward();
        void refreshCurrentPage();
        void onPageStarted(String url);
        void onPageFinished(String url);
        boolean handleBackHistory();
    }

    interface View extends BaseView<Presenter> {
        void loadUrl(@NonNull String url);
        void showTextInSearchBar(String text);
        void goBack();
        void goForward();
        boolean canGoBack();
        boolean canGoForward();
        void setCanGoBackEnabled(boolean enabled);
        void setCanGoForwardEnabled(boolean enabled);
        void reload();
        void setCanReloadEnabled(boolean enabled);
        void clearSearchBar();
        void focusSearchBar();
    }
}
