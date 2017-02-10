package com.duckduckgo.mobile.android.events.shareEvents;


public class ShareSearchEvent extends ShareEvent {
	public final String query;
	
	public ShareSearchEvent(String query){
		this.query = query;
	}
}
