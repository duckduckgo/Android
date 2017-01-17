package com.duckduckgo.mobile.android.events;

import android.view.View;

public class OverflowButtonClickEvent extends Event {

    public View anchor;

    public OverflowButtonClickEvent(View anchor) {
        this.anchor = anchor;
    }
}
