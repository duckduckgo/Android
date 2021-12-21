/*
 * Copyright (c) 2021 DuckDuckGo
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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.*
import com.duckduckgo.app.global.db.AppDatabase
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class BookmarksDataRepositoryTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var bookmarkFoldersDao: BookmarkFoldersDao
    private lateinit var bookmarksDao: BookmarksDao
    private lateinit var repository: BookmarksDataRepository

    private val bookmark = SavedSite.Bookmark(id = 1, title = "title", url = "foo.com", parentId = 0)
    private val bookmarkEntity = BookmarkEntity(id = bookmark.id, title = bookmark.title, url = bookmark.url, parentId = bookmark.parentId)

    private var mockBookmarkFoldersDao: BookmarkFoldersDao = mock()

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookmarkFoldersDao = db.bookmarkFoldersDao()
        bookmarksDao = db.bookmarksDao()
        repository = BookmarksDataRepository(bookmarkFoldersDao, bookmarksDao, db)
    }

    @Test
    fun whenInsertBookmarkFolderThenReturnId() = runTest {
        val id = repository.insert(BookmarkFolder(id = 1, name = "name", parentId = 0))
        assertEquals(1, id)
    }

    @Test
    fun whenInsertBookmarkThenPopulateDB() = runTest {
        repository.insert(bookmark)
        assertEquals(listOf(bookmarkEntity), bookmarksDao.getBookmarks().first())
    }

    @Test
    fun whenInsertBookmarkByTitleAndUrlThenPopulateDB() = runTest {
        repository.insert(title = bookmark.title, url = bookmark.url, parentId = bookmark.parentId)
        assertEquals(listOf(bookmarkEntity), bookmarksDao.getBookmarks().first())
    }

    @Test
    fun whenUpdateBookmarkThenUpdateBookmarkInDB() = runTest {
        repository.insert(bookmark)

        val updatedBookmark = SavedSite.Bookmark(id = bookmark.id, title = "new title", url = "example.com", parentId = 2)

        repository.update(updatedBookmark)
        val bookmarkList = bookmarksDao.getBookmarks().first()

        assertTrue(bookmarkList.size == 1)
        assertEquals(BookmarkEntity(updatedBookmark.id, updatedBookmark.title, updatedBookmark.url, updatedBookmark.parentId), bookmarkList.first())
    }

    @Test
    fun whenDeleteBookmarkThenRemoveBookmarkFromDB() = runTest {
        repository.insert(bookmark)
        repository.delete(bookmark)
        assertFalse(bookmarksDao.hasBookmarks())
    }

    @Test
    fun whenGetBookmarkFoldersByParentIdThenReturnBookmarkFoldersForParentId() = runTest {
        val folder = BookmarkFolder(id = 1, name = "name", parentId = 2)
        repository.insert(folder)
        val folders = repository.getBookmarkFoldersByParentId(2)

        assertTrue(folders.size == 1)
        assertEquals(folder, folders.first())
    }

    @Test
    fun whenGetBookmarksByParentIdThenReturnBookmarksForParentId() = runTest {
        val bookmark = SavedSite.Bookmark(id = 1, title = "name", url = "foo.com", parentId = 2)
        repository.insert(bookmark)
        val bookmarks = repository.getBookmarksByParentId(2)

        assertTrue(bookmarks.size == 1)
        assertEquals(BookmarkEntity(bookmark.id, bookmark.title, bookmark.url, bookmark.parentId), bookmarks.first())
    }

    @Test
    fun whenUpdateBookmarkFolderThenUpdateBookmarkFolderCalled() = runTest {
        repository = BookmarksDataRepository(mockBookmarkFoldersDao, bookmarksDao, db)
        val bookmarkFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
        repository.update(bookmarkFolder)

        verify(mockBookmarkFoldersDao).update(BookmarkFolderEntity(id = bookmarkFolder.id, name = bookmarkFolder.name, parentId = bookmarkFolder.parentId))
    }

    @Test
    fun whenGetBookmarkFolderBranchThenReturnFoldersAndBookmarksForBranch() = runTest {
        val parentFolder = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val childFolder = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
        val childBookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)

        repository.insertFolderBranch(BookmarkFolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder)))

        val branch = repository.getBookmarkFolderBranch(BookmarkFolder(parentFolder.id, parentFolder.name, parentFolder.parentId))

        assertEquals(listOf(childBookmark), branch.bookmarkEntities)
        assertEquals(listOf(parentFolder, childFolder), branch.bookmarkFolderEntities)
    }

    @Test
    fun whenGetBranchFoldersThenReturnFolderListForBranch() = runTest {
        val parentFolderEntity = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val childFolderEntity = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)

        bookmarkFoldersDao.insertList(listOf(parentFolderEntity, childFolderEntity))

        val parentFolder = BookmarkFolder(parentFolderEntity.id, parentFolderEntity.name, parentFolderEntity.parentId)
        val childFolder = BookmarkFolder(childFolderEntity.id, childFolderEntity.name, childFolderEntity.parentId)

        val list = repository.getBranchFolders(parentFolder)

        assertEquals(listOf(parentFolder, childFolder), list)
    }

    @Test
    fun whenDeleteFolderBranchThenDeletedBookmarksAndFoldersAreNoLongerInDB() = runTest {
        val parentFolderEntity = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val childFolderEntity = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
        val childBookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)

        val branchToDelete = BookmarkFolderBranch(listOf(childBookmark), listOf(parentFolderEntity, childFolderEntity))

        repository.insertFolderBranch(branchToDelete)
        val deletedBranch = repository.deleteFolderBranch(BookmarkFolder(parentFolderEntity.id, parentFolderEntity.name, parentFolderEntity.parentId))

        assertEquals(branchToDelete, deletedBranch)
        assertFalse(bookmarksDao.hasBookmarks())
        assertTrue(bookmarkFoldersDao.getBookmarkFoldersSync().isEmpty())
    }

    @Test
    fun whenFetchBookmarksAndFoldersWithNullParentIdThenFetchAllBookmarksAndFolders() = runTest {
        val folder = BookmarkFolder(id = 1, name = "name", parentId = 0)
        val bookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)

        repository.insert(folder)
        bookmarksDao.insert(bookmark)

        val bookmarksAndFolders = repository.fetchBookmarksAndFolders(null)

        assertEquals(listOf(SavedSite.Bookmark(bookmark.id, bookmark.title ?: "", bookmark.url, bookmark.parentId)), bookmarksAndFolders.first().first)
        assertEquals(listOf(BookmarkFolder(folder.id, folder.name, folder.parentId, numBookmarks = 1)), bookmarksAndFolders.first().second)
    }

    @Test
    fun whenFetchBookmarksAndFoldersWithParentIdThenFetchBookmarksAndFoldersForParentId() = runTest {
        val folder = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val anotherFolder = BookmarkFolderEntity(id = 2, name = "another name", parentId = 0)
        val childFolder = BookmarkFolderEntity(id = 3, name = "child folder", parentId = 2)

        val bookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)
        val anotherBookmark = BookmarkEntity(id = 2, title = "another title", url = "www.foo.com", parentId = 2)

        repository.insertFolderBranch(BookmarkFolderBranch(listOf(bookmark, anotherBookmark), listOf(folder, anotherFolder, childFolder)))

        val bookmarksAndFolders = repository.fetchBookmarksAndFolders(2)

        assertEquals(listOf(SavedSite.Bookmark(anotherBookmark.id, anotherBookmark.title ?: "", anotherBookmark.url, anotherBookmark.parentId)), bookmarksAndFolders.first().first)
        assertEquals(listOf(BookmarkFolder(childFolder.id, childFolder.name, childFolder.parentId)), bookmarksAndFolders.first().second)
    }

    @Test
    fun whenBuildFlatStructureThenReturnFolderListWithDepth() = runTest {
        val parentFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
        val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1)
        val folder = BookmarkFolder(id = 3, name = "folder name", parentId = 0)

        bookmarkFoldersDao.insertList(
            listOf(
                BookmarkFolderEntity(parentFolder.id, parentFolder.name, parentFolder.parentId),
                BookmarkFolderEntity(childFolder.id, childFolder.name, childFolder.parentId),
                BookmarkFolderEntity(folder.id, folder.name, folder.parentId)
            )
        )

        val flatStructure = repository.getFlatFolderStructure(3, null, "Bookmarks")

        val items = listOf(
            BookmarkFolderItem(0, BookmarkFolder(0, "Bookmarks", -1), false),
            BookmarkFolderItem(1, parentFolder, false),
            BookmarkFolderItem(2, childFolder, false),
            BookmarkFolderItem(1, folder, true)
        )

        assertEquals(items, flatStructure)
    }

    @Test
    fun whenBuildFlatStructureThenReturnFolderListWithDepthWithoutCurrentFolderBranch() = runTest {
        val parentFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
        val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1)
        val folder = BookmarkFolder(id = 3, name = "folder name", parentId = 0)

        bookmarkFoldersDao.insertList(
            listOf(
                BookmarkFolderEntity(parentFolder.id, parentFolder.name, parentFolder.parentId),
                BookmarkFolderEntity(childFolder.id, childFolder.name, childFolder.parentId),
                BookmarkFolderEntity(folder.id, folder.name, folder.parentId)
            )
        )

        val flatStructure = repository.getFlatFolderStructure(3, parentFolder, "Bookmarks")

        val items = listOf(
            BookmarkFolderItem(0, BookmarkFolder(0, "Bookmarks", -1), false),
            BookmarkFolderItem(1, folder, true)
        )

        assertEquals(items, flatStructure)
    }

    @Test
    fun whenBookmarksRequestedAndAvailableThenReturnListOfBookmarks() = runTest {
        val firstBookmark = SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0)
        val secondBookmark = SavedSite.Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 0)

        repository.insert(firstBookmark)
        repository.insert(secondBookmark)

        val bookmarks = repository.bookmarks()

        assertEquals(listOf(firstBookmark, secondBookmark), bookmarks.first())
    }

    @Test
    fun whenBookmarksRequestedAndNoneAvailableThenReturnEmptyList() = runTest {
        val bookmarks = repository.bookmarks()

        assertEquals(emptyList<SavedSite.Bookmark>(), bookmarks.first())
    }

    @Test
    fun whenBookmarkByUrlRequestedAndAvailableThenReturnBookmark() = runTest {
        val firstBookmark = SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0)
        val secondBookmark = SavedSite.Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 0)

        repository.insert(firstBookmark)
        repository.insert(secondBookmark)

        val bookmark = repository.getBookmark("www.website.com")

        assertEquals(firstBookmark, bookmark)
    }

    @Test
    fun whenBookmarkByUrlRequestedAndNotAvailableThenReturnNull() = runTest {
        val firstBookmark = SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0)
        val secondBookmark = SavedSite.Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 0)

        repository.insert(firstBookmark)
        repository.insert(secondBookmark)

        val bookmark = repository.getBookmark("www.test.com")

        assertNull(bookmark)
    }

    @Test
    fun whenBookmarkByUrlRequestedAndNoBookmarksAvailableThenReturnNull() = runTest {
        val bookmark = repository.getBookmark("www.test.com")

        assertNull(bookmark)
    }

    @Test
    fun whenHasBookmarksRequestedAndNoBookmarksAvailableThenReturnFalse() = runTest {
        val result = repository.hasBookmarks()

        assertFalse(result)
    }

    @Test
    fun whenHasBookmarksRequestedAndBookmarksAvailableThenReturnTrue() = runTest {
        repository.insert(SavedSite.Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0))

        val result = repository.hasBookmarks()

        assertTrue(result)
    }
}
