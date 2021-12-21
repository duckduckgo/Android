/*
 * Copyright (c) 2018 DuckDuckGo
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
import com.duckduckgo.app.global.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BookmarksDaoTest {

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi @get:Rule var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: BookmarksDao

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.bookmarksDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenBookmarkDeleteThenItIsNoLongerInTheList() = runBlocking {
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        dao.insert(bookmark)
        dao.delete(bookmark)
        val list = dao.getBookmarks().first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenBookmarksDeletedThenTheyAreNoLongerInTheList() = runBlocking {
        val bookmarks =
            listOf(
                BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0),
                BookmarkEntity(
                    id = 2, title = "another title", url = "www.foo.example.com", parentId = 0))
        dao.insertList(bookmarks)
        dao.deleteList(bookmarks)
        val list = dao.getBookmarks().first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenBookmarkAddedThenItIsInList() = runBlocking {
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        dao.insert(bookmark)
        val list = dao.getBookmarks().first()
        assertEquals(listOf(bookmark), list)
    }

    @Test
    fun whenBookmarksAddedThenTheyAreInTheList() = runBlocking {
        val bookmarks =
            listOf(
                BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0),
                BookmarkEntity(
                    id = 2, title = "another title", url = "www.foo.example.com", parentId = 0))
        dao.insertList(bookmarks)
        val list = dao.getBookmarks().first()
        assertEquals(bookmarks, list)
    }

    @Test
    fun whenBookmarksAddedThenTheyAreInTheListByParentId() = runBlocking {
        val bookmarks =
            listOf(
                BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1),
                BookmarkEntity(
                    id = 2, title = "another title", url = "www.foo.example.com", parentId = 1))
        dao.insertList(bookmarks)
        val list = dao.getBookmarksByParentId(1).first()
        assertEquals(list, dao.getBookmarksByParentIdSync(1))
        assertEquals(bookmarks, list)
    }

    @Test
    fun whenInInitialStateThenTheBookmarksAreEmpty() = runBlocking {
        val list = dao.getBookmarks().first()
        assertNotNull(list)
        assertTrue(list.isEmpty())
    }

    @Test
    fun whenBookmarksExistThenReturnTrue() = runBlocking {
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        dao.insert(bookmark)
        assertTrue(dao.hasBookmarks())
    }

    @Test
    fun whenBookmarkAreEmptyThenReturnFalse() = runBlocking { assertFalse(dao.hasBookmarks()) }

    @Test
    fun whenBookmarksCountByUrlAndNoBookmarksMatchThenReturnZero() {
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        dao.insert(bookmark)
        val count = dao.bookmarksCountByUrl("test")
        assertEquals(0, count)
    }

    @Test
    fun whenBookmarksCountByUrlAndBookmarksMatchThenReturnCount() {
        val query = "%example%"
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        dao.insert(bookmark)
        val count = dao.bookmarksCountByUrl(query)
        assertEquals(1, count)
    }

    @Test
    fun whenGetBookmarkByUrlAndNoBookmarksMatchThenReturnNull() {
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        dao.insert(bookmark)
        val result = dao.getBookmarkByUrl("test")
        assertNull(result)
    }

    @Test
    fun whenGetBookmarkByUrlAndOneBookmarkIsMatchedThenReturnMatchedBookmark() {
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        val otherBookmark =
            BookmarkEntity(
                id = 2, title = "other title", url = "www.other-example.com", parentId = 0)
        dao.insert(bookmark)
        dao.insert(otherBookmark)
        val result = dao.getBookmarkByUrl("www.example.com")
        assertEquals(bookmark, result)
    }

    @Test
    fun whenGetBookmarkByUrlAndMultipleBookmarksAreMatchedThenLimitTheResultToFirstMatchedBookmark() {
        val bookmark =
            BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 0)
        val sameUrlBookmark =
            BookmarkEntity(id = 2, title = "other title", url = "www.example.com", parentId = 0)
        dao.insert(bookmark)
        dao.insert(sameUrlBookmark)
        val result = dao.getBookmarkByUrl("www.example.com")
        assertEquals(bookmark, result)
    }
}
