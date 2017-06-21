package com.duckduckgo.mobile.android.duckduckgo.ui.omnibar;

import android.support.annotation.NonNull;

/**
 * Created by fgei on 5/22/17.
 */

public interface OmnibarView {
    void displayText(@NonNull String text);

    void clearText();

    void clearFocus();

    void requestSearchFocus();

    void setBackEnabled(boolean enabled);

    void setForwardEnabled(boolean enabled);

    void setRefreshEnabled(boolean enabled);

    void setEditing(boolean editing);

    void setDeleteAllTextButtonVisible(boolean visible);

    void showProgressBar();

    void hideProgressBar();

    void onProgressChanged(int newProgress);
}
