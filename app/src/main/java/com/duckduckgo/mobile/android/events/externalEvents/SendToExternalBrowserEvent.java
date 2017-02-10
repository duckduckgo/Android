package com.duckduckgo.mobile.android.events.externalEvents;

import android.content.Context;


public class SendToExternalBrowserEvent extends ExternalEvent {
	public String url;

	public SendToExternalBrowserEvent(Context context, String url){
		this.url = url;
	}
	
}
