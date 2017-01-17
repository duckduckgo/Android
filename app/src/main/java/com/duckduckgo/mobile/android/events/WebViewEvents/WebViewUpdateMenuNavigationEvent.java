package com.duckduckgo.mobile.android.events.WebViewEvents;

import java.util.HashMap;

public class WebViewUpdateMenuNavigationEvent {

    public int disableId;
    public int enableId;
    public HashMap<Integer, Boolean> newStates = new HashMap<Integer, Boolean>();

    public WebViewUpdateMenuNavigationEvent(int disableId, int enableId) {
        this.disableId = disableId;
        this.enableId = enableId;
    }

    public WebViewUpdateMenuNavigationEvent(HashMap<Integer, Boolean> newStates) {
        this.newStates = newStates;
    }
}
