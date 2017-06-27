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

import android.support.v7.widget.RecyclerView;

import com.duckduckgo.app.ui.base.itemtouchhelper.ItemTouchHelperAdapter;
import com.duckduckgo.app.ui.base.itemtouchhelper.ItemTouchHelperCallback;

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
