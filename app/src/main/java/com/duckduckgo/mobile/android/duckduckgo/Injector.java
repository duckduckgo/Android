package com.duckduckgo.mobile.android.duckduckgo;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenterImpl;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher.TabSwitcherPresenterImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fgei on 5/22/17.
 */

public class Injector {
    private static Map<String, Object> presenters = new HashMap<>();

    public static BrowserPresenter getBrowserPresenter() {
        String key = getKeyforClass(BrowserPresenter.class);
        if (!presenters.containsKey(key)) {
            presenters.put(key, instantiateBrowserPresenter());
        }
        return (BrowserPresenter) presenters.get(key);
    }

    public static TabSwitcherPresenter getTabSwitcherPresenter() {
        String key = getKeyforClass(TabSwitcherPresenter.class);
        if (!presenters.containsKey(key)) {
            presenters.put(key, instantiateTabSwitcherPresenter());
        }
        return (TabSwitcherPresenter) presenters.get(key);
    }

    private static BrowserPresenter instantiateBrowserPresenter() {
        return new BrowserPresenterImpl();
    }

    private static TabSwitcherPresenter instantiateTabSwitcherPresenter() {
        return new TabSwitcherPresenterImpl();
    }

    private static <T> String getKeyforClass(Class<T> clss) {
        return clss.getSimpleName();
    }
}
