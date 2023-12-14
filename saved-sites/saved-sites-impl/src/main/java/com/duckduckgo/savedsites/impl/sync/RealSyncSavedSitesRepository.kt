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
        // remove all current relations
        // store the new children list
        // at the end of the algorithm we check for entities - outer join relations
        // those will be the new orphans
        Timber.d("Sync-Bookmarks: replacing ${folder.id} with children $children")
        savedSitesRelationsDao.replaceFolder(folder, children)
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
        val deletedChildren = entities.filter { it.deleted }.map { it.entityId }
        val childrenLocal = entities.filterNot { it.deleted }.map { it.entityId }

        val metadata = savedSitesSyncMetadataDao.get(folderId)
        if (metadata != null) {
            val childrenResponse = stringListAdapter.fromJson(metadata.childrenResponse!!)
            if (childrenResponse != null) {
                if (childrenResponse == childrenLocal) {
                    // both lists are the same, nothing has changed
                    return SyncFolderChildren(current = childrenLocal, insert = emptyList(), remove = deletedChildren)
                }
                val notInResponse = childrenLocal.minus(childrenResponse.toSet()) // to be added to insert
                val notInLocal = childrenResponse.minus(childrenLocal.toSet()) // to be added to deleted
                val deleted = deletedChildren.plus(notInLocal).distinct()
                return SyncFolderChildren(current = childrenLocal, insert = notInResponse, remove = deleted)
            } else {
                return SyncFolderChildren(current = childrenLocal, insert = emptyList(), remove = deletedChildren)
            }
        } else {
            return SyncFolderChildren(current = childrenLocal, insert = childrenLocal, remove = deletedChildren)
        }
    }

    private fun confirmPendingFolderRequests() {
        Timber.d("Sync-Bookmarks-Metadata: updating children metadata column with values from request column")
        savedSitesSyncMetadataDao.confirmAllChildrenRequests()
    }

    override fun addRequestMetadata(folders: List<SyncSavedSitesRequestEntry>) {
        val children = folders.filter { it.folder != null }.map {
            SavedSitesSyncMetadataEntity(it.id, "[]", stringListAdapter.toJson(it.folder?.children?.current))
        }
        Timber.d("Sync-Bookmarks-Metadata: adding children request metadata for folders: $children")
        savedSitesSyncMetadataDao.addOrUpdate(children)
    }

    // for all folders in the payload that are not deleted
    // add children to children column
    // for all items in the metadata table
    // copy request columns to children
    override fun addResponseMetadata(entities: List<SyncSavedSitesResponseEntry>) {
        if (entities.isEmpty()) {
            confirmPendingFolderRequests()
        } else {
            val folders = entities.filter { it.deleted != null }.filter { it.folder != null }
            if (folders.isEmpty()) {
                Timber.d("Sync-Bookmarks-Metadata: no folders received in the response, nothing to add")
                confirmPendingFolderRequests()
            } else {
                val children = folders.map {
                    val response = stringListAdapter.toJson(it.folder?.children)
                    Timber.d("Sync-Bookmarks-Metadata: new children response $response for ${it.id}")
                    SavedSitesSyncMetadataEntity(it.id, response, null)
                }
                Timber.d("Sync-Bookmarks-Metadata: storing children response metadata for: $children")
                savedSitesSyncMetadataDao.addResponseMetadata(children)
            }
        }
    }

    override fun removeMetadata() {
        savedSitesSyncMetadataDao.removeAll()
    }

    override fun fixOrphans(): Boolean {
        val orphans = savedSitesRelationsDao.getOrphans()
        return if (orphans.isNotEmpty()) {
            Timber.d("Sync-Bookmarks: Found ${orphans.size} orphans, $orphans attaching them to bookmarks root")
            orphans.map { Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = it.entityId) }.also {
                savedSitesRelationsDao.insertList(it)
            }
            true
        } else {
            Timber.d("Sync-Bookmarks: Orphans not present")
            false
        }
    }

    override fun pruneDeleted() {
        Timber.d("Sync-Bookmarks: pruning soft deleted entities and metadata")
        savedSitesEntitiesDao.allDeleted().forEach {
            savedSitesRelationsDao.deleteRelationByEntity(it.entityId)
            savedSitesEntitiesDao.deletePermanently(it)
            savedSitesSyncMetadataDao.remove(it.entityId)
        }
    }
}
