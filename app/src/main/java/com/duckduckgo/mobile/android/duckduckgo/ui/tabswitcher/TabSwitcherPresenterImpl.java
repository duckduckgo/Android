package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

/**
 * Created by fgei on 5/29/17.
 */

public class TabSwitcherPresenterImpl implements TabSwitcherPresenter {

    private TabSwitcherView tabSwitcherView;

    public TabSwitcherPresenterImpl() {
    }

    @Override
    public void attach(TabSwitcherView tabSwitcherView) {
        this.tabSwitcherView = tabSwitcherView;
    }

    @Override
    public void detachView() {
        tabSwitcherView = null;
    }

    @Override
    public void createNewTab() {
        tabSwitcherView.createNewTab();
    }

    @Override
    public void openTab(int position) {
        tabSwitcherView.selectTab(position);
    }

    @Override
    public void openSettings() {

    }

    @Override
    public void closeTabSwitcher() {
        tabSwitcherView.closeTabSwitcher();
    }

    @Override
    public void actionFire() {

    }
}
