package com.duckduckgo.mobile.android.duckduckgo;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenterImpl;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.TabManager;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherPresenterImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fgei on 5/22/17.
 */

public class Injector {
    private static Map<String, Object> instances = new HashMap<>();

    public static BrowserPresenter getBrowserPresenter() {
        String key = getKeyforClass(BrowserPresenter.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateBrowserPresenter());
        }
        return (BrowserPresenter) instances.get(key);
    }

    public static TabSwitcherPresenter getTabSwitcherPresenter() {
        String key = getKeyforClass(TabSwitcherPresenter.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateTabSwitcherPresenter());
        }
        return (TabSwitcherPresenter) instances.get(key);
    }

    public static void clearTabSwitcherPresenter() {
        clear(TabSwitcherPresenter.class);
    }

    private static TabManager getTabManager() {
        String key = getKeyforClass(TabManager.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateTabManager());
        }
        return (TabManager) instances.get(key);
    }

    private static BrowserPresenter instantiateBrowserPresenter() {
        return new BrowserPresenterImpl(getTabManager());
    }

    private static TabSwitcherPresenter instantiateTabSwitcherPresenter() {
        return new TabSwitcherPresenterImpl();
    }

    private static TabManager instantiateTabManager() {
        return new TabManager();
    }

    private static <T> void clear(Class<T> clss) {
        String key = getKeyforClass(clss);
        if (instances.containsKey(key)) {
            instances.remove(key);
        }
    }

    private static <T> String getKeyforClass(Class<T> clss) {
        return clss.getSimpleName();
    }
}
