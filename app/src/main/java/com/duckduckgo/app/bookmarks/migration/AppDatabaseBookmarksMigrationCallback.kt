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
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.Relation
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import java.util.UUID

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
        addRootFolders()
        if (needsMigration()) {
            migrateBookmarks()
            migrateFavorites()
            cleanUpTables()
        }
    }

    private fun addRootFolders() {
        with(appDatabase.get()) {
            syncEntitiesDao().insert(Entity(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "", FOLDER))
            syncEntitiesDao().insert(Entity(SavedSitesNames.BOOMARKS_ROOT, SavedSitesNames.BOOKMARKS_NAME, "", FOLDER))
        }
    }

    private fun needsMigration(): Boolean {
        with(appDatabase.get()) {
            return (favoritesDao().userHasFavorites() || bookmarksDao().bookmarksCount() > 0)
        }
    }

    private fun migrateFavorites() {
        with(appDatabase.get()) {
            val favouriteMigration = mutableListOf<Relation>()
            val entitiesMigration = mutableListOf<Entity>()

            val favourites = favoritesDao().favoritesSync()
            favourites.forEach {
                // try to purge duplicates by only adding favourites with the same url of a bookmark already added
                val existingBookmark = syncEntitiesDao().entityByUrl(it.url)
                if (existingBookmark != null) {
                    favouriteMigration.add(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = existingBookmark.entityId))
                } else {
                    val entity = Entity(UUID.randomUUID().toString(), it.title, it.url, BOOKMARK)
                    entitiesMigration.add(entity)
                    favouriteMigration.add(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = entity.entityId))
                }
            }

            syncEntitiesDao().insertList(entitiesMigration)
            syncRelationsDao().insertList(favouriteMigration)
        }
    }

    private fun migrateBookmarks() {
        with(appDatabase.get()) {
            if (bookmarksDao().bookmarksCount() > 0) {
                // start from root folder
                findFolderRelation(SavedSitesNames.BOOMARKS_ROOT_ID)

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
            val relations = mutableListOf<Relation>()
            val foldersInFolder = bookmarkFoldersDao().getBookmarkFoldersByParentIdSync(folderId)
            val bookmarksInFolder = bookmarksDao().getBookmarksByParentIdSync(folderId)

            val generatedFolderId = UUID.randomUUID().toString()

            foldersInFolder.forEach {
                val entity = Entity(UUID.randomUUID().toString(), it.name, "", FOLDER)
                entities.add(entity)

                if (folderId == 0L) {
                    relations.add(Relation(folderId = SavedSitesNames.BOOMARKS_ROOT, entityId = entity.entityId))
                } else {
                    relations.add(Relation(folderId = generatedFolderId, entityId = entity.entityId))
                }
            }

            bookmarksInFolder.forEach {
                val entity = Entity(UUID.randomUUID().toString(), it.title.orEmpty(), it.url, BOOKMARK)
                entities.add(entity)

                if (folderId == 0L) {
                    relations.add(Relation(folderId = SavedSitesNames.BOOMARKS_ROOT, entityId = entity.entityId))
                } else {
                    relations.add(Relation(folderId = generatedFolderId, entityId = entity.entityId))
                }
            }

            if (entities.isNotEmpty()) {
                syncEntitiesDao().insertList(entities)
            }

            if (relations.isNotEmpty()) {
                syncRelationsDao().insertList(relations)
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
