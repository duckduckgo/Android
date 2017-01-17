package com.duckduckgo.mobile.android.events;

public class ShowAutoCompleteResultsEvent extends Event{

    public boolean isVisible;

    public ShowAutoCompleteResultsEvent(boolean isVisible) {
        this.isVisible = isVisible;
    }
}
