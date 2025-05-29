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
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncEntitiesStore
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataEntity
import com.duckduckgo.savedsites.store.*
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.OffsetDateTime
import java.util.*
import logcat.LogPriority.INFO
import logcat.logcat

class RealSyncSavedSitesRepository(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val savedSitesSyncMetadataDao: SavedSitesSyncMetadataDao,
    private val savedSitesEntitiesStore: SavedSitesSyncEntitiesStore,
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
        logcat { "Sync-Bookmarks: inserting Saved Site $savedSite" }
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

    override fun replaceBookmarkFolder(
        folder: BookmarkFolder,
        children: List<String>,
    ) {
        logcat { "Sync-Bookmarks: replacing ${folder.id} with children $children" }
        savedSitesRelationsDao.replaceBookmarkFolder(folder.id, children)
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

    override fun addToFavouriteFolder(
        favouriteFolder: String,
        children: List<String>,
    ) {
        logcat { "Sync-Bookmarks: adding $children to $favouriteFolder" }
        val relations = children.map {
            Relation(folderId = favouriteFolder, entityId = it)
        }
        savedSitesRelationsDao.insertList(relations)
        savedSitesEntitiesDao.updateModified(favouriteFolder)
    }

    override fun replaceFavouriteFolder(
        favouriteFolder: String,
        children: List<String>,
    ) {
        logcat { "Sync-Bookmarks: replacing $favouriteFolder with children $children" }
        savedSitesRelationsDao.replaceFavouriteFolder(favouriteFolder, children)
    }

    override fun getAllFolderContentSync(folderId: String): Pair<List<Bookmark>, List<BookmarkFolder>> {
        val entities = savedSitesEntitiesDao.allEntitiesInFolderSync(folderId)
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()
        entities.forEach { entity ->
            mapEntity(entity, folderId, bookmarks, folders)
        }
        return Pair(bookmarks.distinct(), folders.distinct())
    }

    private fun mapEntity(
        entity: Entity,
        folderId: String,
        bookmarks: MutableList<Bookmark>,
        folders: MutableList<BookmarkFolder>,
    ) {
        if (entity.type == FOLDER) {
            val numFolders = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
            val numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
            folders.add(BookmarkFolder(entity.entityId, entity.title, folderId, numBookmarks, numFolders, entity.lastModified, entity.deletedFlag()))
        } else {
            bookmarks.add(
                Bookmark(
                    id = entity.entityId,
                    title = entity.title,
                    url = entity.url.orEmpty(),
                    parentId = folderId,
                    lastModified = entity.lastModified,
                ),
            )
        }
    }

    override fun replaceBookmark(
        bookmark: Bookmark,
        localId: String,
    ) {
        savedSitesEntitiesDao.updateId(localId, bookmark.id)
        savedSitesRelationsDao.updateEntityId(localId, bookmark.id)
        savedSitesEntitiesDao.update(
            Entity(
                bookmark.id,
                bookmark.title,
                bookmark.url,
                BOOKMARK,
                bookmark.lastModified ?: DatabaseDateFormatter.iso8601(),
            ),
        )

        savedSitesEntitiesDao.updateModified(bookmark.parentId, bookmark.lastModified ?: DatabaseDateFormatter.iso8601())
    }

    override fun getFoldersModifiedSince(since: String): List<BookmarkFolder> {
        val folders = savedSitesEntitiesDao.allEntitiesByTypeSync(FOLDER).filter { it.modifiedAfter(since) }
        logcat { "Sync-Bookmarks: folders modified since $since are ${folders.map { it.entityId }}" }
        return folders.map { mapBookmarkFolder(it) }
    }

    private fun mapBookmarkFolder(entity: Entity): BookmarkFolder {
        val relation = savedSitesRelationsDao.relationByEntityId(entity.entityId)
        val numFolders = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
        val numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
        val lastModified = entity.lastModified ?: null
        return BookmarkFolder(
            entity.entityId,
            entity.title,
            relation?.folderId ?: "",
            numFolders = numFolders,
            numBookmarks = numBookmarks,
            lastModified = lastModified,
            deleted = entity.deletedFlag(),
        )
    }

    private fun mapToBookmark(entity: Entity): Bookmark? {
        val relation = savedSitesRelationsDao.relationByEntityId(entity.entityId)
        return if (relation != null) {
            entity.mapToBookmark(relation.folderId)
        } else {
            // bookmark was deleted, we can make the parent id up
            entity.mapToBookmark(SavedSitesNames.BOOKMARKS_ROOT)
        }
    }

    override fun getBookmarksModifiedSince(since: String): List<Bookmark> {
        val bookmarks = savedSitesEntitiesDao.allEntitiesByTypeSync(BOOKMARK).filter { it.modifiedAfter(since) }
        logcat { "Sync-Bookmarks: bookmarks modified since $since are ${bookmarks.map { it.entityId }}" }
        return bookmarks.map { mapToBookmark(it)!! }
    }

    private fun getBookmarkById(id: String): Bookmark? {
        val bookmark = savedSitesEntitiesDao.entityById(id)
        return if (bookmark != null) {
            savedSitesRelationsDao.relationByEntityId(bookmark.entityId)?.let {
                bookmark.mapToBookmark(it.folderId)
            }
        } else {
            null
        }
    }

    override fun getFolderDiff(folderId: String): SyncFolderChildren {
        val entitiesId = savedSitesRelationsDao.relationsByFolderId(folderId).map { it.entityId }
        val existingEntities = savedSitesEntitiesDao.allEntitiesInFolderSync(folderId)
        val deletedChildren = existingEntities.filter { it.deleted }.map { it.entityId }
        val childrenLocal = entitiesId.filterNot { deletedChildren.contains(it) }

        val metadata = savedSitesSyncMetadataDao.get(folderId)
            // no response stored for this folder, add children in insert modifier too
            ?: return SyncFolderChildren(current = childrenLocal, insert = childrenLocal, remove = deletedChildren)

        if (metadata.childrenResponse == null) {
            // no response stored for this folder, add children in insert modifier too
            return SyncFolderChildren(current = childrenLocal, insert = childrenLocal, remove = deletedChildren)
        }
        val childrenResponse = stringListAdapter.fromJson(metadata.childrenResponse!!)
            ?: // can't parse response, treat is as new
            return SyncFolderChildren(current = childrenLocal, insert = childrenLocal, remove = deletedChildren)

        if (childrenResponse == childrenLocal) {
            // both lists are the same, nothing has changed
            return SyncFolderChildren(current = childrenLocal, insert = emptyList(), remove = deletedChildren)
        }

        val notInResponse = childrenLocal.minus(childrenResponse.toSet()) // to be added to insert
        val notInLocal = childrenResponse.minus(childrenLocal.toSet()) // to be added to deleted
        val deleted = deletedChildren.plus(notInLocal).distinct()
        return SyncFolderChildren(current = childrenLocal, insert = notInResponse, remove = deleted)
    }

    private fun confirmPendingFolderRequests() {
        logcat { "Sync-Bookmarks-Metadata: updating children metadata column with values from request column" }
        savedSitesSyncMetadataDao.confirmAllChildrenRequests()
    }

    override fun addRequestMetadata(folders: List<SyncSavedSitesRequestEntry>) {
        val children = folders.filter { it.folder != null }.map {
            val storedMetadata = savedSitesSyncMetadataDao.get(it.id)
            storedMetadata?.copy(childrenRequest = stringListAdapter.toJson(it.folder?.children?.current))
                ?: SavedSitesSyncMetadataEntity(it.id, null, stringListAdapter.toJson(it.folder?.children?.current))
        }
        logcat { "Sync-Bookmarks-Metadata: adding children request metadata for folders: $children" }
        savedSitesSyncMetadataDao.addOrUpdate(children)
    }

    override fun discardRequestMetadata() {
        logcat { "Sync-Bookmarks-Metadata: removing all local metadata." }
        savedSitesSyncMetadataDao.discardRequestMetadata()
    }

    // for all folders in the payload that are not deleted
    // add children to children column
    // for all items in the metadata table
    // copy request columns to children
    override fun addResponseMetadata(entities: List<SyncSavedSitesResponseEntry>) {
        if (entities.isEmpty()) {
            confirmPendingFolderRequests()
        } else {
            val allFolders = entities.filter { it.isFolder() }
            val folders = allFolders.filter { it.deleted == null }
            if (folders.isEmpty()) {
                logcat { "Sync-Bookmarks-Metadata: no folders received in the response, nothing to add" }
                confirmPendingFolderRequests()
            } else {
                val children = folders.map {
                    val response = stringListAdapter.toJson(it.folder?.children)
                    logcat { "Sync-Bookmarks-Metadata: new children response $response for ${it.id}" }
                    SavedSitesSyncMetadataEntity(it.id, response, null)
                }
                logcat { "Sync-Bookmarks-Metadata: storing children response metadata for: $children" }
                savedSitesSyncMetadataDao.addResponseMetadata(children)
            }

            val deletedFolders = allFolders.filter { it.deleted != null }.map { it.id }
            if (deletedFolders.isNotEmpty()) {
                logcat { "Sync-Bookmarks-Metadata: deleting metadata for deleted folders $deletedFolders" }
                savedSitesSyncMetadataDao.deleteMetadata(deletedFolders)
            }
        }
    }

    override fun removeMetadata() {
        savedSitesSyncMetadataDao.removeAll()
    }

    override fun fixOrphans(): Boolean {
        val orphans = savedSitesRelationsDao.getOrphans()
        return if (orphans.isNotEmpty()) {
            logcat { "Sync-Bookmarks: Found ${orphans.size} orphans $orphans attaching them to bookmarks root" }
            orphans.map { Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = it.entityId) }.also {
                savedSitesRelationsDao.insertList(it)
            }
            true
        } else {
            logcat { "Sync-Bookmarks: Orphans not present" }
            false
        }
    }

    override fun pruneDeleted() {
        logcat { "Sync-Bookmarks: pruning soft deleted entities and metadata" }
        savedSitesEntitiesDao.allDeleted().forEach {
            savedSitesRelationsDao.deleteRelationByEntity(it.entityId)
            savedSitesEntitiesDao.deletePermanently(it)
            savedSitesSyncMetadataDao.remove(it.entityId)
        }
    }

    // entities that were present before deduplication need to be sent in the next sync operation
    // we do that by setting the lastModified to startTimestamp + 1 second
    // we want to make sure all parents from those entities are also sent
    // we will find all the parents of the entities until we find bookmarks root
    // if one of those folders
    // if not present -> keep going up until the first parent folder is in the response table
    // for bookmarks -> is parent folder in metadata table? -> mark bookmark and parent
    // if not present -> keep going up until the first parent folder is in the response table
    override fun setLocalEntitiesForNextSync(startTimestamp: String) {
        logcat { "Sync-Bookmarks: looking for folders that need modifiedSince updated after a Deduplication" }
        val entitiesToUpdate = mutableListOf<String>()
        val modifiedSince = OffsetDateTime.now().plusSeconds(1)
        getEntitiesModifiedBefore(startTimestamp).forEach {
            traverseParents(it, entitiesToUpdate)
        }

        logcat { "Sync-Bookmarks: updating $entitiesToUpdate modifiedSince to $modifiedSince" }
        savedSitesEntitiesDao.updateModified(entitiesToUpdate.toList(), DatabaseDateFormatter.iso8601(modifiedSince))
    }

    override fun getInvalidSavedSites(): List<SavedSite> {
        return savedSitesEntitiesStore.invalidEntitiesIds.takeIf { it.isNotEmpty() }?.let { ids ->
            getSavedSites(ids)
        } ?: emptyList()
    }

    override fun markSavedSitesAsInvalid(ids: List<String>) {
        logcat(INFO) { "Sync-Bookmarks: Storing invalid items: $ids" }
        savedSitesEntitiesStore.invalidEntitiesIds = ids
    }

    private fun getSavedSites(ids: List<String>): List<SavedSite> {
        return savedSitesEntitiesDao.entities(ids).filter { it.type == BOOKMARK }.map {
            it.mapToSavedSite()
        }
    }

    private fun traverseParents(entity: String, entitiesToUpdate: MutableList<String>) {
        // find parent of each entity
        entitiesToUpdate.add(entity)
        val parents = savedSitesRelationsDao.relationsByEntityId(entity)
        parents.forEach {
            traverseParents(it.folderId, entitiesToUpdate)
        }
    }

    private fun getEntitiesModifiedBefore(date: String): List<String> {
        val entities = savedSitesEntitiesDao.entities()
            .filter { it.modifiedBefore(date) }
            .filterNot { it.lastModified.isNullOrEmpty() }
            .map { it.entityId }
        logcat { "Sync-Bookmarks: entities modified before $date are $entities" }
        return entities
    }

    private fun Entity.modifiedBefore(since: String): Boolean {
        return if (this.lastModified == null) {
            false
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val beforeModified = OffsetDateTime.parse(since)
            entityModified.isBefore(beforeModified)
        }
    }

    private fun Entity.modifiedAfter(since: String): Boolean {
        return if (this.lastModified == null) {
            false
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(since)
            entityModified.isAfter(sinceModified)
        }
    }
}
