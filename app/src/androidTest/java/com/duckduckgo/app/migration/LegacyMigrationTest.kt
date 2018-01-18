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

package com.duckduckgo.app.migration

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Room
import android.content.ContentValues
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.DuckDuckGoRequestRewriter
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.migration.legacy.LegacyDb
import com.duckduckgo.app.migration.legacy.LegacyDbContracts
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LegacyMigrationTest {

    // target context else we can't write a db file
    val context = InstrumentationRegistry.getTargetContext()
    val urlConverter = QueryUrlConverter(DuckDuckGoRequestRewriter(DuckDuckGoUrlDetector()))

    var appDatabase: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    var bookmarksDao = MockBookmarksDao()

    @After
    fun after() {
        context.getDatabasePath(LegacyDbContracts.DATABASE_NAME).delete()
    }

    @Test
    fun whenLegacyDbExistsThenMigrationIsSuccessful() {

        populateLegacyDB()

        // migrate
        val migration = LegacyMigration(appDatabase, bookmarksDao, context, urlConverter);

        migration.start { favourites, searches ->
            assertEquals(1, favourites)
            assertEquals(1, searches)
        }

        assertEquals(2, bookmarksDao.bookmarks.size)

        migration.start { favourites, searches ->
            assertEquals(0, favourites)
            assertEquals(0, searches)
        }

    }

    private fun populateLegacyDB() {
        val db = LegacyDb(context)

        assertNotEquals(-1, db.sqLiteDB.insert(LegacyDbContracts.SAVED_SEARCH_TABLE.TABLE_NAME, null, searchEntry()))
        assertNotEquals(-1, db.sqLiteDB.insert(LegacyDbContracts.FEED_TABLE.TABLE_NAME, null, favouriteEntry()))
        assertNotEquals(-1, db.sqLiteDB.insert(LegacyDbContracts.FEED_TABLE.TABLE_NAME, null, notFavouriteEntry()))

        db.close()
    }

    private fun notFavouriteEntry(): ContentValues {
        val values = ContentValues()
        values.put(LegacyDbContracts.FEED_TABLE._ID, "oops id")
        values.put(LegacyDbContracts.FEED_TABLE.COLUMN_TITLE, "oops title")
        values.put(LegacyDbContracts.FEED_TABLE.COLUMN_URL, "oops url")
        values.put(LegacyDbContracts.FEED_TABLE.COLUMN_FAVORITE, "OOPS")
        return values
    }

    private fun favouriteEntry(): ContentValues {
        val values = ContentValues()
        values.put(LegacyDbContracts.FEED_TABLE._ID, "favourite id")
        values.put(LegacyDbContracts.FEED_TABLE.COLUMN_TITLE, "favourite title")
        values.put(LegacyDbContracts.FEED_TABLE.COLUMN_URL, "favourite url")
        values.put(LegacyDbContracts.FEED_TABLE.COLUMN_FAVORITE, "F")
        return values
    }

    private fun searchEntry(): ContentValues {
        val values = ContentValues()
        values.put(LegacyDbContracts.SAVED_SEARCH_TABLE.COLUMN_TITLE, "search title")
        values.put(LegacyDbContracts.SAVED_SEARCH_TABLE.COLUMN_QUERY, "search query")
        return values
    }

    class MockBookmarksDao(): BookmarksDao {

        var bookmarks = mutableListOf<BookmarkEntity>()

        override fun insert(bookmark: BookmarkEntity) {
            bookmarks.add(bookmark)
        }

        override fun bookmarks(): LiveData<List<BookmarkEntity>> {
            throw UnsupportedOperationException()
        }

        override fun delete(bookmark: BookmarkEntity) {
            throw UnsupportedOperationException()
        }

    }

}