package com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab;

import android.util.Log;

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

    public List<Tab> tabs;
    public Tab currentTab;

    public TabManager(OnTabListener onTabListener) {
        Log.e("tab_manager", "creator");
        tabs = new ArrayList<>();
        this.onTabListener = onTabListener;
    }

    public void createNewTab() {
        Log.e("tab_manager", "create new tab");
        Tab tab = Tab.createNewTab();
        tab.index = tabs.size();
        tabs.add(tab);
        currentTab = tab;
        onTabListener.onTabCreated(currentTab);
        onTabListener.onCurrentTabChanged(currentTab);
        printTabs("after createNewTab");
    }

    public void selectTab(int position) {
        Log.e("tab_manager", "selectTab, position: " + position);
        if (tabs.size() > position) {
            currentTab = tabs.get(position);
            onTabListener.onCurrentTabChanged(currentTab);
        }
        printTabs("after selectTab");
    }

    public void removeTabs(List<Integer> positions) {
        String out = "";
        for (int position : positions) out += position + " - ";
        Log.e("tab_manager", "removeTabs, position: " + out);
        int currentIndex = 0;
        List<Tab> newList = new ArrayList<>();

        for (Tab tab : tabs) {
            if (!positions.contains(tab.index)) {
                tab.index = currentIndex;
                newList.add(tab);
                currentIndex++;
            } else {
                onTabListener.onTabRemoved(tab);
            }
        }

        tabs.clear();
        tabs.addAll(newList);
        if (tabs.size() == 0) {
            createNewTab();
        }

        printTabs("after removeTabs");
    }

    public void printTabs(String msg) {
        Log.e("tab_manager", "------ print tabs -------- size: " + tabs.size() + " - " + msg);
        for (Tab tab : tabs) {
            Log.e("tab_manager", "index: " + tab.index + " currentUrl: " + tab.currentUrl);
        }
    }

    public void clear() {
        Log.e("tab_manager", "clear");
        tabs.clear();
    }
}
