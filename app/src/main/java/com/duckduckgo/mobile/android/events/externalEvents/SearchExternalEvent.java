package com.duckduckgo.mobile.android.events.externalEvents;

public class SearchExternalEvent extends ExternalEvent {
	public final String query;
	
	public SearchExternalEvent(String query){
		this.query = query;
	}
}
