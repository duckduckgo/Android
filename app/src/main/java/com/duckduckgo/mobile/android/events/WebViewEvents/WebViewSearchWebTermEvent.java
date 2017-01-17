package com.duckduckgo.mobile.android.events.WebViewEvents;

import com.duckduckgo.mobile.android.events.Event;

public class WebViewSearchWebTermEvent extends Event {

	public String term;

	public WebViewSearchWebTermEvent(String term) {
		this.term = term;
	}
}
