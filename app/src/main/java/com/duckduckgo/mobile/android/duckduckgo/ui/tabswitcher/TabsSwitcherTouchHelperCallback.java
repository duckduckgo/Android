package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.widget.RecyclerView;

import com.duckduckgo.mobile.android.duckduckgo.ui.base.itemtouchhelper.ItemTouchHelperAdapter;
import com.duckduckgo.mobile.android.duckduckgo.ui.base.itemtouchhelper.ItemTouchHelperCallback;

/**
 * Created by fgei on 6/23/17.
 */

public class TabsSwitcherTouchHelperCallback extends ItemTouchHelperCallback<ItemTouchHelperAdapter> {

    private static final float ALPHA_FULL = 1.0f;

    public TabsSwitcherTouchHelperCallback(ItemTouchHelperAdapter adapter) {
        super(adapter);
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true;
    }

    @Override
    public boolean isItemMoveEnabled() {
        return false;
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return false;
    }
}
