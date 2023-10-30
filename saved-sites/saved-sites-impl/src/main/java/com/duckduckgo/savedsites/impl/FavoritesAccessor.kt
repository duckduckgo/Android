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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.impl.sync.*
import com.duckduckgo.savedsites.store.*
import com.duckduckgo.sync.api.*
import com.squareup.anvil.annotations.*
import io.reactivex.*
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.*

interface FavoritesAccessor {
    fun changeViewMode()
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
class FavoritesAccessorImpl @Inject constructor(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val syncStateMonitor: SyncStateMonitor,
    private val savedSitesSettings: SavedSitesSettingsRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : FavoritesAccessor {

    private var viewMode: ViewMode = ViewMode.Default

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            savedSitesSettings.viewModeFlow()
                .combine(syncStateMonitor.syncState()) { viewMode, syncState ->
                    when (syncState) {
                        SyncState.OFF -> ViewMode.Default
                        else -> ViewMode.FormFactorViewMode(viewMode)
                    }
                }.distinctUntilChanged().collect {
                    viewMode = it
                }
        }
    }

    override fun getFavorites(): Flow<List<SavedSite.Favorite>> {
        return getFavoriteFolderFlow().flatMapLatest { favoriteFolder ->
            savedSitesRelationsDao.relations(favoriteFolder).distinctUntilChanged().map { relations ->
                Timber.d("Sync-Bookmarks: getFavorites as Flow, emit relations")
                relations.mapIndexed { index, relation ->
                    savedSitesEntitiesDao.entityById(relation.entityId)!!.mapToFavorite(index)
                }
            }.flowOn(dispatcherProvider.io())
        }
    }

    override fun getFavoritesObservable(): Single<List<SavedSite.Favorite>> {
        return savedSitesRelationsDao.relationsObservable(SavedSitesNames.FAVORITES_ROOT).map { relations ->
            relations.mapIndexed { index, relation ->
                savedSitesEntitiesDao.entityById(relation.entityId)!!.mapToFavorite(index)
            }
        }
    }

    override fun getFavoritesSync(): List<SavedSite.Favorite> {
        val folder = getFavoriteFolder(viewMode)
        return savedSitesEntitiesDao.entitiesInFolderSync(folder).mapToFavorites()
    }

    override fun getFavoritesCountByDomain(domain: String): Int {
        val folder = getFavoriteFolder(viewMode)
        return savedSitesRelationsDao.countFavouritesByUrl(domain, folder)
    }

    override fun getFavoriteById(id: String): SavedSite.Favorite? {
        val folder = getFavoriteFolder(viewMode)
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(folder).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.id == id }
    }

    override fun favoritesCount(): Long {
        val folder = getFavoriteFolder(viewMode)
        return savedSitesEntitiesDao.entitiesInFolderSync(folder).size.toLong()
    }

    override fun getFavorite(url: String): SavedSite.Favorite? {
        val folder = getFavoriteFolder(viewMode)
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(folder).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.url == url }
    }

    override fun updateWithPosition(favorites: List<SavedSite.Favorite>) {
        val favoriteFolder = getFavoriteFolder(viewMode)
        savedSitesRelationsDao.delete(favoriteFolder)
        val relations = favorites.map { Relation(folderId = favoriteFolder, entityId = it.id) }
        savedSitesRelationsDao.insertList(relations)
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
        val favoriteFolders = getInsertFavoriteFolder(viewMode)
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
            favoriteFolders.forEach { favoriteFolderName ->
                savedSitesRelationsDao.insert(Relation(folderId = favoriteFolderName, entityId = existentBookmark.entityId))
                savedSitesEntitiesDao.updateModified(entityId = favoriteFolderName, lastModified = lastModifiedOrFallback)
            }
            savedSitesEntitiesDao.updateModified(id, lastModifiedOrFallback)
            getFavorite(url)!!
        }
    }

    override fun deleteFavorite(favorite: SavedSite.Favorite) {
        val deleteFavoriteFolder = getDeleteFavoriteFolder(favorite.id, viewMode)
        deleteFavoriteFolder.forEach { folderName ->
            savedSitesRelationsDao.deleteRelationByEntity(favorite.id, folderName)
            savedSitesEntitiesDao.updateModified(folderName)
        }
    }

    private fun getFavoriteFolderFlow(): Flow<String> {
        return savedSitesSettings.viewModeFlow().map { getFavoriteFormFactorFolder(it) }
    }

    private fun getFavoriteFolder(viewMode: ViewMode): String {
        return when (viewMode) {
            ViewMode.Default -> SavedSitesNames.FAVORITES_ROOT
            is ViewMode.FormFactorViewMode -> return getFavoriteFormFactorFolder(viewMode.favoritesViewMode)
        }
    }

    private fun getFavoriteFormFactorFolder(viewMode: FavoritesViewMode): String {
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> SavedSitesNames.FAVORITES_MOBILE_ROOT
            FavoritesViewMode.UNIFIED -> SavedSitesNames.FAVORITES_ROOT
        }
    }

    private fun getInsertFavoriteFolder(viewMode: ViewMode): List<String> {
        return when (viewMode) {
            ViewMode.Default -> listOf(SavedSitesNames.FAVORITES_ROOT)
            is ViewMode.FormFactorViewMode -> getInsertFavoriteFormFactorFolder(viewMode.favoritesViewMode)
        }
    }

    private fun getInsertFavoriteFormFactorFolder(viewMode: FavoritesViewMode): List<String> {
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
            FavoritesViewMode.UNIFIED -> listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
        }
    }

    private fun getDeleteFavoriteFolder(
        entityId: String,
        viewMode: ViewMode,
    ): List<String> {
        return when (viewMode) {
            ViewMode.Default -> listOf(SavedSitesNames.FAVORITES_ROOT)
            is ViewMode.FormFactorViewMode -> { getDeleteFormFactorFolder(entityId, viewMode.favoritesViewMode) }
        }
    }

    private fun getDeleteFormFactorFolder(
        entityId: String,
        viewMode: FavoritesViewMode,
    ): List<String> {
        return when (viewMode) {
            FavoritesViewMode.NATIVE -> {
                val isDesktopFavorite = savedSitesRelationsDao.relationsByEntityId(entityId)
                    .find { it.folderId == SavedSitesNames.FAVORITES_DESKTOP_ROOT } != null
                if (isDesktopFavorite) {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT)
                } else {
                    listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
                }
            }

            FavoritesViewMode.UNIFIED -> {
                listOf(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_DESKTOP_ROOT)
            }
        }
    }

    override fun changeViewMode() {
        val viewModeX = viewMode
        when (viewModeX) {
            ViewMode.Default -> return
            is ViewMode.FormFactorViewMode -> {
                when (viewModeX.favoritesViewMode) {
                    FavoritesViewMode.UNIFIED -> {
                        savedSitesSettings.favoritesDisplayMode = FavoritesViewMode.NATIVE
                    }
                    FavoritesViewMode.NATIVE -> {
                        savedSitesSettings.favoritesDisplayMode = FavoritesViewMode.UNIFIED
                    }
                }
            }
        }
    }

    sealed class ViewMode {
        object Default : ViewMode()
        data class FormFactorViewMode(val favoritesViewMode: FavoritesViewMode) : ViewMode()
    }
}
