package com.duckduckgo.mobile.android.events;

import com.duckduckgo.mobile.android.util.SESSIONTYPE;

public class RequestOpenWebPageEvent extends Event {

	public String url;
	public SESSIONTYPE sessionType;

	public RequestOpenWebPageEvent(String url, SESSIONTYPE sessionType) {
		this.url = url;
		this.sessionType = sessionType;
	}
}
