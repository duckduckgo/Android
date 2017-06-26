package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import android.support.annotation.NonNull;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.BookmarkRepository;

import java.util.ArrayList;
import java.util.List;

/**
 *    Copyright 2017 DuckDuckGo
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

public class SharedPreferencesBookmarkRepository implements BookmarkRepository {

    private BookmarkSharedPreferences preferences;
    private BookmarkJsonEntityMapper mapper;

    public SharedPreferencesBookmarkRepository(@NonNull BookmarkSharedPreferences preferences, @NonNull BookmarkJsonEntityMapper mapper) {
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
