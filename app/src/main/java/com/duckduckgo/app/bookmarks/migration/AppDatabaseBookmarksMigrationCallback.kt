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

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor

@OptIn(ExperimentalCoroutinesApi::class)
class AppDatabaseBookmarksMigrationCallback(
    private val appDatabase: Lazy<AppDatabase>,
    private val dispatcherProvider: DispatcherProvider,
) : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        ioThread {
            runMigration()
        }
    }

    fun runMigration() {
        migrateBookmarks()
        migrateFavorites()
        // cleanUpTables()
    }

    private fun migrateFavorites() {
        with(appDatabase.get()) {
            if (favoritesDao().userHasFavorites()) {
                val favourites = favoritesDao().favoritesSync()
                val favouriteChildren = mutableListOf<String>()
                favourites.forEach {
                    // try to purge duplicates by only adding favourites with the same url of a bookmark already added
                    val existingBookmark = syncEntitiesDao().entityByUrl(it.url)
                    if (existingBookmark != null) {
                        favouriteChildren.add(existingBookmark.id)
                    } else {
                        syncEntitiesDao().insert(Entity("favorite${it.id}", it.title, it.url, BOOKMARK))
                        favouriteChildren.add("favorite${it.id}")
                    }
                }
                syncRelationsDao().insert(Relation(Relation.FAVORITES_ROOT, favouriteChildren))
            }
        }
    }

    private fun migrateBookmarks() {
        with(appDatabase.get()) {
            if (bookmarksDao().bookmarksCount() > 0) {
                // start from root folder
                findFolderRelation(Relation.BOOMARKS_ROOT_ID)

                // continue folder by folder
                val bookmarkFolders = bookmarkFoldersDao().getBookmarkFoldersSync()
                bookmarkFolders.forEach {
                    findFolderRelation(it.id)
                }
            }
        }
    }

    private fun findFolderRelation(folderId: Long) {
        with(appDatabase.get()) {
            val entities = mutableListOf<Entity>()
            val foldersInFolder = bookmarkFoldersDao().getBookmarkFoldersByParentIdSync(folderId)
            val bookmarksInFolder = bookmarksDao().getBookmarksByParentIdSync(folderId)
            val children = mutableListOf<String>()

            foldersInFolder.forEach {
                if (folderId == Relation.BOOMARKS_ROOT_ID) {
                    entities.add(Entity(Relation.BOOMARKS_ROOT, it.name, "", FOLDER))
                } else {
                    entities.add(Entity("folder${it.id}", it.name, "", FOLDER))
                }
                children.add("folder${it.id}")
            }
            bookmarksInFolder.forEach {
                entities.add(Entity("bookmark${it.id}", it.title.orEmpty(), it.url, BOOKMARK))
                children.add("bookmark${it.id}")
            }

            if (entities.isNotEmpty()){
                syncEntitiesDao().insertList(entities)
            }

            if (children.isNotEmpty()){
                if (folderId == Relation.BOOMARKS_ROOT_ID) {
                    syncRelationsDao().insert(Relation(Relation.BOOMARKS_ROOT, children))
                } else {
                    syncRelationsDao().insert(Relation("folder$folderId", children))
                }
            }
        }
    }

    private fun cleanUpTables() {
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
