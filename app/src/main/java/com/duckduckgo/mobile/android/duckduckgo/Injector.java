package com.duckduckgo.mobile.android.duckduckgo;

import android.content.Context;

import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.BookmarkEntityMapper;
import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.BookmarkPreferences;
import com.duckduckgo.mobile.android.duckduckgo.data.bookmark.BookmarkRepositoryImpl;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;
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
        instances.put(getKeyforClass(BookmarkPreferences.class), instantiateBookmarkPreferences(context));
    }

    public static BookmarkPreferences injectBookmarkPreferences() {
        return (BookmarkPreferences) instances.get(getKeyforClass(BookmarkPreferences.class));
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

    public static BookmarkRepository injectBookmarkRepository() {
        String key = getKeyforClass(BookmarkRepository.class);
        if (!instances.containsKey(key)) {
            instances.put(key, instantiateBookmarkRepositoryImpl());
        }
        return (BookmarkRepository) instances.get(key);
    }

    private static BrowserPresenterImpl instantiateBrowserPresenterImpl() {
        return new BrowserPresenterImpl(injectBookmarkRepository());
    }

    private static BookmarksPresenterImpl instantiateBookmarksPresenterImpl() {
        return new BookmarksPresenterImpl(injectBookmarkRepository());
    }

    private static BookmarkRepositoryImpl instantiateBookmarkRepositoryImpl() {
        return new BookmarkRepositoryImpl(injectBookmarkPreferences(), instantiateBookmarkEntityMapper());
    }

    private static BookmarkEntityMapper instantiateBookmarkEntityMapper() {
        return new BookmarkEntityMapper();
    }

    private static BookmarkPreferences instantiateBookmarkPreferences(Context context) {
        return new BookmarkPreferences(context);
    }

    private static <T> String getKeyforClass(Class<T> clss) {
        return clss.getSimpleName();
    }
}
