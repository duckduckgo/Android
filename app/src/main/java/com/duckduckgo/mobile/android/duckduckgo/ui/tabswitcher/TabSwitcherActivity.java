package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
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
    public static final String RESULT_DISMISSED = "extra_result_dismissed";

    public static String getResultExtra(Intent intent) {
        return intent.getStringExtra(EXTRA_RESULT);
    }

    public static int getResultTabSelected(Intent intent) {
        return intent.getIntExtra(EXTRA_RESULT_TAB_SELECTED, -1);
    }

    public static boolean hasResultTabDeleted(Intent intent) {
        return intent.hasExtra(EXTRA_RESULT_TABS_REMOVED);
    }

    public static List<Integer> getResultTabDeleted(Intent intent) {
        return intent.getIntegerArrayListExtra(EXTRA_RESULT_TABS_REMOVED);
    }

    private static final String EXTRA_TABS_TO_REMOVE = "extra_tabs_to_remove";

    @BindView(R.id.tab_switcher_toolbar)
    Toolbar toolbar;

    @BindView(R.id.tab_switcher_new_tab_button)
    Button newTabButton;

    @BindView(R.id.tab_switcher_done_button)
    Button doneButton;

    @BindView(R.id.tab_switcher_remove_all_button)
    Button removeAllButton;

    @BindView(R.id.tab_switcher_tabs_recycler_view)
    RecyclerView recyclerView;

    private TabSwitcherPresenter presenter;
    private TabSwitcherAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_switcher);
        ButterKnife.bind(this);
        presenter = Injector.getTabSwitcherPresenter();

        initUI();

        if (savedInstanceState != null) {
            List<Tab> savedListTabs = savedInstanceState.getParcelableArrayList(EXTRA_TABS);
            List<Tab> savedListTabsToRemove = savedInstanceState.getParcelableArrayList(EXTRA_TABS_TO_REMOVE);
            presenter.restoreState(savedListTabs, savedListTabsToRemove);
        } else {
            presenter.load(getExtraTabs(getIntent().getExtras()));
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
        outState.putParcelableArrayList(EXTRA_TABS, new ArrayList<Parcelable>(presenter.saveStateTabs()));
        outState.putParcelableArrayList(EXTRA_TABS_TO_REMOVE, new ArrayList<Parcelable>(presenter.saveStateTabsToRemove()));
    }

    @Override
    public void loadTabs(@NonNull List<Tab> tabs) {
        adapter.setTabs(tabs);
    }

    @Override
    public void closeTabSwitcher(@NonNull List<Integer> positionToDelete) {
        Intent resultIntent = getIntentForTabsToDelete(positionToDelete);
        resultIntent.putExtra(EXTRA_RESULT, RESULT_DISMISSED);
        finishActivityWithResultOKAndExtraIntent(resultIntent);
    }

    @Override
    public void resultCreateNewTab(@NonNull List<Integer> positionToDelete) {
        Intent resultIntent = getIntentForTabsToDelete(positionToDelete);
        resultIntent.putExtra(EXTRA_RESULT, RESULT_CREATE_NEW_TAB);
        finishActivityWithResultOKAndExtraIntent(resultIntent);
    }

    @Override
    public void resultSelectTab(int selectedIndex, @NonNull List<Integer> positionToDelete) {
        Intent resultIntent = getIntentForTabsToDelete(positionToDelete);
        resultIntent.putExtra(EXTRA_RESULT, RESULT_TAB_SELECTED);
        resultIntent.putExtra(EXTRA_RESULT_TAB_SELECTED, selectedIndex);
        finishActivityWithResultOKAndExtraIntent(resultIntent);
    }

    @Override
    public void resultRemoveAllTabs() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_RESULT, RESULT_REMOVE_ALL_TABS);
        finishActivityWithResultOKAndExtraIntent(resultIntent);
    }

    private void initUI() {
        toolbar.setTitle(R.string.tab_switcher_toolbar);
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
        initRecyclerView();
    }

    private void initRecyclerView() {
        adapter = new TabSwitcherAdapter(new TabSwitcherAdapter.TabClickListener() {
            @Override
            public void onTabClicked(Tab tab) {
                presenter.openTab(tab);
            }

            @Override
            public void onTabRemoved(Tab tab) {
                presenter.closeTab(tab);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private Intent getIntentForTabsToDelete(List<Integer> positionToDelete) {
        Intent intent = new Intent();
        if (positionToDelete != null && !positionToDelete.isEmpty()) {
            intent.putExtra(EXTRA_RESULT_TABS_REMOVED, new ArrayList<>(positionToDelete));
        }
        return intent;
    }

    private void finishActivityWithResultOKAndExtraIntent(Intent resultIntent) {
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
