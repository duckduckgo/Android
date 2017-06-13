package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkRepositoryImpl implements BookmarkRepository {

    private BookmarkPreferences preferences;
    private BookmarkEntityMapper mapper;

    public BookmarkRepositoryImpl(@NonNull BookmarkPreferences preferences, @NonNull BookmarkEntityMapper mapper) {
        this.preferences = preferences;
        this.mapper = mapper;
    }

    @NonNull
    @Override
    public List<Bookmark> getAll() {
        List<Bookmark> bookmarks = new ArrayList<>();
        for (Bookmark bookmark : preferences.getAll()) {
            bookmarks.add(bookmark);
        }
        return bookmarks;
    }

    @Override
    public void insert(@NonNull Bookmark bookmark) {
        preferences.insert(mapper.map(bookmark));
    }

    @Override
    public void update(@NonNull Bookmark bookmark) {
        preferences.update(mapper.map(bookmark));
    }

    @Override
    public void delete(@NonNull Bookmark bookmark) {
        preferences.delete(mapper.map(bookmark));
    }
}
