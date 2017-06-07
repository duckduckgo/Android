package com.duckduckgo.mobile.android.duckduckgo.ui.browser.web;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.WebView;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.TabView;

/**
 * Created by fgei on 5/29/17.
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
}
