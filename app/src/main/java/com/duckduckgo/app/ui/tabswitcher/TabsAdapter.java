/*
 * Copyright (c) 2017 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.ui.tabswitcher;

import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.duckduckgo.app.ui.base.itemtouchhelper.ItemTouchHelperAdapter;
import com.duckduckgo.app.ui.tab.TabEntity;

import java.util.ArrayList;
import java.util.List;

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
