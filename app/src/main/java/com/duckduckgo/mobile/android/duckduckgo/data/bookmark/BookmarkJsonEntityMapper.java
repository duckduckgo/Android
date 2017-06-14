package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public class BookmarkJsonEntityMapper {

    public BookmarkJsonEntity map(Bookmark bookmark) {
        return new BookmarkJsonEntity(bookmark);
    }

    public List<BookmarkJsonEntity> map(List<? extends Bookmark> bookmarks) {
        List<BookmarkJsonEntity> entities = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            entities.add(new BookmarkJsonEntity(bookmark));
        }
        return entities;
    }
}
