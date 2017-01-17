package com.duckduckgo.mobile.android.events;

public class AutoCompleteResultClickEvent extends Event {

    public int position;

    public AutoCompleteResultClickEvent(int position) {
        this.position = position;
    }
}
