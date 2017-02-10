package com.duckduckgo.mobile.android.events;

import com.duckduckgo.mobile.android.util.SCREEN;

public class DisplayScreenEvent extends Event {

	public SCREEN screenToDisplay;
	public boolean clean;

	public DisplayScreenEvent(SCREEN screenToDisplay, boolean clean) {
		this.screenToDisplay = screenToDisplay;
		this.clean = clean;
	}
}
