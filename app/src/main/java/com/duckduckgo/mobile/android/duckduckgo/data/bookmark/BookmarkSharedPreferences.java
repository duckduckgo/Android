package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import android.content.Context;

import com.duckduckgo.mobile.android.duckduckgo.data.base.BaseSharedPreferencesDataStore;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkSharedPreferences extends BaseSharedPreferencesDataStore<BookmarkJsonEntity> {

    private static final String PREF_NAME = "bookmarks";

    public BookmarkSharedPreferences(Context context) {
        super(context, BookmarkJsonEntity.class);
    }

    @Override
    public String getFileName() {
        return PREF_NAME;
    }
}
