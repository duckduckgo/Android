package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import android.content.Context;

import com.duckduckgo.mobile.android.duckduckgo.data.base.SharedPreferencesDataStore;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkSharedPreferences extends SharedPreferencesDataStore<BookmarkJsonEntity> {

    private static final String PREF_NAME = "bookmarks";

    public BookmarkSharedPreferences(Context context) {
        super(context, PREF_NAME, new EntityCreator<BookmarkJsonEntity>() {
            @Override
            public BookmarkJsonEntity create() {
                return new BookmarkJsonEntity();
            }
        });
    }
}
