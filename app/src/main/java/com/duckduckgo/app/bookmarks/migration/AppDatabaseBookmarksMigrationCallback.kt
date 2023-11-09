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
import com.duckduckgo.app.global.formatters.time.*
import com.duckduckgo.appbuildconfig.api.*
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.Relation
import dagger.Lazy
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor

@OptIn(ExperimentalCoroutinesApi::class)
class AppDatabaseBookmarksMigrationCallback(
    private val appDatabase: Lazy<AppDatabase>,
    private val dispatcherProvider: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
) : RoomDatabase.Callback() {

    private val folderMap: MutableMap<Long, String> = mutableMapOf()

    override fun onOpen(db: SupportSQLiteDatabase) {
        ioThread {
            runMigration()
        }
    }

    fun runMigration() {
        addRootFolders()
        val needsMigration = needsMigration()
        if (needsMigration) {
            migrateBookmarks()
            migrateFavorites()
            cleanUpTables()
        }

        // To be removed once internals update the app too FormFactorSpecificFavorites
        if (appBuildConfig.isInternalBuild()) {
            addFavoritesFormFactorFolders()
            val needsFormFactorFavoritesMigration = needsFormFactorFavoritesMigration()
            if (needsFormFactorFavoritesMigration) {
                migrateFavoritesToFormFactorFolders()
            }
        }
    }

    private fun addRootFolders() {
        with(appDatabase.get()) {
            if (syncEntitiesDao().entityById(SavedSitesNames.BOOKMARKS_ROOT) == null) {
                syncEntitiesDao().insert(Entity(SavedSitesNames.BOOKMARKS_ROOT, SavedSitesNames.BOOKMARKS_NAME, "", FOLDER, lastModified = null))
            }
            if (syncEntitiesDao().entityById(SavedSitesNames.FAVORITES_ROOT) == null) {
                syncEntitiesDao().insert(Entity(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "", FOLDER, lastModified = null))
            }
        }
    }

    private fun addFavoritesFormFactorFolders() {
        with(appDatabase.get()) {
            if (syncEntitiesDao().entityById(SavedSitesNames.FAVORITES_MOBILE_ROOT) == null) {
                syncEntitiesDao().insert(
                    Entity(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_MOBILE_NAME, "", FOLDER, lastModified = null),
                )
            }
            if (syncEntitiesDao().entityById(SavedSitesNames.FAVORITES_DESKTOP_ROOT) == null) {
                syncEntitiesDao().insert(
                    Entity(SavedSitesNames.FAVORITES_DESKTOP_ROOT, SavedSitesNames.FAVORITES_DESKTOP_NAME, "", FOLDER, lastModified = null),
                )
            }
        }
    }

    private fun needsMigration(): Boolean {
        with(appDatabase.get()) {
            return (favoritesDao().userHasFavorites() || bookmarksDao().bookmarksCount() > 0)
        }
    }
    private fun needsFormFactorFavoritesMigration(): Boolean {
        with(appDatabase.get()) {
            return syncEntitiesDao().allEntitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT) != syncEntitiesDao().allEntitiesInFolderSync(
                SavedSitesNames.FAVORITES_MOBILE_ROOT,
            )
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
                    favouriteMigration.add(Relation(folderId = SavedSitesNames.FAVORITES_MOBILE_ROOT, entityId = existingBookmark.entityId))
                } else {
                    val entity = Entity(UUID.randomUUID().toString(), it.title, it.url, BOOKMARK)
                    entitiesMigration.add(entity)
                    favouriteMigration.add(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = entity.entityId))
                    favouriteMigration.add(Relation(folderId = SavedSitesNames.FAVORITES_MOBILE_ROOT, entityId = entity.entityId))
                    favouriteMigration.add(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
                }
            }

            syncEntitiesDao().insertList(entitiesMigration)
            syncRelationsDao().insertList(favouriteMigration)
        }
    }

    private fun migrateFavoritesToFormFactorFolders() {
        with(appDatabase.get()) {
            val favouriteMigration = mutableListOf<Relation>()
            val entitiesMigration = mutableListOf<Entity>()
            val rootFavorites = syncEntitiesDao().allEntitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT)
            val formFactorFavorites = syncEntitiesDao().allEntitiesInFolderSync(SavedSitesNames.FAVORITES_MOBILE_ROOT)
            val formFactorFolder = syncEntitiesDao().entityById(SavedSitesNames.FAVORITES_MOBILE_ROOT)
            val needRelation = rootFavorites.filter { rootFavorite ->
                formFactorFavorites.firstOrNull { it.entityId == rootFavorite.entityId } == null
            }
            val now = DatabaseDateFormatter.iso8601()
            needRelation.forEach {
                it.lastModified = now
                entitiesMigration.add(it)
                favouriteMigration.add(Relation(folderId = SavedSitesNames.FAVORITES_MOBILE_ROOT, entityId = it.entityId))
            }
            if (needRelation.isNotEmpty()) {
                formFactorFolder?.apply {
                    lastModified = now
                    entitiesMigration.add(this)
                }
            }
            syncEntitiesDao().insertList(entitiesMigration)
            syncRelationsDao().insertList(favouriteMigration)
        }
    }

    private fun migrateBookmarks() {
        with(appDatabase.get()) {
            if (bookmarksDao().bookmarksCount() > 0) {
                // generate Ids for all folders
                val bookmarkFolders = bookmarkFoldersDao().getBookmarkFoldersSync()
                bookmarkFolders.forEach {
                    folderMap[it.id] = UUID.randomUUID().toString()
                }

                // start from root folder
                findFolderRelation(SavedSitesNames.BOOMARKS_ROOT_ID, folderMap)

                // continue folder by folder
                bookmarkFolders.forEach {
                    findFolderRelation(it.id, folderMap)
                }
            }
        }
    }

    private fun findFolderRelation(
        parentId: Long,
        folderMap: MutableMap<Long, String>,
    ) {
        with(appDatabase.get()) {
            val entities = mutableListOf<Entity>()
            val relations = mutableListOf<Relation>()
            val foldersInFolder = bookmarkFoldersDao().getBookmarkFoldersByParentIdSync(parentId)
            val bookmarksInFolder = bookmarksDao().getBookmarksByParentIdSync(parentId)

            foldersInFolder.forEach {
                val entity = Entity(entityId = folderMap[it.id.toLong()]!!, title = it.name, url = "", type = FOLDER)
                entities.add(entity)

                if (parentId == SavedSitesNames.BOOMARKS_ROOT_ID) {
                    relations.add(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
                } else {
                    relations.add(Relation(folderId = folderMap[parentId]!!, entityId = entity.entityId))
                }
            }

            bookmarksInFolder.forEach {
                val entity = Entity(UUID.randomUUID().toString(), it.title.orEmpty(), it.url, BOOKMARK)
                entities.add(entity)

                if (parentId == SavedSitesNames.BOOMARKS_ROOT_ID) {
                    relations.add(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
                } else {
                    relations.add(Relation(folderId = folderMap[parentId]!!, entityId = entity.entityId))
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
