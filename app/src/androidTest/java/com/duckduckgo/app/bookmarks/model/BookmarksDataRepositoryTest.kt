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

package com.duckduckgo.app.bookmarks.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.*
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class BookmarksDataRepositoryTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var syncEntitiesDao: SyncEntitiesDao
    private lateinit var syncRelationsDao: SyncRelationsDao
    private lateinit var repository: SavedSitesRepository

    private val bookmark = SavedSite.Bookmark(id = "folder1", title = "title", url = "foo.com", parentId = Relation.BOOMARKS_ROOT)
    private val bookmarkEntity = Entity(entityId = bookmark.id, title = bookmark.title, url = bookmark.url, type = BOOKMARK)

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncEntitiesDao = db.syncEntitiesDao()
        syncRelationsDao = db.syncRelationsDao()
        repository = RealSavedSitesRepository(syncEntitiesDao, syncRelationsDao)
    }

    @Test
    fun whenInsertBookmarkFolderThenReturnId() = runTest {
        val id = repository.insert(BookmarkFolder(id = "folder1", name = "name", parentId = Relation.BOOMARKS_ROOT))
        assertEquals(1, id)
    }

    @Test
    fun whenInsertBookmarkThenPopulateDB() = runTest {
        repository.insert(bookmark)
        assertEquals(listOf(bookmarkEntity), repository.getBookmarks().first())
    }

    @Test
    fun whenInsertBookmarkByTitleAndUrlThenPopulateDB() = runTest {
        repository.insertBookmark(title = bookmark.title, url = bookmark.url)
        assertEquals(listOf(bookmarkEntity), repository.getBookmarks().first())
    }

    @Test
    fun whenUpdateBookmarkThenUpdateBookmarkInDB() = runTest {
        repository.insert(bookmark)

        val updatedBookmark = SavedSite.Bookmark(id = bookmark.id, title = "new title", url = "example.com", parentId = "folder2")

        repository.update(updatedBookmark)
        val bookmarkList = repository.getBookmarks().first()

        assertTrue(bookmarkList.size == 1)
        assertEquals(updatedBookmark, bookmarkList.first())
    }

    @Test
    fun whenDeleteBookmarkThenRemoveBookmarkFromDB() = runTest {
        repository.insert(bookmark)
        repository.delete(bookmark)
        assertFalse(repository.hasBookmarks())
    }

    @Test
    fun whenGetBookmarkFoldersByParentIdThenReturnBookmarkFoldersForParentId() = runTest {
        val folder = BookmarkFolder(id = "folder1", name = "name", parentId = "folder2")
        repository.insert(folder)

        val folders = repository.getFolder("folder1")

        assertEquals(folder, folders)
    }

    @Test
    fun whenGetBookmarksByParentIdThenReturnBookmarksForParentId() = runTest {
        val bookmark = SavedSite.Bookmark(id = "folder1", title = "name", url = "foo.com", parentId = "folder2")
        repository.insert(bookmark)
        repository.getFolderContent("folder2").test {
            val result = awaitItem()
            assertTrue(result.first.size == 1)
            assertTrue(result.second.isEmpty())
            assertEquals(result.first.first(), bookmark)
        }
    }

    // @Test
    // fun whenGetBookmarkFolderBranchThenReturnFoldersAndBookmarksForBranch() = runTest {
    //     val parentFolder = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
    //     val childFolder = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
    //     val childBookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)
    //
    //     repository.insertFolderBranch(BookmarkFolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder)))
    //
    //     val branch = repository.getBookmarkFolderBranch(BookmarkFolder(parentFolder.id, parentFolder.name, parentFolder.parentId))
    //
    //     assertEquals(listOf(childBookmark), branch.bookmarkEntities)
    //     assertEquals(listOf(parentFolder, childFolder), branch.bookmarkFolderEntities)
    // }

    // @Test
    // fun whenGetBranchFoldersThenReturnFolderListForBranch() = runTest {
    //     val parentFolderEntity = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
    //     val childFolderEntity = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
    //
    //     bookmarkFoldersDao.insertList(listOf(parentFolderEntity, childFolderEntity))
    //
    //     val parentFolder = BookmarkFolder(parentFolderEntity.id, parentFolderEntity.name, parentFolderEntity.parentId)
    //     val childFolder = BookmarkFolder(childFolderEntity.id, childFolderEntity.name, childFolderEntity.parentId)
    //
    //     val list = repository.getBranchFolders(parentFolder)
    //
    //     assertEquals(listOf(parentFolder, childFolder), list)
    // }

    // @Test
    // fun whenDeleteFolderBranchThenDeletedBookmarksAndFoldersAreNoLongerInDB() = runTest {
    //     val parentFolderEntity = Entity(title = "name", url ="", type = FOLDER)
    //     val childFolderEntity = Entity(title = "another name", url ="", type = FOLDER)
    //     val childBookmark = Entity(title = "title", url = "www.example.com", type = BOOKMARK)
    //
    //     val branchToDelete = BookmarkFolderBranch(listOf(childBookmark), listOf(parentFolderEntity, childFolderEntity))
    //
    //     repository.insertFolderBranch(branchToDelete)
    //     val deletedBranch = repository.deleteFolderBranch(BookmarkFolder(parentFolderEntity.id, parentFolderEntity.name, parentFolderEntity.parentId))
    //
    //     assertEquals(branchToDelete, deletedBranch)
    //     assertFalse(bookmarksDao.hasBookmarks())
    //     assertTrue(bookmarkFoldersDao.getBookmarkFoldersSync().isEmpty())
    // }
    //
    // @Test
    // fun whenFetchBookmarksAndFoldersWithNullParentIdThenFetchAllBookmarksAndFolders() = runTest {
    //     val folder = BookmarkFolder(id = 1, name = "name", parentId = 0)
    //     val bookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)
    //
    //     repository.insert(folder)
    //     bookmarksDao.insert(bookmark)
    //
    //     val bookmarksAndFolders = repository.fetchBookmarksAndFolders(null)
    //
    //     assertEquals(
    //         listOf(SavedSite.Bookmark(bookmark.id, bookmark.title ?: "", bookmark.url, bookmark.parentId)),
    //         bookmarksAndFolders.first().first,
    //     )
    //     assertEquals(listOf(BookmarkFolder(folder.id, folder.name, folder.parentId, numBookmarks = 1)), bookmarksAndFolders.first().second)
    // }
    //
    // @Test
    // fun whenFetchBookmarksAndFoldersWithParentIdThenFetchBookmarksAndFoldersForParentId() = runTest {
    //     val folder = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
    //     val anotherFolder = BookmarkFolderEntity(id = 2, name = "another name", parentId = 0)
    //     val childFolder = BookmarkFolderEntity(id = 3, name = "child folder", parentId = 2)
    //
    //     val bookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)
    //     val anotherBookmark = BookmarkEntity(id = 2, title = "another title", url = "www.foo.com", parentId = 2)
    //
    //     repository.insertFolderBranch(BookmarkFolderBranch(listOf(bookmark, anotherBookmark), listOf(folder, anotherFolder, childFolder)))
    //
    //     val bookmarksAndFolders = repository.fetchBookmarksAndFolders(2)
    //
    //     assertEquals(
    //         listOf(SavedSite.Bookmark(anotherBookmark.id, anotherBookmark.title ?: "", anotherBookmark.url, anotherBookmark.parentId)),
    //         bookmarksAndFolders.first().first,
    //     )
    //     assertEquals(listOf(BookmarkFolder(childFolder.id, childFolder.name, childFolder.parentId)), bookmarksAndFolders.first().second)
    // }
    //
    // @Test
    // fun whenBuildFlatStructureThenReturnFolderListWithDepth() = runTest {
    //     val parentFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
    //     val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1)
    //     val folder = BookmarkFolder(id = 3, name = "folder name", parentId = 0)
    //
    //     bookmarkFoldersDao.insertList(
    //         listOf(
    //             BookmarkFolderEntity(parentFolder.id, parentFolder.name, parentFolder.parentId),
    //             BookmarkFolderEntity(childFolder.id, childFolder.name, childFolder.parentId),
    //             BookmarkFolderEntity(folder.id, folder.name, folder.parentId),
    //         ),
    //     )
    //
    //     val flatStructure = repository.getFlatFolderStructure(3, null, "Bookmarks")
    //
    //     val items = listOf(
    //         BookmarkFolderItem(0, BookmarkFolder(0, "Bookmarks", -1), false),
    //         BookmarkFolderItem(1, parentFolder, false),
    //         BookmarkFolderItem(2, childFolder, false),
    //         BookmarkFolderItem(1, folder, true),
    //     )
    //
    //     assertEquals(items, flatStructure)
    // }
    //
    // @Test
    // fun whenBuildFlatStructureThenReturnFolderListWithDepthWithoutCurrentFolderBranch() = runTest {
    //     val parentFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
    //     val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1)
    //     val folder = BookmarkFolder(id = 3, name = "folder name", parentId = 0)
    //
    //     bookmarkFoldersDao.insertList(
    //         listOf(
    //             BookmarkFolderEntity(parentFolder.id, parentFolder.name, parentFolder.parentId),
    //             BookmarkFolderEntity(childFolder.id, childFolder.name, childFolder.parentId),
    //             BookmarkFolderEntity(folder.id, folder.name, folder.parentId),
    //         ),
    //     )
    //
    //     val flatStructure = repository.getFlatFolderStructure(3, parentFolder, "Bookmarks")
    //
    //     val items = listOf(
    //         BookmarkFolderItem(0, BookmarkFolder(0, "Bookmarks", -1), false),
    //         BookmarkFolderItem(1, folder, true),
    //     )
    //
    //     assertEquals(items, flatStructure)
    // }
    //
    // @Test
    // fun whenBookmarksRequestedAndAvailableThenReturnListOfBookmarks() = runTest {
    //     val firstBookmark = SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0)
    //     val secondBookmark = SavedSite.Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 0)
    //
    //     repository.insert(firstBookmark)
    //     repository.insert(secondBookmark)
    //
    //     val bookmarks = repository.bookmarks()
    //
    //     assertEquals(listOf(firstBookmark, secondBookmark), bookmarks.first())
    // }
    //
    // @Test
    // fun whenBookmarksRequestedAndNoneAvailableThenReturnEmptyList() = runTest {
    //     val bookmarks = repository.bookmarks()
    //
    //     assertEquals(emptyList<SavedSite.Bookmark>(), bookmarks.first())
    // }
    //
    // @Test
    // fun whenBookmarkByUrlRequestedAndAvailableThenReturnBookmark() = runTest {
    //     val firstBookmark = SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0)
    //     val secondBookmark = SavedSite.Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 0)
    //
    //     repository.insert(firstBookmark)
    //     repository.insert(secondBookmark)
    //
    //     val bookmark = repository.getBookmark("www.website.com")
    //
    //     assertEquals(firstBookmark, bookmark)
    // }
    //
    // @Test
    // fun whenBookmarkByUrlRequestedAndNotAvailableThenReturnNull() = runTest {
    //     val firstBookmark = SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0)
    //     val secondBookmark = SavedSite.Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 0)
    //
    //     repository.insert(firstBookmark)
    //     repository.insert(secondBookmark)
    //
    //     val bookmark = repository.getBookmark("www.test.com")
    //
    //     assertNull(bookmark)
    // }
    //
    // @Test
    // fun whenBookmarkByUrlRequestedAndNoBookmarksAvailableThenReturnNull() = runTest {
    //     val bookmark = repository.getBookmark("www.test.com")
    //
    //     assertNull(bookmark)
    // }
    //
    // @Test
    // fun whenHasBookmarksRequestedAndNoBookmarksAvailableThenReturnFalse() = runTest {
    //     val result = repository.hasBookmarks()
    //
    //     assertFalse(result)
    // }
    //
    // @Test
    // fun whenHasBookmarksRequestedAndBookmarksAvailableThenReturnTrue() = runTest {
    //     repository.insert(SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0))
    //
    //     val result = repository.hasBookmarks()
    //
    //     assertTrue(result)
    // }
    //
    // @Test
    // fun whenDeleteAllFoldersAndBookmarksThenDeleteAllFoldersAndBookmarks() = runTest {
    //     val parentFolderEntity = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
    //     val childFolderEntity = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
    //     val childBookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)
    //
    //     val branchToDelete = BookmarkFolderBranch(listOf(childBookmark), listOf(parentFolderEntity, childFolderEntity))
    //
    //     repository.insertFolderBranch(branchToDelete)
    //
    //     repository.deleteAll()
    //
    //     assertFalse(bookmarksDao.hasBookmarks())
    //     assertTrue(bookmarkFoldersDao.getBookmarkFoldersSync().isEmpty())
    // }
}
