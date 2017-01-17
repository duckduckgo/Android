package com.duckduckgo.mobile.android.listener;
import android.content.DialogInterface;

import com.duckduckgo.mobile.android.adapters.PageMenuContextAdapter;
import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.util.Item;

public class ExecuteActionOnClickListener implements DialogInterface.OnClickListener {
	private final PageMenuContextAdapter contextAdapter;

	public ExecuteActionOnClickListener(PageMenuContextAdapter contextAdapter) {
		this.contextAdapter = contextAdapter;
	}

	public void onClick(DialogInterface dialog, int item) {
		Item clickedItem = ((Item) contextAdapter.getItem(item));
		BusProvider.getInstance().post(clickedItem.EventToFire);
	}
}