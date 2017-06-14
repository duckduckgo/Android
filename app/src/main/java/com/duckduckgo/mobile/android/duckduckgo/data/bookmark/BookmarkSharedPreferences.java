package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkSharedPreferences {

    private static final String PREF_NAME = "bookmarks";

    private SharedPreferences sharedPreferences;

    public BookmarkSharedPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    void insert(@NonNull BookmarkJsonEntity bookmarkJsonEntity) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String key = getKeyForBookmark(bookmarkJsonEntity);
        editor.putString(key, bookmarkJsonEntity.toJson());
        editor.apply();
    }

    void update(@NonNull BookmarkJsonEntity bookmarkJsonEntity) {
        String key = getKeyForBookmark(bookmarkJsonEntity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, bookmarkJsonEntity.toJson());
        editor.apply();
    }

    void delete(@NonNull BookmarkJsonEntity bookmarkJsonEntity) {
        String key = getKeyForBookmark(bookmarkJsonEntity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(key);
        editor.apply();
    }

    List<BookmarkJsonEntity> getAll() {
        List<BookmarkJsonEntity> out = new ArrayList<>();
        for (Object item : sharedPreferences.getAll().values()) {
            BookmarkJsonEntity entity = new BookmarkJsonEntity();
            entity.fromJson(item.toString());
            out.add(entity);
        }
        return out;
    }

    void clearAll() {
        sharedPreferences.edit().clear().apply();
    }

    @NonNull
    private String getKeyForBookmark(BookmarkJsonEntity entity) {
        return entity.getId();
    }
}
