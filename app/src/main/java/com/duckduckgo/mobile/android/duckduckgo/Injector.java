package com.duckduckgo.mobile.android.duckduckgo;

import android.content.Context;

import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.BookmarkJsonEntityMapper;
import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.BookmarkSharedPreferences;
import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.SharedPreferencesBookmarkRepository;
import com.duckduckgo.mobile.android.duckduckgo.data.tab.SharedPreferencesTabRepository;
import com.duckduckgo.mobile.android.duckduckgo.data.tab.TabJsonEntityMapper;
import com.duckduckgo.mobile.android.duckduckgo.data.tab.TabSharedPreferences;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarksPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarksPresenterImpl;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenter;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.BrowserPresenterImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by fgei on 5/22/17.
 */

public class Injector {
    private static Map<String, Object> instances = new HashMap<>();

    public static void init(Context context) {
        instances.put(getKeyforClass(BookmarkSharedPreferences.class), instantiateBookmarkPreferences(context));
        instances.put(getKeyforClass(TabSharedPreferences.class), instantiateTabSharedPreferences(context));
    }

    public static TabSharedPreferences injectTabSharedPreferences() {
        return (TabSharedPreferences) instances.get(getKeyforClass(TabSharedPreferences.class));
    }

    public static BookmarkSharedPreferences injectBookmarkSharedPreferences() {
        return (BookmarkSharedPreferences) instances.get(getKeyforClass(BookmarkSharedPreferences.class));
    }

    public static BrowserPresenter injectBrowserPresenter() {
        String key = getKeyforClass(BrowserPresenter.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateBrowserPresenterImpl());
        }
        return (BrowserPresenterImpl) instances.get(key);
    }

    public static BookmarksPresenter injectBookmarkPresenter() {
        String key = getKeyforClass(BookmarksPresenter.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateBookmarksPresenterImpl());
        }
        return (BookmarksPresenter) instances.get(key);
    }

    public static void clearBookmarksPresenter() {
        instances.remove(getKeyforClass(BookmarksPresenter.class));
    }

    public static SharedPreferencesTabRepository injectSharedPreferencesTabRepository() {
        String key = getKeyforClass(SharedPreferencesTabRepository.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateSharedPreferencesTabRepository());
        }
        return (SharedPreferencesTabRepository) instances.get(key);
    }

    public static SharedPreferencesBookmarkRepository injectSharedPreferencesBookmarkRepository() {
        String key = getKeyforClass(SharedPreferencesBookmarkRepository.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateSharedPreferencesBookmarkRepository());
        }
        return (SharedPreferencesBookmarkRepository) instances.get(key);
    }

    private static BrowserPresenterImpl instantiateBrowserPresenterImpl() {
        return new BrowserPresenterImpl(injectSharedPreferencesTabRepository(), injectSharedPreferencesBookmarkRepository());
    }

    private static BookmarksPresenterImpl instantiateBookmarksPresenterImpl() {
        return new BookmarksPresenterImpl(injectSharedPreferencesBookmarkRepository());
    }

    private static SharedPreferencesTabRepository instantiateSharedPreferencesTabRepository() {
        return new SharedPreferencesTabRepository(injectTabSharedPreferences(), instantiateTabJsonEntityMapper());
    }

    private static SharedPreferencesBookmarkRepository instantiateSharedPreferencesBookmarkRepository() {
        return new SharedPreferencesBookmarkRepository(injectBookmarkSharedPreferences(), instantiateBookmarkJsonEntityMapper());
    }

    private static TabJsonEntityMapper instantiateTabJsonEntityMapper() {
        return new TabJsonEntityMapper();
    }

    private static BookmarkJsonEntityMapper instantiateBookmarkJsonEntityMapper() {
        return new BookmarkJsonEntityMapper();
    }

    private static TabSharedPreferences instantiateTabSharedPreferences(Context context) {
        return new TabSharedPreferences(context);
    }

    private static BookmarkSharedPreferences instantiateBookmarkPreferences(Context context) {
        return new BookmarkSharedPreferences(context);
    }

    private static <T> String getKeyforClass(Class<T> clss) {
        return clss.getSimpleName();
    }
}
