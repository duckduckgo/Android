package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkEntityMapper {
    public BookmarkEntityMapper() {
    }

    public BookmarkEntity map(Bookmark bookmark) {
        return new BookmarkEntity(bookmark);
    }

    public List<BookmarkEntity> map(List<? extends Bookmark> bookmarks) {
        List<BookmarkEntity> entities = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            entities.add(new BookmarkEntity(bookmark));
        }
        return entities;
    }
}
