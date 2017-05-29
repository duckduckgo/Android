package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * Created by fgei on 5/29/17.
 */

public class TabSwitcherAdapter extends RecyclerView.Adapter<TabViewHolder> {

    public interface TabClickListener {
        void onTabClicked(View v, String title, int position);
    }

    private TabClickListener tabClickListener;
    private List<String> tabs;

    public TabSwitcherAdapter(List<String> tabs, TabClickListener tabClickListener) {
        this.tabs = tabs;
        this.tabClickListener = tabClickListener;
    }

    @Override
    public TabViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return TabViewHolder.inflate(parent);
    }

    @Override
    public void onBindViewHolder(final TabViewHolder holder, int position) {
        final String title = tabs.get(position);
        holder.setTab(title);
        holder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tabClickListener.onTabClicked(v, title, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }
}
