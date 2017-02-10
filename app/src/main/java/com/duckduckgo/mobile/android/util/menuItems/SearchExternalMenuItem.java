package com.duckduckgo.mobile.android.util.menuItems;

import android.content.Context;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.events.externalEvents.SearchExternalEvent;
import com.duckduckgo.mobile.android.util.Item;

public class SearchExternalMenuItem extends Item {
	
	public SearchExternalMenuItem(Context context, String query){
		super(context.getResources().getString(R.string.action_view_external), android.R.drawable.ic_menu_rotate, ItemType.EXTERNAL);
		this.EventToFire = new SearchExternalEvent(query);
	}

}
