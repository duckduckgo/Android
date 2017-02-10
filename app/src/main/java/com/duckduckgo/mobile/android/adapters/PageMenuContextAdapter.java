package com.duckduckgo.mobile.android.adapters;

import java.util.HashMap;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.util.Item;
import com.duckduckgo.mobile.android.util.Item.ItemType;

public class PageMenuContextAdapter extends ArrayAdapter<Item> {
	
	Context context;
	static HashMap<ItemType, Item> dialogItems;
	
	private void initItemMap() {
        dialogItems.put(ItemType.SHARE, new Item(context.getResources().getString(R.string.action_share), android.R.drawable.ic_menu_save, ItemType.SHARE));
        dialogItems.put(ItemType.EXTERNAL, new Item(context.getResources().getString(R.string.action_view_external), android.R.drawable.ic_menu_save, ItemType.EXTERNAL));
	}
	
	protected Item getItem(ItemType itemType){
		return dialogItems.get(itemType);
	}

	public PageMenuContextAdapter(Context context, int resource,
			int textViewResourceId) {
		super(context, resource, textViewResourceId);
		this.context = context;
		
		// avoid creating static item map again
		if(dialogItems == null) {
			dialogItems = new HashMap<ItemType, Item>();
			initItemMap();
		}
	}
	
	public View getView(int position, View convertView, android.view.ViewGroup parent) {		
		View v = super.getView(position, convertView, parent);
		return v;
	}

}
