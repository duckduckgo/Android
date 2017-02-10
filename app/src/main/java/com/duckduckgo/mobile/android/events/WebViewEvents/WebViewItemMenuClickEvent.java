package com.duckduckgo.mobile.android.events.WebViewEvents;

import android.view.MenuItem;
import android.view.View;

public class WebViewItemMenuClickEvent {

    public MenuItem item;

    public WebViewItemMenuClickEvent(MenuItem item) {
        this.item = item;
    }
}
