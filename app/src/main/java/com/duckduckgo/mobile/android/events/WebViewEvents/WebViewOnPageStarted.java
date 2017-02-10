package com.duckduckgo.mobile.android.events.WebViewEvents;

import com.duckduckgo.mobile.android.events.Event;

public class WebViewOnPageStarted extends Event {

    public String url;

    public WebViewOnPageStarted(String url) {
        this.url = url;
    }
}
