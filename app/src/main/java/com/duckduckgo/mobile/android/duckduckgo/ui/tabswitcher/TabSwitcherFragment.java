package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.duckduckgo.mobile.android.duckduckgo.Injector;
import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.base.itemtouchhelper.OnStartDragListener;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by fgei on 6/14/17.
 */

public class TabSwitcherFragment extends Fragment implements TabSwitcherView, OnStartDragListener {

    public static final String TAG = TabSwitcherFragment.class.getSimpleName();

    public static TabSwitcherFragment newInstance() {
        return new TabSwitcherFragment();
    }

    @BindView(R.id.tab_switcher_toolbar)
    Toolbar toolbar;

    @BindView(R.id.tab_switcher_recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.tab_switcher_done_button)
    Button doneButton;

    @BindView(R.id.tab_switcher_new_button)
    Button newButton;

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
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }

    private void initUI() {
        initToolbar();
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

    private void initToolbar() {
        toolbar.setTitle(R.string.tab_switcher_toolbar);
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
}
