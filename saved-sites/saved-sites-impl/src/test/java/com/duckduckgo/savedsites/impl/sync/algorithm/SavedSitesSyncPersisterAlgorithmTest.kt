/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.sync.algorithm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkEntries
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkEntry
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkPage
import com.duckduckgo.savedsites.impl.sync.SyncFolderChildren
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.LOCAL_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SavedSitesSyncPersisterAlgorithmTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val timestampStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val deduplicationStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val remoteStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val localStrategy: SavedSitesSyncPersisterStrategy = mock()
    private val repository: SavedSitesRepository = mock()

    private lateinit var algorithm: SavedSitesSyncPersisterAlgorithm

    private val threeHoursAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3))
    private val twoHoursAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))

    @Before
    fun setup() {
        algorithm = RealSavedSitesSyncPersisterAlgorithm(
            FakeCrypto(),
            repository,
            deduplicationStrategy,
            timestampStrategy,
            remoteStrategy,
            localStrategy,
        )
    }

    @Test
    fun whenProcessingEntriesWithDeduplicationStrategyThenDeduplicationPersisterIsUsed() {
        val rootFolder = BookmarkFolder(
            id = SavedSitesNames.BOOKMARKS_ROOT,
            name = SavedSitesNames.BOOKMARKS_NAME,
            lastModified = twoHoursAgo,
            parentId = "",
        )
        val folder = BookmarkFolder(id = "folder1", name = "name", lastModified = twoHoursAgo, parentId = SavedSitesNames.BOOKMARKS_ROOT)
        val bookmark = Bookmark(id = "bookmark1", title = "title", url = "foo.com", lastModified = twoHoursAgo, parentId = folder.id)
        val someEntries = SyncBookmarkEntries(
            listOf(
                fromSavedSite(bookmark),
                fromBookmarkFolder(folder, listOf(bookmark.id)),
                fromBookmarkFolder(rootFolder, listOf(folder.id)),
            ),
            twoHoursAgo,
        )
        algorithm.processEntries(someEntries, DEDUPLICATION)

        verify(deduplicationStrategy).processBookmarkFolder(folder)
        verify(deduplicationStrategy).processBookmark(bookmark, folder.id)

        verifyNoInteractions(remoteStrategy)
        verifyNoInteractions(localStrategy)
        verifyNoInteractions(timestampStrategy)
    }

    @Test
    fun whenProcessingEntriesWithTimestampStrategyThenTimestampPersisterIsUsed() {
        val rootFolder = BookmarkFolder(
            id = SavedSitesNames.BOOKMARKS_ROOT,
            name = SavedSitesNames.BOOKMARKS_NAME,
            lastModified = twoHoursAgo,
            parentId = "",
        )
        val folder = BookmarkFolder(id = "folder1", name = "name", lastModified = twoHoursAgo, parentId = SavedSitesNames.BOOKMARKS_ROOT)
        val bookmark = Bookmark(id = "bookmark1", title = "title", url = "foo.com", lastModified = twoHoursAgo, parentId = folder.id)
        val someEntries = SyncBookmarkEntries(
            listOf(
                fromSavedSite(bookmark),
                fromBookmarkFolder(folder, listOf(bookmark.id)),
                fromBookmarkFolder(rootFolder, listOf(folder.id)),
            ),
            twoHoursAgo,
        )
        algorithm.processEntries(someEntries, TIMESTAMP)

        verify(timestampStrategy).processBookmarkFolder(folder)
        verify(timestampStrategy).processBookmark(bookmark, folder.id)

        verifyNoInteractions(remoteStrategy)
        verifyNoInteractions(localStrategy)
        verifyNoInteractions(deduplicationStrategy)
    }

    @Test
    fun whenProcessingEntriesWithRemoteStrategyThenRemotePersisterIsUsed() {
        val rootFolder = BookmarkFolder(
            id = SavedSitesNames.BOOKMARKS_ROOT,
            name = SavedSitesNames.BOOKMARKS_NAME,
            lastModified = twoHoursAgo,
            parentId = "",
        )
        val folder = BookmarkFolder(id = "folder1", name = "name", lastModified = twoHoursAgo, parentId = SavedSitesNames.BOOKMARKS_ROOT)
        val bookmark = Bookmark(id = "bookmark1", title = "title", url = "foo.com", lastModified = twoHoursAgo, parentId = folder.id)
        val someEntries = SyncBookmarkEntries(
            listOf(
                fromSavedSite(bookmark),
                fromBookmarkFolder(folder, listOf(bookmark.id)),
                fromBookmarkFolder(rootFolder, listOf(folder.id)),
            ),
            twoHoursAgo,
        )
        algorithm.processEntries(someEntries, REMOTE_WINS)

        verify(remoteStrategy).processBookmarkFolder(folder)
        verify(remoteStrategy).processBookmark(bookmark, folder.id)

        verifyNoInteractions(timestampStrategy)
        verifyNoInteractions(localStrategy)
        verifyNoInteractions(deduplicationStrategy)
    }

    @Test
    fun whenProcessingEntriesWithLocalStrategyThenLocalPersisterIsUsed() {
        val rootFolder = BookmarkFolder(
            id = SavedSitesNames.BOOKMARKS_ROOT,
            name = SavedSitesNames.BOOKMARKS_NAME,
            lastModified = twoHoursAgo,
            parentId = "",
        )
        val folder = BookmarkFolder(id = "folder1", name = "name", lastModified = twoHoursAgo, parentId = SavedSitesNames.BOOKMARKS_ROOT)
        val bookmark = Bookmark(id = "bookmark1", title = "title", url = "foo.com", lastModified = twoHoursAgo, parentId = folder.id)
        val someEntries = SyncBookmarkEntries(
            listOf(
                fromSavedSite(bookmark),
                fromBookmarkFolder(folder, listOf(bookmark.id)),
                fromBookmarkFolder(rootFolder, listOf(folder.id)),
            ),
            twoHoursAgo,
        )
        algorithm.processEntries(someEntries, LOCAL_WINS)

        verify(localStrategy).processBookmarkFolder(folder)
        verify(localStrategy).processBookmark(bookmark, folder.id)

        verifyNoInteractions(timestampStrategy)
        verifyNoInteractions(remoteStrategy)
        verifyNoInteractions(deduplicationStrategy)
    }

    private fun fromSavedSite(savedSite: SavedSite): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = savedSite.id,
            title = savedSite.title,
            page = SyncBookmarkPage(savedSite.url),
            folder = null,
            deleted = null,
            client_last_modified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun fromBookmarkFolder(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = bookmarkFolder.id,
            title = bookmarkFolder.name,
            folder = SyncFolderChildren(children),
            page = null,
            deleted = null,
            client_last_modified = bookmarkFolder.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    class FakeCrypto : SyncCrypto {
        override fun encrypt(text: String): String {
            return text
        }

        override fun decrypt(data: String): String {
            return data
        }
    }
}
