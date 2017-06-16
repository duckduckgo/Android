package com.duckduckgo.mobile.android.duckduckgo.ui.tab;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.webkit.WebView;

/**
 * Created by fgei on 6/14/17.
 */

public class DDGWebView extends WebView implements TabView {
    public DDGWebView(Context context) {
        super(context);
    }

    public DDGWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DDGWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DDGWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @NonNull
    @Override
    public String getTabId() {
        return (String) getTag();
    }

    @Override
    public void setTabId(@NonNull String tabId) {
        setTag(tabId);
    }
}
