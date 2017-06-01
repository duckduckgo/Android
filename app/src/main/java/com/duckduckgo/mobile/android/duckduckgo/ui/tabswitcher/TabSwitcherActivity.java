package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;

import com.duckduckgo.mobile.android.duckduckgo.Injector;
import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class TabSwitcherActivity extends AppCompatActivity implements TabSwitcherView {

    private static final String EXTRA_TABS = "extra_tabs";

    public static Intent getStartIntent(Context context, List<Tab> tabs) {
        Intent intent = new Intent(context, TabSwitcherActivity.class);
        intent.putExtra(EXTRA_TABS, new ArrayList<>(tabs));
        return intent;
    }

    private static List<Tab> getExtraTabs(Bundle bundle) {
        return bundle.getParcelableArrayList(EXTRA_TABS);
    }

    private static final String EXTRA_RESULT = "extra_result";
    private static final String EXTRA_RESULT_TAB_SELECTED = "extra_result_tab_selected";
    private static final String EXTRA_RESULT_TABS_REMOVED = "extra_result_tabs_removed";

    public static final String RESULT_CREATE_NEW_TAB = "result_create_new_tab";
    public static final String RESULT_TAB_SELECTED = "result_tab_selected";
    public static final String RESULT_REMOVE_ALL_TABS = "result_remove_all_tabs";
    public static final String RESULT_REMOVE_TAB = "result_remove_tab";

    public static String getResultExtra(Intent intent) {
        return intent.getStringExtra(EXTRA_RESULT);
    }

    public static int getResultTabSelected(Intent intent) {
        return intent.getIntExtra(EXTRA_RESULT_TAB_SELECTED, -1);
    }

    private static final String EXTRA_TABS_TO_REMOVE = "extra_tabs_to_remove";

    @BindView(R.id.tab_switcher_new_tab_button)
    Button newTabButton;

    @BindView(R.id.tab_switcher_done_button)
    Button doneButton;

    @BindView(R.id.tab_switcher_remove_all_button)
    Button removeAllButton;

    @BindView(R.id.tab_switcher_tabs_recycler_view)
    RecyclerView recyclerView;

    private TabSwitcherPresenter presenter;
    private List<Integer> tabsToRemove = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_switcher);
        ButterKnife.bind(this);
        presenter = Injector.getTabSwitcherPresenter();
        initUI(getExtraTabs(getIntent().getExtras()));

        if (savedInstanceState != null) {
            List<Integer> savedList = savedInstanceState.getIntegerArrayList(EXTRA_TABS_TO_REMOVE);
            if (savedList != null) {
                tabsToRemove.addAll(savedList);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        presenter.attach(this);
    }

    @Override
    protected void onPause() {
        presenter.detachView();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        presenter.closeTabSwitcher();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntegerArrayList(EXTRA_TABS_TO_REMOVE, new ArrayList<Integer>(tabsToRemove));
    }

    @Override
    public void closeTabSwitcher() {
        finish();
    }

    @Override
    public void resultCreateNewTab() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT, RESULT_CREATE_NEW_TAB);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void resultSelectTab(int tabIndex) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT, RESULT_TAB_SELECTED);
        intent.putExtra(EXTRA_RESULT_TAB_SELECTED, tabIndex);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void resultRemoveTab(int tabIndex) {
        tabsToRemove.add(tabIndex);
    }

    @Override
    public void resultRemoveAllTabs() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT, RESULT_REMOVE_ALL_TABS);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void initUI(List<Tab> tabs) {
        newTabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.createNewTab();
            }
        });
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.closeTabSwitcher();
            }
        });
        removeAllButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.closeAllTabs();
            }
        });
        initRecyclerView(tabs);
    }

    private void initRecyclerView(List<Tab> tabs) {
        TabSwitcherAdapter adapter = new TabSwitcherAdapter(tabs, new TabSwitcherAdapter.TabClickListener() {
            @Override
            public void onTabClicked(View v, Tab tab, int position) {
                presenter.openTab(tab);
            }

            @Override
            public void onTabRemoved(View v, Tab tab, int position) {
                presenter.closeTab(tab);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private boolean hasTabsToRemove() {
        return tabsToRemove != null && tabsToRemove.size() > 0;
    }

    private void addExtraRemoveTabsToIntent(Intent intent) {
        if (hasTabsToRemove()) {
            intent.putExtra(EXTRA_RESULT_TABS_REMOVED, new ArrayList<>(tabsToRemove));
        }
    }

    private void finishActivity() {
        if (hasTabsToRemove()) {
            finishActivityWithResult(RESULT_OK, new Intent());
        }
    }

    private void finishActivityWithResult(int result, Intent intent) {

    }
}
