package com.duckduckgo.mobile.android.duckduckgo.ui.main;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarkEntity;

/**
 * Created by fgei on 6/14/17.
 */

public interface MainView {
    void showConfirmSaveBookmark(@NonNull BookmarkEntity bookmarkEntity);

    void navigateToBookmarks();
}
