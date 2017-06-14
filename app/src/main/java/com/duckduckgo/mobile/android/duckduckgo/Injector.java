package com.duckduckgo.mobile.android.duckduckgo;

import android.content.Context;

import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.BookmarkJsonEntityMapper;
import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.BookmarkSharedPreferences;
import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.SharedPreferencesBookmarkRepository;
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
    }

    public static BookmarkSharedPreferences injectBookmarkPreferences() {
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

    public static SharedPreferencesBookmarkRepository injectSharedPreferencesBookmarkRepository() {
        String key = getKeyforClass(SharedPreferencesBookmarkRepository.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateSharedPreferencesBookmarkRepository());
        }
        return (SharedPreferencesBookmarkRepository) instances.get(key);
    }

    private static BrowserPresenterImpl instantiateBrowserPresenterImpl() {
        return new BrowserPresenterImpl(injectSharedPreferencesBookmarkRepository());
    }

    private static BookmarksPresenterImpl instantiateBookmarksPresenterImpl() {
        return new BookmarksPresenterImpl(injectSharedPreferencesBookmarkRepository());
    }

    private static SharedPreferencesBookmarkRepository instantiateSharedPreferencesBookmarkRepository() {
        return new SharedPreferencesBookmarkRepository(injectBookmarkPreferences(), instantiateBookmarkJsonEntityMapper());
    }

    private static BookmarkJsonEntityMapper instantiateBookmarkJsonEntityMapper() {
        return new BookmarkJsonEntityMapper();
    }

    private static BookmarkSharedPreferences instantiateBookmarkPreferences(Context context) {
        return new BookmarkSharedPreferences(context);
    }

    private static <T> String getKeyforClass(Class<T> clss) {
        return clss.getSimpleName();
    }
}
