package com.duckduckgo.mobile.android.util.menuItems;

import android.content.Context;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.events.shareEvents.ShareSearchEvent;
import com.duckduckgo.mobile.android.util.Item;

public class ShareSearchMenuItem extends Item {
	
	public ShareSearchMenuItem(Context context, String query){
		super(context.getResources().getString(R.string.action_share), android.R.drawable.ic_menu_share, ItemType.SHARE);
		this.EventToFire = new ShareSearchEvent(query);
	}

}
