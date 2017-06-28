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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.duckduckgo.app.Injector;
import com.duckduckgo.app.R;
import com.duckduckgo.app.ui.base.itemtouchhelper.OnStartDragListener;
import com.duckduckgo.app.ui.browser.BrowserPresenter;
import com.duckduckgo.app.ui.tab.TabEntity;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

public class TabSwitcherFragment extends Fragment implements TabSwitcherView, OnStartDragListener {

    public static final String TAG = TabSwitcherFragment.class.getSimpleName();

    public static TabSwitcherFragment newInstance() {
        return new TabSwitcherFragment();
    }

    @BindView(R.id.tab_switcher_title_text_view)
    TextView titletextView;

    @BindView(R.id.tab_switcher_recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.tab_switcher_done_button)
    Button doneButton;

    @BindView(R.id.tab_switcher_new_button)
    ImageButton newButton;

    @BindView(R.id.tab_swither_fire_container)
    View fireContainer;

    private Unbinder unbinder;
    private TabsAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    private BrowserPresenter browserPresenter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_tab_switcher, container, false);
        unbinder = ButterKnife.bind(this, rootView);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initUI();
        browserPresenter = Injector.injectBrowserPresenter();
    }

    @Override
    public void onResume() {
        super.onResume();
        browserPresenter.attachTabSwitcherView(this);
        browserPresenter.loadTabsSwitcherTabs();
    }

    @Override
    public void onPause() {
        browserPresenter.detachTabSwitcherView();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void showTabs(List<TabEntity> tabs) {
        adapter.setTabs(tabs);
    }

    @Override
    public void showTitle() {
        setTitle(R.string.tab_switcher_title);
    }

    @Override
    public void showNoTabsTitle() {
        setTitle(R.string.tab_switcher_title_no_tabs);
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }

    private void initUI() {
        initRecyclerView();
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browserPresenter.dismissTabSwitcher();
            }
        });
        newButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browserPresenter.openNewTab();
            }
        });
        fireContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browserPresenter.fire();
            }
        });
    }

    private void initRecyclerView() {
        adapter = new TabsAdapter(new TabsAdapter.OnTabListener() {
            @Override
            public void onClick(View v, int position) {
                browserPresenter.openTab(position);
            }

            @Override
            public void onDelete(View v, int position) {
                browserPresenter.closeTab(position);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        ItemTouchHelper.Callback callback = new TabsSwitcherTouchHelperCallback(adapter);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void setTitle(@StringRes int resId) {
        titletextView.setText(resId);
    }
}
