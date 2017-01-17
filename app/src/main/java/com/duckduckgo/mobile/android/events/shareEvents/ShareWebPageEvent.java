package com.duckduckgo.mobile.android.events.shareEvents;

public class ShareWebPageEvent extends ShareEvent {
	public String url;
	public String title;
	
	public ShareWebPageEvent(String title, String url){
		this.title = title;
		this.url = url;
	}
}
