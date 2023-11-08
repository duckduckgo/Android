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

package com.duckduckgo.savedsites.impl.sync

import com.duckduckgo.app.di.*
import com.duckduckgo.app.global.*
import com.duckduckgo.app.global.formatters.time.*
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.*
import com.duckduckgo.savedsites.store.*
import java.util.*
import kotlinx.coroutines.*
import timber.log.*

class RealSyncSavedSitesRepository(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : SyncSavedSitesRepository {

    override fun getFavoritesSync(favoriteFolder: String): List<Favorite> {
        return savedSitesEntitiesDao.entitiesInFolderSync(favoriteFolder).mapToFavorites()
    }

    override fun getFavorite(
        url: String,
        favoriteFolder: String,
    ): Favorite? {
        val folder = favoriteFolder
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(folder).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.url == url }
    }

    override fun getFavoriteById(
        id: String,
        favoriteFolder: String,
    ): Favorite? {
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(favoriteFolder).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.id == id }
    }

    // why not receiving an entity?
    override fun insertFavorite(
        id: String,
        url: String,
        title: String,
        lastModified: String?,
        favoriteFolder: String,
    ): Favorite {
        val idOrFallback = id.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        val titleOrFallback = title.takeIf { it.isNotEmpty() } ?: url
        val existentBookmark = savedSitesEntitiesDao.entityByUrl(url)
        val lastModifiedOrFallback = lastModified ?: DatabaseDateFormatter.iso8601()
        return if (existentBookmark == null) {
            val entity = Entity(
                entityId = idOrFallback,
                title = titleOrFallback,
                url = url,
                type = EntityType.BOOKMARK,
                lastModified = lastModifiedOrFallback,
            )
            savedSitesEntitiesDao.insert(entity)
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
            savedSitesEntitiesDao.updateModified(SavedSitesNames.BOOKMARKS_ROOT, lastModifiedOrFallback)
            savedSitesRelationsDao.insert(Relation(folderId = favoriteFolder, entityId = entity.entityId))
            savedSitesEntitiesDao.updateModified(entityId = favoriteFolder, lastModified = lastModifiedOrFallback)
            getFavoriteById(entity.entityId, favoriteFolder)!!
        } else {
            savedSitesRelationsDao.insert(Relation(folderId = favoriteFolder, entityId = existentBookmark.entityId))
            savedSitesEntitiesDao.updateModified(entityId = favoriteFolder, lastModified = lastModifiedOrFallback)
            savedSitesEntitiesDao.updateModified(id, lastModifiedOrFallback)
            getFavorite(url, favoriteFolder)!!
        }
    }

    override fun insert(
        savedSite: SavedSite,
        favoriteFolder: String,
    ): SavedSite {
        Timber.d("Sync-Feature: inserting Saved Site $savedSite")
        val titleOrFallback = savedSite.titleOrFallback()
        return when (savedSite) {
            is Favorite -> {
                return insertFavorite(savedSite.id, savedSite.url, savedSite.title, savedSite.lastModified, favoriteFolder)
            }

            is SavedSite.Bookmark -> {
                // a bookmark will have a parent folder that we must respect
                val entity = Entity(
                    entityId = savedSite.id,
                    title = titleOrFallback,
                    url = savedSite.url,
                    type = EntityType.BOOKMARK,
                    lastModified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
                )
                savedSitesEntitiesDao.insert(entity)
                savedSitesRelationsDao.insert(Relation(folderId = savedSite.parentId, entityId = entity.entityId))
                savedSitesEntitiesDao.updateModified(
                    savedSite.parentId,
                    savedSite.lastModified
                        ?: DatabaseDateFormatter.iso8601(),
                )
                entity.mapToBookmark(savedSite.parentId)
            }
        }
    }

    override fun delete(
        savedSite: SavedSite,
        favoriteFolder: String,
    ) {
        when (savedSite) {
            is SavedSite.Bookmark -> { /* no-op */
            }

            is Favorite -> deleteFavorite(savedSite, favoriteFolder)
        }
    }

    private fun deleteFavorite(
        favorite: Favorite,
        favoriteFolder: String,
    ) {
        savedSitesRelationsDao.deleteRelationByEntity(favorite.id, favoriteFolder)
        savedSitesEntitiesDao.updateModified(favoriteFolder)
    }

    override fun updateFavourite(
        favorite: Favorite,
        favoriteFolder: String,
    ) {
        savedSitesEntitiesDao.entityById(favorite.id)?.let { entity ->
            savedSitesEntitiesDao.update(Entity(entity.entityId, favorite.title, favorite.url, EntityType.BOOKMARK))

            // following code is to update position on a specific folder
            // revisit this when implementing sync, should consider the folder
            val favorites = savedSitesEntitiesDao.entitiesInFolderSync(favoriteFolder).mapIndexed { index, entity ->
                entity.mapToFavorite(index)
            }.toMutableList()

            val currentFavorite = favorites.find { it.id == favorite.id }
            val currentIndex = favorites.indexOf(currentFavorite)
            if (currentIndex < 0) return
            favorites.removeAt(currentIndex)
            favorites.add(favorite.position, favorite)

            updateWithPosition(favorites, favoriteFolder)
        }
    }

    override fun updateWithPosition(
        favorites: List<Favorite>,
        favoriteFolder: String,
    ) {
        savedSitesRelationsDao.delete(favoriteFolder)
        val relations = favorites.map { Relation(folderId = favoriteFolder, entityId = it.id) }
        savedSitesRelationsDao.insertList(relations)
        savedSitesEntitiesDao.updateModified(favoriteFolder)
    }

    override fun replaceFavourite(
        favorite: Favorite,
        localId: String,
        favoriteFolder: String,
    ) {
        savedSitesEntitiesDao.updateId(localId, favorite.id)
        savedSitesRelationsDao.updateEntityId(localId, favorite.id)
        updateFavourite(favorite, favoriteFolder)
    }

    private fun getQueryFavoriteFolder(viewMode: FavoritesViewMode): String {
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> SavedSitesNames.FAVORITES_MOBILE_ROOT
            FavoritesViewMode.UNIFIED -> SavedSitesNames.FAVORITES_ROOT
            FavoritesViewMode.DESKTOP -> SavedSitesNames.FAVORITES_DESKTOP_ROOT
        }
    }

    private fun getInsertFavoriteFolder(viewMode: FavoritesViewMode): List<String> {
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
            FavoritesViewMode.UNIFIED -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
            FavoritesViewMode.DESKTOP -> TODO()
        }
    }

    private fun getDeleteFavoriteFolder(viewMode: FavoritesViewMode): List<String> {
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> {
                // bookmark exists for desktop, remove only for mobile
                if (false) {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT)
                } else {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
                }
            }

            FavoritesViewMode.UNIFIED -> {
                return listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_DESKTOP_ROOT)
            }

            FavoritesViewMode.DESKTOP -> TODO()
        }
    }
}
