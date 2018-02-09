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

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.global.db.AppDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BookmarksDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BookmarksDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(), AppDatabase::class.java)
                .build()
        dao = db.bookmarksDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenBookmarkDeleteThenItIsNoLongerInTheList() {
        val bookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com")
        dao.insert(bookmark)
        dao.delete(bookmark)
        val list = dao.bookmarks().blockingObserve()
        assertTrue(list!!.isEmpty())
    }

    @Test
    fun whenBookmarkAddedThenItIsInList() {
        val bookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com")
        dao.insert(bookmark)
        val list = dao.bookmarks().blockingObserve()
        assertEquals(listOf(bookmark), list)
    }

    @Test
    fun whenInInitialStateThenTheBookmarksAreEmpty() {
        val list = dao.bookmarks().blockingObserve()
        assertNotNull(list)
        assertTrue(list!!.isEmpty())
    }

}