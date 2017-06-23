package com.duckduckgo.mobile.android.duckduckgo.ui.base.itemtouchhelper;

import android.support.v7.widget.RecyclerView;

/**
 * Created by fgei on 6/12/17.
 */

public interface ItemTouchHelperAdapter {
    boolean onItemMove(int fromPosition, int toPosition);

    void onItemDismiss(RecyclerView.ViewHolder holder, int position);
}
