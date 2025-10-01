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

package com.duckduckgo.savedsites.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.*
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.*
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.impl.sync.*
import com.duckduckgo.savedsites.store.*
import com.duckduckgo.sync.api.*
import com.squareup.anvil.annotations.*
import io.reactivex.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

interface FavoritesDelegate {
    fun getFavoritesSync(): List<SavedSite.Favorite>
    fun getFavoritesCountByDomain(domain: String): Int
    fun getFavorite(url: String): SavedSite.Favorite?
    fun getFavoriteById(id: String): SavedSite.Favorite?
    fun favoritesCount(): Long
    fun updateWithPosition(favorites: List<SavedSite.Favorite>)
    fun insertFavorite(id: String, url: String, title: String, lastModified: String?): SavedSite.Favorite
    fun deleteFavorite(favorite: SavedSite.Favorite)
    fun getFavorites(): Flow<List<SavedSite.Favorite>>
    fun getFavoritesObservable(): Single<List<SavedSite.Favorite>>
    fun updateFavourite(favorite: SavedSite.Favorite)
}

@ContributesBinding(AppScope::class)
class RealFavoritesDelegate @Inject constructor(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val favoritesDisplayModeSetting: FavoritesDisplayModeSettingsRepository,
    private val relationsReconciler: RelationsReconciler,
    private val dispatcherProvider: DispatcherProvider,
) : FavoritesDelegate {

    override fun getFavorites(): Flow<List<SavedSite.Favorite>> {
        return favoritesDisplayModeSetting.getFavoriteFolderFlow().flatMapLatest { viewMode ->
            val favoriteFolder = favoritesDisplayModeSetting.getQueryFolder()
            savedSitesRelationsDao.relations(favoriteFolder).distinctUntilChanged().map { relations ->
                relations.mapIndexedNotNull { index, relation ->
                    savedSitesEntitiesDao.entityById(relation.entityId)?.mapToFavorite(index)
                }
            }.flowOn(dispatcherProvider.io())
        }
    }

    override fun getFavoritesObservable(): Single<List<SavedSite.Favorite>> {
        return savedSitesRelationsDao.relationsObservable(SavedSitesNames.FAVORITES_ROOT).map { relations ->
            relations.mapIndexedNotNull { index, relation ->
                savedSitesEntitiesDao.entityById(relation.entityId)?.mapToFavorite(index)
            }
        }
    }

    override fun getFavoritesSync(): List<SavedSite.Favorite> {
        val folder = favoritesDisplayModeSetting.getQueryFolder()
        return savedSitesEntitiesDao.entitiesInFolderSync(folder).mapToFavorites()
    }

    override fun getFavoritesCountByDomain(domain: String): Int {
        val folder = favoritesDisplayModeSetting.getQueryFolder()
        return savedSitesRelationsDao.countFavouritesByUrl(domain, folder)
    }

    override fun getFavoriteById(id: String): SavedSite.Favorite? {
        val folder = favoritesDisplayModeSetting.getQueryFolder()
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(folder).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.id == id }
    }

    override fun favoritesCount(): Long {
        val folder = favoritesDisplayModeSetting.getQueryFolder()
        return savedSitesEntitiesDao.entitiesInFolderSync(folder).size.toLong()
    }

    override fun getFavorite(url: String): SavedSite.Favorite? {
        val folder = favoritesDisplayModeSetting.getQueryFolder()
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(folder).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.url == url }
    }

    override fun updateWithPosition(favorites: List<SavedSite.Favorite>) {
        val favoriteFolder = favoritesDisplayModeSetting.getQueryFolder()
        val relations = favorites.map { Relation(folderId = favoriteFolder, entityId = it.id) }
        val reconciledList = relationsReconciler.reconcileRelations(
            originalRelations = savedSitesRelationsDao.relationsByFolderId(favoriteFolder).map { it.entityId },
            newFolderRelations = relations.map { it.entityId },
        )
        savedSitesRelationsDao.replaceFavouriteFolder(favoriteFolder, reconciledList)
        savedSitesEntitiesDao.updateModified(favoriteFolder)
    }

    override fun updateFavourite(favorite: SavedSite.Favorite) {
        savedSitesEntitiesDao.entityById(favorite.id)?.let { entity ->
            savedSitesEntitiesDao.update(Entity(entity.entityId, favorite.title, favorite.url, EntityType.BOOKMARK))
        }
    }

    override fun insertFavorite(
        id: String,
        url: String,
        title: String,
        lastModified: String?,
    ): SavedSite.Favorite {
        val idOrFallback = id.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        val titleOrFallback = title.takeIf { it.isNotEmpty() } ?: url
        val existentBookmark = savedSitesEntitiesDao.entityByUrl(url)
        val lastModifiedOrFallback = lastModified ?: DatabaseDateFormatter.iso8601()
        val favoriteFolders = favoritesDisplayModeSetting.getInsertFolder()
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
            favoriteFolders.forEach { favoriteFolderName ->
                savedSitesRelationsDao.insert(Relation(folderId = favoriteFolderName, entityId = entity.entityId))
                savedSitesEntitiesDao.updateModified(entityId = favoriteFolderName, lastModified = lastModifiedOrFallback)
            }
            getFavoriteById(entity.entityId)!!
        } else {
            val currentRelations = savedSitesRelationsDao.relationsByEntityId(existentBookmark.entityId)
            favoriteFolders.forEach { favoriteFolderName ->
                if (currentRelations.firstOrNull { it.folderId == favoriteFolderName } == null) {
                    savedSitesRelationsDao.insert(Relation(folderId = favoriteFolderName, entityId = existentBookmark.entityId))
                    savedSitesEntitiesDao.updateModified(entityId = favoriteFolderName, lastModified = lastModifiedOrFallback)
                }
            }
            savedSitesEntitiesDao.updateModified(id, lastModifiedOrFallback)
            getFavorite(url)!!
        }
    }

    override fun deleteFavorite(favorite: SavedSite.Favorite) {
        val deleteFavoriteFolder = favoritesDisplayModeSetting.getDeleteFolder(favorite.id)
        deleteFavoriteFolder.forEach { folderName ->
            savedSitesRelationsDao.deleteRelationByEntity(favorite.id, folderName)
            savedSitesEntitiesDao.updateModified(folderName)
        }
    }
}
