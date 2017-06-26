package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.support.annotation.NonNull;

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

public interface BookmarksPresenter {
    void attachView(BookmarksView bookmarksView);

    void detachView();

    void restore(boolean isEditing);

    void load();

    void edit();

    void dismiss();

    void bookmarkSelected(int position);

    void bookmarkDeleted(int position);

    void bookmarksMoved(int fromPosition, int toPosition);

    void saveEditedBookmark(@NonNull BookmarkEntity bookmarkEntity);
}
