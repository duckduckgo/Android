package com.duckduckgo.mobile.android.duckduckgo.domain.bookmark;

import android.support.annotation.NonNull;

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

public interface BookmarkRepository {
    List<Bookmark> getAll();

    void insert(@NonNull Bookmark bookmark);

    void update(@NonNull Bookmark bookmark);

    void delete(@NonNull Bookmark bookmark);
}
