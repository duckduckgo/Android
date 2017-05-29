package com.duckduckgo.mobile.android.duckduckgo;

import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenterImpl;

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

    private static BrowserPresenter instantiateBrowserPresenter() {
        return new BrowserPresenterImpl();
    }

    private static <T> String getKeyforClass(T t) {
        return t.getClass().getSimpleName();
    }
}
