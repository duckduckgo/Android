package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.duckduckgo.mobile.android.duckduckgo.ui.base.itemtouchhelper.ItemTouchHelperAdapter;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/14/17.
 */

public class TabsAdapter extends RecyclerView.Adapter<TabViewHolder> implements ItemTouchHelperAdapter {

    public interface OnTabListener {
        void onClick(View v, int position);

        void onDelete(View v, int position);
    }

    private OnTabListener onTabListener;
    private List<TabEntity> tabs = new ArrayList<>();

    public TabsAdapter(OnTabListener onTabListener) {
        this.onTabListener = onTabListener;
    }

    public void setTabs(List<TabEntity> tabs) {
        updateList(tabs);
    }

    @Override
    public TabViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return TabViewHolder.inflate(parent, new TabViewHolder.OnTabListener() {
            @Override
            public void onTabSelected(View v, int position) {
                onTabListener.onClick(v, position);
            }

            @Override
            public void onTabDeleted(View v, int position) {
                onTabListener.onDelete(v, position);
            }
        });
    }

    @Override
    public void onBindViewHolder(TabViewHolder holder, int position) {
        TabEntity tabEntity = tabs.get(position);
        holder.setTab(tabEntity);
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        return false;
    }

    @Override
    public void onItemDismiss(RecyclerView.ViewHolder holder, int position) {
        onTabListener.onDelete(holder.itemView, position);
    }

    private void updateList(List<TabEntity> newList) {
        TabDiffCallback diffCallback = new TabDiffCallback(tabs, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        tabs.clear();
        tabs.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }
}
