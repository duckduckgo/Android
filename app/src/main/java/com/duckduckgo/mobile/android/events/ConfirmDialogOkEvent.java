package com.duckduckgo.mobile.android.events;

public class ConfirmDialogOkEvent extends Event {

    public int action;

    public ConfirmDialogOkEvent(int action) {
        this.action = action;
    }
}
