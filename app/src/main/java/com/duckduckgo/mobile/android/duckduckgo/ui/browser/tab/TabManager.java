package com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 5/30/17.
 */

public class TabManager {

    public List<Tab> tabs;
    public Tab currentTab;

    public TabManager() {
        tabs = new ArrayList<>();
    }

    public void createNewTab() {
        Tab tab = new Tab();
        tab.index = tabs.size();
        tabs.add(tab);
        currentTab = tab;
    }

    public void selectTab(int position) {
        if (tabs.size() > position) {
            currentTab = tabs.get(position);
        }
    }

    public void removeTab(int position) {
        if (tabs.size() > position) {
            tabs.remove(position);
            for (Tab tab : tabs) {
                if (tab.index > position) {
                    tab.index--;
                }
            }
            int newPosition = tabs.size() > position ? position : --position;
            currentTab = tabs.get(newPosition);
        }
    }

    public void clear() {
        tabs.clear();
    }
}
