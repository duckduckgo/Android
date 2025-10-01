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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.*
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.LOCAL_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.time.ZoneOffset

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
    private val syncSavedSitesRepository: SyncSavedSitesRepository = mock()

    private lateinit var algorithm: SavedSitesSyncPersisterAlgorithm

    private val threeHoursAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3))
    private val twoHoursAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))

    @Before
    fun setup() {
        algorithm = RealSavedSitesSyncPersisterAlgorithm(
            FakeCrypto(),
            repository,
            syncSavedSitesRepository,
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
        algorithm.processEntries(someEntries, DEDUPLICATION, threeHoursAgo)

        verify(deduplicationStrategy).processBookmarkFolder(folder, listOf(bookmark.id))
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
        algorithm.processEntries(someEntries, TIMESTAMP, threeHoursAgo)

        verify(timestampStrategy).processBookmarkFolder(folder, listOf(bookmark.id))
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
        algorithm.processEntries(someEntries, REMOTE_WINS, threeHoursAgo)

        verify(remoteStrategy).processBookmarkFolder(folder, listOf(bookmark.id))
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

        val result = algorithm.processEntries(someEntries, LOCAL_WINS, threeHoursAgo)

        assertTrue(result is Success)
        val success = result as Success
        assertFalse(success.orphans)

        verify(localStrategy).processBookmarkFolder(folder, listOf(bookmark.id))
        verify(localStrategy).processBookmark(bookmark, folder.id)

        verifyNoInteractions(timestampStrategy)
        verifyNoInteractions(remoteStrategy)
        verifyNoInteractions(deduplicationStrategy)
    }

    @Test
    fun whenProcessingOrphansThenResultIsSuccess() {
        val folder = BookmarkFolder(id = "folder1", name = "name", lastModified = twoHoursAgo, parentId = SavedSitesNames.BOOKMARKS_ROOT)
        val bookmark = Bookmark(id = "bookmark1", title = "title", url = "foo.com", lastModified = twoHoursAgo, parentId = folder.id)
        val someEntries = SyncBookmarkEntries(
            listOf(
                fromSavedSite(bookmark),
            ),
            twoHoursAgo,
        )

        whenever(repository.getSavedSite(bookmark.id)).thenReturn(null)

        algorithm.processEntries(someEntries, LOCAL_WINS, threeHoursAgo)
        val result = algorithm.processEntries(someEntries, LOCAL_WINS, threeHoursAgo)

        assertTrue(result is SyncMergeResult.Success)
        val success = result as SyncMergeResult.Success
        assertTrue(success.orphans)
    }

    private fun fromSavedSite(savedSite: SavedSite): SyncSavedSitesResponseEntry {
        return SyncSavedSitesResponseEntry(
            id = savedSite.id,
            title = savedSite.title,
            page = SyncBookmarkPage(savedSite.url),
            folder = null,
            deleted = null,
            last_modified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun fromBookmarkFolder(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
    ): SyncSavedSitesResponseEntry {
        return SyncSavedSitesResponseEntry(
            id = bookmarkFolder.id,
            title = bookmarkFolder.name,
            folder = SyncSavedSiteResponseFolder(children),
            page = null,
            deleted = null,
            last_modified = bookmarkFolder.lastModified ?: DatabaseDateFormatter.iso8601(),
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
