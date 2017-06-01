package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 5/29/17.
 */

public class TabSwitcherAdapter extends RecyclerView.Adapter<TabViewHolder> {

    public interface TabClickListener {
        void onTabClicked(View v, Tab tab, int position);

        void onTabRemoved(View v, Tab tab, int position);
    }

    private TabClickListener tabClickListener;
    private List<Tab> tabs;

    public TabSwitcherAdapter(List<Tab> tabs, TabClickListener tabClickListener) {
        this.tabs = tabs;
        this.tabClickListener = tabClickListener;
    }

    @Override
    public TabViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return TabViewHolder.inflate(parent);
    }

    @Override
    public void onBindViewHolder(final TabViewHolder holder, int position) {
        final Tab tab = tabs.get(position);
        holder.setTab(tab);
        holder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tabClickListener.onTabClicked(v, tab, holder.getAdapterPosition());
            }
        });
        holder.setOnRemoveClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tabClickListener.onTabRemoved(v, tab, holder.getAdapterPosition());
                removeTab(holder.getAdapterPosition());

            }
        });
    }

    private void removeTab(int position) {
        List<Tab> newTabs = new ArrayList<>();
        newTabs.addAll(tabs);
        newTabs.remove(position);
        TabsDiffCallback tabsDiffCallback = new TabsDiffCallback(tabs, newTabs);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(tabsDiffCallback);
        tabs.clear();
        tabs.addAll(newTabs);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }
}
