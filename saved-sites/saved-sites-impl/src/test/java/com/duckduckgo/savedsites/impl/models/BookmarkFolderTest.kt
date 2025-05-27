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

package com.duckduckgo.savedsites.impl.models

import com.duckduckgo.savedsites.api.models.BookmarkFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkFolderTest {

    @Test
    fun whenFolderHasNoItemsThenIsEmptyReturnsTrue() {
        val folder = BookmarkFolder(name = "Test", parentId = "root", numFolders = 0, numBookmarks = 0)
        assertTrue(folder.isEmpty())
    }

    @Test
    fun whenFolderHasFoldersThenIsEmptyReturnsFalse() {
        val folder = BookmarkFolder(name = "Test", parentId = "root", numFolders = 1, numBookmarks = 0)
        assertFalse(folder.isEmpty())
    }

    @Test
    fun whenFolderHasBookmarksThenIsEmptyReturnsFalse() {
        val folder = BookmarkFolder(name = "Test", parentId = "root", numFolders = 0, numBookmarks = 2)
        assertFalse(folder.isEmpty())
    }

    @Test
    fun whenFolderHasItemsThenGetTotalItemsReturnsSum() {
        val folder = BookmarkFolder(name = "Test", parentId = "root", numFolders = 3, numBookmarks = 4)
        assertEquals(7, folder.getTotalItems())
    }
}
