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
import android.support.annotation.WorkerThread
import android.webkit.URLUtil
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.migration.legacy.LegacyDb
import com.duckduckgo.app.migration.legacy.LegacyDbContracts
import timber.log.Timber
import javax.inject.Inject

class LegacyMigration @Inject constructor(
        val database: AppDatabase,
        val bookmarksDao: BookmarksDao,
        val context: Context,
        val queryUrlConverter: QueryUrlConverter) {

    @WorkerThread
    fun start(completion: (favourites: Int, searches: Int) -> Unit) {

        LegacyDb(context.applicationContext).use {
            migrate(it, completion)
        }

    }

    private fun migrate(legacyDb:LegacyDb, completion: (favourites: Int, searches: Int) -> Unit) {

        var favourites = 0
        var searches = 0

        database.runInTransaction {
            favourites = migrateFavourites(legacyDb)
            searches = migrateSavedSearches(legacyDb)
            legacyDb.deleteAll()
        }

        completion(favourites, searches)
    }

    private fun migrateSavedSearches(db: LegacyDb): Int {

        var count = 0
        db.cursorSavedSearch.use {

            if (!it.moveToFirst()) {
                Timber.d("No saved searches found")
                return 0
            }

            val titleColumn = it.getColumnIndex(LegacyDbContracts.SAVED_SEARCH_TABLE.COLUMN_TITLE)
            val queryColumn = it.getColumnIndex(LegacyDbContracts.SAVED_SEARCH_TABLE.COLUMN_QUERY)

            do {

                val title = it.getString(titleColumn)
                val query = it.getString(queryColumn)

                val url = if (URLUtil.isNetworkUrl(query)) query else queryUrlConverter.convertQueryToUri(query).toString()

                bookmarksDao.insert(BookmarkEntity(title = title, url = url))

                count += 1
            } while (it.moveToNext())
        }

        return count
    }

    private fun migrateFavourites(db: LegacyDb) : Int {
        val feedObjects = db.selectAll() ?: return 0

        var count = 0
        for (feedObject in feedObjects) {

            if (!db.isSaved(feedObject.id)) {
                continue
            }

            val title = feedObject.title
            val url = feedObject.url

            bookmarksDao.insert(BookmarkEntity(title = title, url = url))

            count += 1
        }

        return count
    }

}