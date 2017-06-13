package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkPreferences {

    private static final String PREF_NAME = "bookmarks";

    private SharedPreferences sharedPreferences;

    public BookmarkPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    void insert(@NonNull BookmarkEntity bookmarkEntity) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String key = getKeyForBookmark(bookmarkEntity);
        editor.putString(key, bookmarkEntity.toJson());
        editor.apply();
    }

    void update(@NonNull BookmarkEntity bookmarkEntity) {
        String key = getKeyForBookmark(bookmarkEntity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, bookmarkEntity.toJson());
        editor.apply();
    }

    void delete(@NonNull BookmarkEntity bookmarkEntity) {
        String key = getKeyForBookmark(bookmarkEntity);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(key);
        editor.apply();
    }

    List<BookmarkEntity> getAll() {
        List<BookmarkEntity> out = new ArrayList<>();
        for (Object item : sharedPreferences.getAll().values()) {
            BookmarkEntity entity = new BookmarkEntity();
            entity.fromJson(item.toString());
            out.add(entity);
        }
        return out;
    }

    void clearAll() {
        sharedPreferences.edit().clear().apply();
    }

    @NonNull
    private String getKeyForBookmark(BookmarkEntity entity) {
        return entity.getId();
    }
}
