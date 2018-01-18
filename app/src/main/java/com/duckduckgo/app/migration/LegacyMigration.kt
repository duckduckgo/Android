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

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.migration.legacy.LegacyDB
import com.duckduckgo.app.migration.legacy.LegacyDBContracts
import org.jetbrains.anko.doAsync
import javax.inject.Inject

class LegacyMigration @Inject constructor(
        val database: AppDatabase,
        val bookmarksDao: BookmarksDao,
        val context: Context,
        val queryUrlConverter: QueryUrlConverter) {

    /**
     * Start an asynchronous migration. The completion handler will be called with the number of items migrated.
     *
     * @param context - a context to use, but always uses the application context
     * @param completion - a migration handler that will be called once migration has finished
     */
    fun start(context: Context, completion: (favourites: Int, searches: Int) -> Unit) {
        doAsync {
            migrate(context.applicationContext, completion)
        }
    }

    private fun migrate(context: Context, completion: (favourites: Int, searches: Int) -> Unit) {
        val ddgDB = LegacyDB(context)

        var favourites = 0
        var searches = 0
        database.runInTransaction {
            favourites = migrateSavedFeedObjects(ddgDB)
            searches = migrateSavedSearches(ddgDB)
        }

        ddgDB.deleteAll()

        completion(favourites, searches)
    }

    private fun migrateSavedSearches(ddgDB: LegacyDB): Int {

        val cursor = ddgDB.cursorSavedSearch
        if (!cursor.moveToFirst()) {
            return 0
        }

        val titleColumn = cursor.getColumnIndex(LegacyDBContracts.SAVED_SEARCH_TABLE.COLUMN_TITLE)
        val queryColumn = cursor.getColumnIndex(LegacyDBContracts.SAVED_SEARCH_TABLE.COLUMN_QUERY)

        var count = 0
        do {

            val title = cursor.getString(titleColumn)
            val query = cursor.getString(queryColumn)

            val url = if (URLUtil.isNetworkUrl(query)) query else queryUrlConverter.convertQueryToUri(query).toString()

            bookmarksDao.insert(BookmarkEntity(title = title, url = url))

            count += 1
        } while (cursor.moveToNext())

        return count
    }

    private fun migrateSavedFeedObjects(ddgDB: LegacyDB) : Int {
        val feedObjects = ddgDB.selectAll() ?: return 0

        for (feedObject in feedObjects) {

            val title = feedObject.title
            val url = feedObject.url

            bookmarksDao.insert(BookmarkEntity(title = title, url = url))
        }

        return feedObjects.size
    }

}