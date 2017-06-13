package com.duckduckgo.mobile.android.duckduckgo.ui.navigator;

import android.support.v4.app.Fragment;

import com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks.BookmarksActivity;

/**
 * Created by fgei on 6/12/17.
 */

public class Navigator {

    public static void navigateToBookmarks(Fragment fromFragment, int requestCode) {
        fromFragment.startActivityForResult(BookmarksActivity.getStartIntent(fromFragment.getContext()), requestCode);
    }
}
