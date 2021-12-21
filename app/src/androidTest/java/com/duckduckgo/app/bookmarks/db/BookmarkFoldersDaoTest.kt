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

package com.duckduckgo.app.bookmarks.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.global.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BookmarkFoldersDaoTest {

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi @get:Rule var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var bookmarksDao: BookmarksDao
    private lateinit var bookmarkFoldersDao: BookmarkFoldersDao

    private val bookmarkFolderList =
        listOf(
            BookmarkFolderEntity(id = 1, name = "name", parentId = 0),
            BookmarkFolderEntity(id = 2, name = "another name", parentId = 1),
            BookmarkFolderEntity(id = 3, name = "subfolder name", parentId = 1))

    private val bookmarksList =
        listOf(
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1),
            BookmarkEntity(
                id = 2, title = "another title", url = "www.foo.example.com", parentId = 3),
            BookmarkEntity(
                id = 3, title = "bookmark title", url = "www.bar.example.com", parentId = 3))

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        bookmarkFoldersDao = db.bookmarkFoldersDao()
        bookmarksDao = db.bookmarksDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenBookmarkFolderAddedThenItIsInTheList() = runBlocking {
        val bookmarkFolderEntity = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        bookmarkFoldersDao.insert(bookmarkFolderEntity)
        val list = bookmarkFoldersDao.getBookmarkFolders().first()
        assertEquals(
            listOf(
                BookmarkFolder(
                    bookmarkFolderEntity.id,
                    bookmarkFolderEntity.name,
                    bookmarkFolderEntity.parentId)),
            list)
    }

    @Test
    fun whenBookmarkFoldersAddedThenTheyAreInTheList() = runBlocking {
        bookmarkFoldersDao.insertList(bookmarkFolderList)
        val list = bookmarkFoldersDao.getBookmarkFoldersSync()
        assertEquals(bookmarkFolderList, list)
    }

    @Test
    fun whenBookmarksAndBookmarkFoldersAddedThenNumBookmarksAndNumFoldersPopulated() = runBlocking {
        bookmarkFoldersDao.insertList(bookmarkFolderList)
        bookmarksDao.insertList(bookmarksList)

        val list = bookmarkFoldersDao.getBookmarkFolders().first()
        assertEquals(3, list.size)

        assertEquals(list[0].numFolders, 2)
        assertEquals(list[0].numBookmarks, 1)

        assertEquals(list[1].numFolders, 0)
        assertEquals(list[1].numBookmarks, 0)

        assertEquals(list[2].numFolders, 0)
        assertEquals(list[2].numBookmarks, 2)
    }

    @Test
    fun whenBookmarksAndBookmarkFoldersAddedThenNumBookmarksAndNumFoldersPopulatedByParentId() =
        runBlocking {
        bookmarkFoldersDao.insertList(bookmarkFolderList)
        bookmarksDao.insertList(bookmarksList)

        var list = bookmarkFoldersDao.getBookmarkFoldersByParentId(0).first()
        assertEquals(list, bookmarkFoldersDao.getBookmarkFoldersByParentIdSync(0))
        assertEquals(1, list.size)

        assertEquals(list[0].numFolders, 2)
        assertEquals(list[0].numBookmarks, 1)

        list = bookmarkFoldersDao.getBookmarkFoldersByParentId(1).first()
        assertEquals(list, bookmarkFoldersDao.getBookmarkFoldersByParentIdSync(1))
        assertEquals(2, list.size)

        assertEquals(list[0].numFolders, 0)
        assertEquals(list[0].numBookmarks, 0)

        assertEquals(list[1].numFolders, 0)
        assertEquals(list[1].numBookmarks, 2)
    }

    @Test
    fun whenInInitialStateThenTheBookmarkFoldersAreEmpty() = runBlocking {
        val list = bookmarkFoldersDao.getBookmarkFoldersByParentId(0).first()
        assertNotNull(list)
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenBookmarkFolderDeletedThenItIsNoLongerInTheList() = runBlocking {
        val bookmarkFolder = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        bookmarkFoldersDao.insert(bookmarkFolder)
        bookmarkFoldersDao.delete(listOf(bookmarkFolder))
        val list = bookmarkFoldersDao.getBookmarkFoldersByParentId(0).first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenBookmarkFolderUpdatedThenUpdateBookmarkFolder() = runBlocking {
        bookmarkFoldersDao.insert(BookmarkFolderEntity(id = 1, name = "name", parentId = 0))

        val bookmarkFolderEntity = BookmarkFolderEntity(id = 1, name = "updated name", parentId = 0)
        bookmarkFoldersDao.update(bookmarkFolderEntity)

        val list = bookmarkFoldersDao.getBookmarkFoldersSync()
        assertEquals(1, list.size)
        assertEquals(bookmarkFolderEntity, list.first())
    }
}
