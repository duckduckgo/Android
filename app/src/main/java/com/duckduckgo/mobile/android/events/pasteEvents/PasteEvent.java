package com.duckduckgo.mobile.android.events.pasteEvents;

import com.duckduckgo.mobile.android.events.Event;

public class PasteEvent extends Event {
	
	public String query;

	public PasteEvent(String query){
		this.query = query;
	}
	
}
