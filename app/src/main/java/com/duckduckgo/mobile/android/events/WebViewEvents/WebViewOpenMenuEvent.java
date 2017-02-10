package com.duckduckgo.mobile.android.events.WebViewEvents;

import android.view.View;

import com.duckduckgo.mobile.android.events.Event;

public class WebViewOpenMenuEvent extends Event {

    public View anchorView;

    public WebViewOpenMenuEvent(View anchorView) {
        this.anchorView = anchorView;
    };
}
