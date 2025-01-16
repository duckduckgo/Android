/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.savedsites.impl.bookmarks

import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarkFolderItem
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarkItem
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.BookmarksItemTypes
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.EmptyHint
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksAdapter.EmptySearchHint
import java.text.Collator

class BookmarksNameSortingComparator : Comparator<BookmarksItemTypes> {
    override fun compare(
        p0: BookmarksItemTypes?,
        p1: BookmarksItemTypes?,
    ): Int {
        if (p0 == null && p1 == null) return 0
        if (p0 == null) return -1
        if (p1 == null) return 1

        with(buildCollator()) {
            val titles = extractTitles(p0, p1)
            return compareFields(titles.first, titles.second)
        }
    }

    private fun extractTitles(
        p0: BookmarksItemTypes,
        p1: BookmarksItemTypes,
    ): Pair<String?, String?> {
        return Pair(extractTitle(p0), extractTitle(p1))
    }

    private fun extractTitle(bookmarkItemType: BookmarksItemTypes): String? {
        return when (bookmarkItemType) {
            is BookmarkItem -> bookmarkItemType.bookmark.title.lowercase()
            is BookmarkFolderItem -> bookmarkItemType.bookmarkFolder.name.lowercase()
            EmptyHint, EmptySearchHint -> null
        }
    }

    private fun Collator.compareFields(
        field1: String?,
        field2: String?,
    ): Int {
        if (field1 == null && field2 == null) return 0
        if (field1 == null) return -1
        if (field2 == null) return 1
        return getCollationKey(field1).compareTo(getCollationKey(field2))
    }

    private fun buildCollator(): Collator {
        val coll: Collator = Collator.getInstance()
        coll.strength = Collator.SECONDARY
        return coll
    }
}
