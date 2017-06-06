package com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 5/30/17.
 */

public class TabManager {

    public interface OnTabListener {
        void onTabCreated(Tab tabCreated);

        void onTabRemoved(Tab tabRemoved);

        void onCurrentTabChanged(Tab currentTab);
    }

    private OnTabListener onTabListener;

    private List<Tab> tabs;
    private Tab currentTab;

    public TabManager() {
        tabs = new ArrayList<>();
    }

    public void setOnTabListener(OnTabListener onTabListener) {
        this.onTabListener = onTabListener;
    }

    public List<Tab> getTabs() {
        return tabs;
    }

    public void setTabs(List<Tab> tabs) {
        this.tabs = tabs;
    }

    public void createNewTab() {
        Tab tab = Tab.createNewTab();
        tab.index = tabs.size();
        tabs.add(tab);
        if (onTabListener != null) {
            onTabListener.onTabCreated(tab);
        }
        setCurrentTab(tab);
    }

    public void selectTab(int position) {
        if (tabs.size() > position) {
            Tab tab = tabs.get(position);
            setCurrentTab(tab);
        }
    }

    public void removeTabs(List<Integer> positions) {
        int currentIndex = 0;
        List<Tab> newList = new ArrayList<>();

        int currentTabIndex = currentTab.index;

        for (Tab tab : tabs) {
            if (!positions.contains(tab.index)) {
                tab.index = currentIndex;
                newList.add(tab);
                currentIndex++;
            } else {
                if (onTabListener != null) {
                    onTabListener.onTabRemoved(tab);
                }
                if (tab == currentTab) {
                    currentTabIndex = currentIndex;
                }
            }
        }

        tabs.clear();
        tabs.addAll(newList);
        if (tabs.size() == 0) {
            createNewTab();
        } else if (!tabs.contains(currentTab)) {
            selectTab(currentTabIndex);
        }
    }

    public void removeAll() {
        for (Tab tab : tabs) {
            if (onTabListener != null) {
                onTabListener.onTabRemoved(tab);
            }
        }
        tabs.clear();
    }

    public Tab getCurrentTab() {
        return currentTab;
    }

    private void setCurrentTab(Tab tab) {
        currentTab = tab;
        if (onTabListener != null) {
            onTabListener.onCurrentTabChanged(currentTab);
        }
    }
}
