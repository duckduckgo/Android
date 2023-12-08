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

import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataEntity
import com.duckduckgo.savedsites.store.*
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.*
import timber.log.*

class RealSyncSavedSitesRepository(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val savedSitesSyncMetadataDao: SavedSitesSyncMetadataDao,
) : SyncSavedSitesRepository {

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

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
        Timber.d("Sync-Bookmarks: inserting Saved Site $savedSite")
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

    override fun replaceFolder(
        folder: BookmarkFolder,
        children: List<String>,
    ) {
        // check the stored list of children for folderId
        // the ones that are not in the new children list need to be moved to bookmarks_root
        val storedChildren = savedSitesEntitiesDao.allEntitiesInFolderSync(folder.id).map { it.entityId }
        val orphanedChildren = storedChildren.minus(children.toSet())
        orphanedChildren.forEach {
            savedSitesRelationsDao.updateParentId(SavedSitesNames.BOOKMARKS_ROOT, it)
        }

        savedSitesEntitiesDao.update(
            Entity(
                entityId = folder.id,
                title = folder.name,
                url = "",
                type = FOLDER,
                lastModified = folder.lastModified ?: DatabaseDateFormatter.iso8601(),
            ),
        )
    }

    override fun getFolderDiff(folderId: String): SyncFolderChildren {
        // we get the previous children metadata
        // if not present, we just add everything to children and insert
        // if present, we check for items that have been added or removed locally
        // and send them alongside the current list of local children
        val entities = savedSitesEntitiesDao.allEntitiesInFolderSync(folderId)
        val childrenLocal = entities.filterNot { it.deleted }.map { it.entityId }

        val metadata = savedSitesSyncMetadataDao.get(folderId)
        if (metadata != null) {
            val childrenResponse = stringListAdapter.fromJson(metadata.childrenResponse)!!
            if (childrenResponse == childrenLocal) {
                // both lists are the same, nothing has changed
                return SyncFolderChildren(current = childrenLocal, insert = emptyList(), remove = emptyList())
            }
            val notInResponse = childrenLocal.minus(childrenResponse.toSet()) // to be added to insert
            val notInLocal = childrenResponse.minus(childrenLocal.toSet()) // to be added to deleted
            return SyncFolderChildren(current = childrenLocal, insert = notInResponse, remove = notInLocal)
        } else {
            return SyncFolderChildren(current = childrenLocal, insert = childrenLocal, remove = emptyList())
        }
    }

    override fun confirmAllFolderChildrenMetadata() {
        savedSitesSyncMetadataDao.confirmAllChildrenRequests()
    }

    override fun addRequestMetadata(folders: List<SyncSavedSitesRequestEntry>) {
        val children = folders.filter { it.children != null }.map {
            SavedSitesSyncMetadataEntity(it.id, "[]", stringListAdapter.toJson(it.children?.current))
        }
        Timber.d("Sync-Bookmarks-Metadata: adding children request metadata for folders: $children")
        savedSitesSyncMetadataDao.addOrUpdate(children)
    }

    override fun addResponseMetadata(folders: List<SyncSavedSitesResponseEntry>) {
        val children = folders.filter { it.folder != null }.map {
            SavedSitesSyncMetadataEntity(it.id, stringListAdapter.toJson(it.folder?.children), "[]")
        }
        Timber.d("Sync-Bookmarks-Metadata: adding children response metadata for folders: $children")
        savedSitesSyncMetadataDao.confirmChildren(children)
    }
}
