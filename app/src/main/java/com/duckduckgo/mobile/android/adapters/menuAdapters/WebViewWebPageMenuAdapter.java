package com.duckduckgo.mobile.android.adapters.menuAdapters;

import android.content.Context;

import com.duckduckgo.mobile.android.adapters.PageMenuContextAdapter;
import com.duckduckgo.mobile.android.util.menuItems.ReloadMenuItem;
import com.duckduckgo.mobile.android.util.menuItems.SendToExternalBrowserMenuItem;
import com.duckduckgo.mobile.android.util.menuItems.ShareWebPageMenuItem;

public class WebViewWebPageMenuAdapter extends PageMenuContextAdapter {
	private Context context;
	private String pageUrl;

	public WebViewWebPageMenuAdapter(Context context, int resource,
			int textViewResourceId) {
		super(context, resource, textViewResourceId);
		this.context = context;
	}
	
	public WebViewWebPageMenuAdapter(Context context, int resource,
			int textViewResourceId, String pageUrl) {
		this(context, resource, textViewResourceId);
		this.pageUrl = pageUrl;
		addMenuItems();
	}
	
	public void addMenuItems() {
		add(new ShareWebPageMenuItem(context, pageUrl, pageUrl));
		add(new SendToExternalBrowserMenuItem(context, pageUrl));
		add(new ReloadMenuItem(context));
	}
}