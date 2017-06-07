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
        void onTabClicked(Tab tab);

        void onTabRemoved(Tab tab);
    }

    private TabClickListener tabClickListener;
    private List<Tab> tabs;

    public TabSwitcherAdapter(TabClickListener tabClickListener) {
        tabs = new ArrayList<>();
        this.tabClickListener = tabClickListener;
    }

    public void setTabs(List<Tab> tabs) {
        this.tabs.clear();
        this.tabs.addAll(tabs);
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
                tabClickListener.onTabClicked(tab);
            }
        });
        holder.setOnRemoveClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tabClickListener.onTabRemoved(tab);
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
