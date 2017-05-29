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

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class TabSwitcherActivity extends AppCompatActivity implements TabSwitcherView {

    public static Intent getStartIntent(Context context) {
        return new Intent(context, TabSwitcherActivity.class);
    }

    private static final String EXTRA_RESULT = "extra_result";
    private static final String EXTRA_RESULT_TAB_SELECTED = "extra_result_tab_selected";

    public static final String RESULT_CREATE_NEW_TAB = "result_create_new_tab";
    public static final String RESULT_TAB_SELECTED = "result_tab_selected";

    public static String getResultExtra(Intent intent) {
        return intent.getStringExtra(EXTRA_RESULT);
    }

    public static int getResultTabSelected(Intent intent) {
        return intent.getIntExtra(EXTRA_RESULT_TAB_SELECTED, -1);
    }

    @BindView(R.id.tab_switcher_new_tab_button)
    Button newTabButton;

    @BindView(R.id.tab_switcher_done_button)
    Button doneButton;

    @BindView(R.id.tab_switcher_tabs_recycler_view)
    RecyclerView recyclerView;

    private TabSwitcherPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tab_switcher);
        ButterKnife.bind(this);
        presenter = Injector.getTabSwitcherPresenter();
        initUI();
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
    public void closeTabSwitcher() {
        finish();
    }

    @Override
    public void createNewTab() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT, RESULT_CREATE_NEW_TAB);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void selectTab(int position) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT, RESULT_TAB_SELECTED);
        intent.putExtra(EXTRA_RESULT_TAB_SELECTED, position);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void initUI() {
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
        initRecyclerView();
    }

    private void initRecyclerView() {
        List<String> tabs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tabs.add("tab: " + i);
        }
        TabSwitcherAdapter adapter = new TabSwitcherAdapter(tabs, new TabSwitcherAdapter.TabClickListener() {
            @Override
            public void onTabClicked(View v, String title, int position) {
                presenter.openTab(position);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
}
