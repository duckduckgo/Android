package com.duckduckgo.mobile.android.util.menuItems;

import android.content.Context;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.events.ReloadEvent;
import com.duckduckgo.mobile.android.util.Item;


public class ReloadMenuItem  extends Item {

	public ReloadMenuItem(Context context){
        super(context.getResources().getString(R.string.Refresh), R.drawable.icon_reload, ItemType.SAVE);
		this.EventToFire = new ReloadEvent();
	}

}