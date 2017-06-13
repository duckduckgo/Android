package com.duckduckgo.mobile.android.duckduckgo.domain.bookmark;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;

import java.util.List;

/**
 * Created by fgei on 6/12/17.
 */

public interface BookmarkRepository {
    List<Bookmark> getAll();

    void insert(@NonNull Bookmark bookmark);

    void update(@NonNull Bookmark bookmark);

    void delete(@NonNull Bookmark bookmark);
}
