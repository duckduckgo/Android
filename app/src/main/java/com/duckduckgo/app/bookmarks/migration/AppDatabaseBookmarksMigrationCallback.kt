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

package com.duckduckgo.app.bookmarks.migration

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.bookmarks.model.TreeNode
import com.duckduckgo.app.bookmarks.service.FolderTreeItem
import com.duckduckgo.app.bookmarks.service.RealSavedSitesParser
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.forEach

@OptIn(ExperimentalCoroutinesApi::class)
class AppDatabaseBookmarksMigrationCallback(
    private val appDatabase: Lazy<AppDatabase>,
    private val dispatcherProvider: DispatcherProvider
) : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        ioThread {
            runMigration()
        }
    }

    fun runMigration() {
        migrateFavorites()
        migrateBookmarks()
        cleanUpTables()
    }

    private fun migrateFavorites() {
        with(appDatabase.get()) {
            if (favoritesDao().userHasFavorites()) {
                val favourites = favoritesDao().favoritesSync()
                val favouriteChildren = mutableListOf<String>()
                favourites.forEach {
                    syncEntitiesDao().insert(Entity(it.id.toString(), it.title, it.url, BOOKMARK))
                    favouriteChildren.add(it.id.toString())
                }
                syncRelationsDao().insert(Relation(Relation.FAVORITES_ROOT, favouriteChildren))
            }
        }
    }

    private fun migrateBookmarks() {
        with(appDatabase.get()) {
            if (bookmarksDao().bookmarksCount() > 0) {
                // start from root folder
                val bookmarksInRoot = bookmarksDao().getBookmarksByParentIdSync(Relation.BOOMARKS_ROOT_ID)
                val boomarksInRootChildren = mutableListOf<String>()
                bookmarksInRoot.forEach {
                    syncEntitiesDao().insert(Entity(it.id.toString(), it.title.orEmpty(), it.url, BOOKMARK))
                    boomarksInRootChildren.add(it.id.toString())
                }
                syncRelationsDao().insert(Relation(Relation.BOOMARKS_ROOT, boomarksInRootChildren))

                // continue folder by folder
            }
        }
    }

    private fun cleanUpTables(){
        with(appDatabase.get()) {
            favoritesDao().deleteAll()
            bookmarksDao().deleteAll()
            bookmarkFoldersDao().deleteAll()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ioThread(f: () -> Unit) {
        // At most 1 thread will be doing IO
        dispatcherProvider.io().limitedParallelism(1).asExecutor().execute(f)
    }
}
