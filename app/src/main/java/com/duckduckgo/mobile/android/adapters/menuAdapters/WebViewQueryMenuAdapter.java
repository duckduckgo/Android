package com.duckduckgo.mobile.android.adapters.menuAdapters;

import android.content.Context;

import com.duckduckgo.mobile.android.adapters.PageMenuContextAdapter;
import com.duckduckgo.mobile.android.util.menuItems.ReloadMenuItem;
import com.duckduckgo.mobile.android.util.menuItems.SearchExternalMenuItem;
import com.duckduckgo.mobile.android.util.menuItems.ShareSearchMenuItem;

public class WebViewQueryMenuAdapter extends PageMenuContextAdapter {
	private Context context;
	private String query;

	public WebViewQueryMenuAdapter(Context context, int resource,
			int textViewResourceId) {
		super(context, resource, textViewResourceId);
		this.context = context;
	}
	
	public WebViewQueryMenuAdapter(Context context, int resource,
			int textViewResourceId, String query) {
		this(context, resource, textViewResourceId);
		this.query = query;
		addMenuItems();
	}
	
	public void addMenuItems() {
		add(new ShareSearchMenuItem(context, query));
		add(new SearchExternalMenuItem(context, query));
		add(new ReloadMenuItem(context));
	}
}