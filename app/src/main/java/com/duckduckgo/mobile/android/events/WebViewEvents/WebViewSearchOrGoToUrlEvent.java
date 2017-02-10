package com.duckduckgo.mobile.android.events.WebViewEvents;

import com.duckduckgo.mobile.android.events.Event;
import com.duckduckgo.mobile.android.util.SESSIONTYPE;

public class WebViewSearchOrGoToUrlEvent extends Event {

	public String text;
	public SESSIONTYPE sessionType;

	public WebViewSearchOrGoToUrlEvent(String text, SESSIONTYPE sessionType) {
		this.text = text;
		this.sessionType = sessionType;
	}
}
