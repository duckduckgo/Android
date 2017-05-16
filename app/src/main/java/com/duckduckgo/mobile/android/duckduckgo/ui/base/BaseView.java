package com.duckduckgo.mobile.android.duckduckgo.ui.base;

/**
 * Created by fgei on 5/15/17.
 */

public interface BaseView<P extends BasePresenter> {
    void setPresenter(P presenter);
}
