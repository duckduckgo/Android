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

import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.api.models.FolderBranch
import com.duckduckgo.savedsites.api.models.FolderTreeItem
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSites
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import io.reactivex.Single
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import logcat.logcat

class RealSavedSitesRepository(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val favoritesDelegate: FavoritesDelegate,
    private val relationsReconciler: RelationsReconciler,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : SavedSitesRepository {

    override fun getSavedSites(folderId: String): Flow<SavedSites> {
        return getFavorites().combine(getFolderContent(folderId)) { favorites, folderContent ->
            SavedSites(favorites = favorites.distinct(), bookmarks = folderContent)
        }
    }

    private fun getFolderContent(folderId: String): Flow<List<Any>> {
        return savedSitesEntitiesDao.entitiesInFolder(folderId).map { entities ->
            val bookmarks = mutableListOf<Any>()
            entities.map { entity ->
                mapEntity(entity, folderId, bookmarks)
            }
            bookmarks.distinct()
        }
            .flowOn(dispatcherProvider.io())
    }

    override fun getFolderTreeItems(folderId: String): List<FolderTreeItem> {
        val entities = savedSitesEntitiesDao.entitiesInFolderSync(folderId)
        val combinedList = mutableListOf<FolderTreeItem>()

        entities.forEach { entity ->
            if (entity.type == FOLDER) {
                val item = entity.mapToBookmarkFolder(folderId)
                combinedList.add(FolderTreeItem(item.id, item.name, item.parentId, null))
            } else {
                val item = entity.mapToBookmark(folderId)
                combinedList.add(FolderTreeItem(item.id, item.title, item.parentId, item.url))
            }
        }
        return combinedList
    }

    private fun mapEntity(
        entity: Entity,
        folderId: String,
        bookmarks: MutableList<Any>,
    ) {
        if (entity.type == FOLDER) {
            val numFolders = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
            val numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
            bookmarks.add(
                BookmarkFolder(entity.entityId, entity.title, folderId, numBookmarks, numFolders, entity.lastModified, entity.deletedFlag()),
            )
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

    override fun getFolderTree(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
    ): List<BookmarkFolderItem> {
        val rootFolder = getFolder(SavedSitesNames.BOOKMARKS_ROOT)
        return if (rootFolder != null) {
            val rootFolderItem = BookmarkFolderItem(0, rootFolder, rootFolder.id == selectedFolderId)
            val folders = mutableListOf(rootFolderItem)
            val folderDepth = traverseFolderWithDepth(1, folders, SavedSitesNames.BOOKMARKS_ROOT, selectedFolderId = selectedFolderId, currentFolder)
            if (currentFolder != null) {
                folderDepth.filterNot { it.bookmarkFolder == currentFolder }
            } else {
                folderDepth
            }
        } else {
            emptyList()
        }
    }

    override fun getBookmarksTree(): List<Bookmark> {
        val bookmarks = mutableListOf<Bookmark>()
        val bookmarkEntities = savedSitesEntitiesDao.entitiesByTypeSync(BOOKMARK)
        bookmarkEntities.forEach {
            val relation = savedSitesRelationsDao.relationByEntityId(it.entityId)
            if (relation != null) {
                bookmarks.add(it.mapToBookmark(relation.folderId))
            }
        }
        return bookmarks
    }

    private fun traverseFolderWithDepth(
        depth: Int = 0,
        folders: MutableList<BookmarkFolderItem>,
        folderId: String,
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
    ): List<BookmarkFolderItem> {
        getFolders(folderId).map {
            if (it.id != currentFolder?.id) {
                folders.add(BookmarkFolderItem(depth, it, it.id == selectedFolderId))
                traverseFolderWithDepth(depth + 1, folders, it.id, selectedFolderId = selectedFolderId, currentFolder)
            }
        }
        return folders
    }

    override fun insertFolderBranch(branchToInsert: FolderBranch) {
        with(branchToInsert) {
            folders.forEach {
                insert(it)
            }
            bookmarks.forEach {
                insert(it)
            }
        }
    }

    override fun getFolderBranch(folder: BookmarkFolder): FolderBranch {
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf(folder)
        val folderContent = traverseBranch(bookmarks, folders, folder.id)
        return FolderBranch(folderContent.first, folderContent.second)
    }

    private fun traverseBranch(
        bookmarks: MutableList<Bookmark>,
        folders: MutableList<BookmarkFolder>,
        folderId: String,
    ): Pair<List<Bookmark>, List<BookmarkFolder>> {
        val folderContent = folderContent(folderId)
        bookmarks.addAll(folderContent.first)
        folders.addAll(folderContent.second)
        folderContent.second.forEach {
            traverseBranch(bookmarks, folders, it.id)
        }
        return Pair(bookmarks, folders)
    }

    private fun getFolders(folderId: String): List<BookmarkFolder> {
        return savedSitesEntitiesDao.entitiesInFolder(folderId, FOLDER).map { entity ->
            entity.mapToBookmarkFolder(folderId)
        }
    }

    private fun folderContent(folderId: String): Pair<List<Bookmark>, List<BookmarkFolder>> {
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()
        savedSitesEntitiesDao.entitiesInFolderSync(folderId).forEach { entity ->
            if (entity.type == FOLDER) {
                folders.add(entity.mapToBookmarkFolder(folderId))
            } else {
                bookmarks.add(entity.mapToBookmark(folderId))
            }
        }
        return Pair(bookmarks, folders)
    }

    override fun deleteFolderBranch(folder: BookmarkFolder): FolderBranch {
        val folderContent = getFolderBranch(folder)
        folderContent.folders.forEach {
            delete(it)
        }
        folderContent.bookmarks.forEach {
            delete(it)
        }

        delete(folder)
        return folderContent
    }

    override fun getFavorites(): Flow<List<Favorite>> {
        return favoritesDelegate.getFavorites()
    }

    override fun getFavoritesObservable(): Single<List<Favorite>> {
        return favoritesDelegate.getFavoritesObservable()
    }

    override fun getFavoritesSync(): List<Favorite> {
        return favoritesDelegate.getFavoritesSync()
    }

    override fun getFavoritesCountByDomain(domain: String): Int {
        return favoritesDelegate.getFavoritesCountByDomain(domain)
    }

    override fun getFavorite(url: String): Favorite? {
        return favoritesDelegate.getFavorite(url)
    }

    override fun getFavoriteById(id: String): Favorite? {
        return favoritesDelegate.getFavoriteById(id)
    }

    override fun getBookmarks(): Flow<List<Bookmark>> {
        return savedSitesEntitiesDao.entitiesByType(BOOKMARK).map { entities ->
            entities.map { entity ->
                val relationId = savedSitesRelationsDao.relationByEntityId(entity.entityId)?.folderId ?: SavedSitesNames.BOOKMARKS_ROOT
                entity.mapToBookmark(relationId)
            }
        }
    }

    override fun getBookmarksObservable(): Single<List<Bookmark>> {
        return savedSitesEntitiesDao.entitiesByTypeObservable(BOOKMARK).map { entities ->
            entities.map { entity ->
                val relationId = savedSitesRelationsDao.relationByEntityId(entity.entityId)?.folderId ?: SavedSitesNames.BOOKMARKS_ROOT
                entity.mapToBookmark(relationId)
            }
        }
    }

    override fun getBookmark(url: String): Bookmark? {
        val bookmark = savedSitesEntitiesDao.entityByUrl(url)
        return if (bookmark != null) {
            savedSitesRelationsDao.relationByEntityId(bookmark.entityId)?.let {
                bookmark.mapToBookmark(it.folderId)
            }
        } else {
            null
        }
    }

    override fun getBookmarkById(id: String): Bookmark? {
        val bookmark = savedSitesEntitiesDao.entityById(id)
        return if (bookmark != null) {
            val isFavourite = getFavoriteById(id)
            savedSitesRelationsDao.relationByEntityId(bookmark.entityId)?.let {
                bookmark.mapToBookmark(it.folderId, isFavourite != null)
            }
        } else {
            null
        }
    }

    override fun getSavedSite(id: String): SavedSite? {
        val savedSite = savedSitesEntitiesDao.entityById(id)
        return if (savedSite != null) {
            getFavoriteById(id) ?: getBookmarkById(id)
        } else {
            null
        }
    }

    override fun hasBookmarks(): Boolean {
        return bookmarksCount() > 0
    }

    override fun hasFavorites(): Boolean {
        return favoritesCount() > 0
    }

    override fun insertBookmark(
        url: String,
        title: String,
    ): Bookmark {
        val titleOrFallback = title.takeIf { it.isNotEmpty() } ?: url
        val entity = Entity(title = titleOrFallback, url = url, type = BOOKMARK)
        savedSitesEntitiesDao.insert(entity)
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
        savedSitesEntitiesDao.updateModified(SavedSitesNames.BOOKMARKS_ROOT, entity.lastModified!!)

        return entity.mapToBookmark(SavedSitesNames.BOOKMARKS_ROOT)
    }

    override fun insertFavorite(
        id: String,
        url: String,
        title: String,
        lastModified: String?,
    ): Favorite {
        return favoritesDelegate.insertFavorite(id, url, title, lastModified)
    }

    override fun insert(savedSite: SavedSite): SavedSite {
        val titleOrFallback = savedSite.titleOrFallback()
        return when (savedSite) {
            is Favorite -> {
                return insertFavorite(savedSite.id, savedSite.url, savedSite.title, savedSite.lastModified)
            }

            is Bookmark -> {
                // a bookmark will have a parent folder that we must respect
                val entity = Entity(
                    entityId = savedSite.id,
                    title = titleOrFallback,
                    url = savedSite.url,
                    type = BOOKMARK,
                    lastModified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
                )
                savedSitesEntitiesDao.insert(entity)
                if (savedSitesRelationsDao.relation(savedSite.parentId, entityId = entity.entityId) == null) {
                    savedSitesRelationsDao.insert(Relation(folderId = savedSite.parentId, entityId = entity.entityId))
                }
                savedSitesEntitiesDao.updateModified(savedSite.parentId, savedSite.lastModified ?: DatabaseDateFormatter.iso8601())
                entity.mapToBookmark(savedSite.parentId)
            }
        }
    }

    override fun delete(savedSite: SavedSite, deleteBookmark: Boolean) {
        when (savedSite) {
            is Bookmark -> deleteBookmark(savedSite)
            is Favorite -> {
                if (deleteBookmark) {
                    getBookmark(savedSite.url)?.let {
                        deleteBookmark(it)
                    }
                } else {
                    deleteFavorite(savedSite)
                }
            }
        }
    }

    private fun deleteFavorite(favorite: Favorite) {
        favoritesDelegate.deleteFavorite(favorite)
    }

    private fun deleteBookmark(bookmark: Bookmark) {
        val folders = savedSitesRelationsDao.relationsByEntityId(bookmark.id)

        savedSitesRelationsDao.deleteRelationByEntity(bookmark.id)
        savedSitesEntitiesDao.delete(bookmark.id)

        folders.forEach {
            savedSitesEntitiesDao.updateModified(it.folderId)
        }
    }

    override fun updateFavourite(favorite: Favorite) {
        favoritesDelegate.updateFavourite(favorite)
    }

    override fun updateBookmark(
        bookmark: Bookmark,
        fromFolderId: String,
        updateFavorite: Boolean,
    ) {
        if (bookmark.parentId != fromFolderId) {
            // bookmark has moved to another folder
            savedSitesRelationsDao.deleteRelationByEntityAndFolder(bookmark.id, fromFolderId)
            savedSitesRelationsDao.insert(Relation(folderId = bookmark.parentId, entityId = bookmark.id))
        }

        val lastModified = DatabaseDateFormatter.iso8601()
        savedSitesEntitiesDao.update(
            Entity(
                bookmark.id,
                bookmark.title,
                bookmark.url,
                BOOKMARK,
                lastModified,
            ),
        )

        if (updateFavorite) {
            if (bookmark.isFavorite) {
                insertFavorite(bookmark.id, bookmark.url, bookmark.title)
            } else {
                deleteFavorite(
                    Favorite(
                        id = bookmark.id,
                        title = bookmark.title,
                        url = bookmark.url,
                        lastModified = bookmark.lastModified,
                        position = 0,
                    ),
                )
            }
        }

        savedSitesEntitiesDao.updateModified(fromFolderId, lastModified)
        savedSitesEntitiesDao.updateModified(bookmark.parentId, lastModified)
    }

    override fun insert(folder: BookmarkFolder): BookmarkFolder {
        val entity = Entity(
            entityId = folder.id,
            title = folder.name,
            url = "",
            lastModified = folder.lastModified ?: DatabaseDateFormatter.iso8601(),
            type = FOLDER,
        )
        savedSitesEntitiesDao.insert(entity)
        savedSitesRelationsDao.insert(Relation(folderId = folder.parentId, entityId = folder.id))
        savedSitesEntitiesDao.updateModified(folder.parentId, folder.lastModified ?: DatabaseDateFormatter.iso8601())
        return folder
    }

    override fun update(folder: BookmarkFolder) {
        val oldFolder = getFolder(folder.id) ?: return

        savedSitesEntitiesDao.update(
            Entity(
                entityId = folder.id,
                title = folder.name,
                url = "",
                type = FOLDER,
                lastModified = DatabaseDateFormatter.iso8601(),
            ),
        )

        // has folder parent changed?
        if (oldFolder.parentId != folder.parentId) {
            savedSitesRelationsDao.deleteRelationByEntity(folder.id)
            savedSitesRelationsDao.insert(Relation(folderId = folder.parentId, entityId = folder.id))
            savedSitesEntitiesDao.updateModified(folder.parentId)
            savedSitesEntitiesDao.updateModified(oldFolder.parentId)
        }
    }

    override fun replaceFolderContent(
        folder: BookmarkFolder,
        oldId: String,
    ) {
        // if the folder existed, we don't update the modified since so the next sync trigger it's picked up
        savedSitesEntitiesDao.updateId(oldId, folder.id)
        savedSitesRelationsDao.updateEntityId(oldId, folder.id)
        savedSitesRelationsDao.updateFolderId(oldId, folder.id)

        val oldFolder = getFolder(folder.id) ?: return

        savedSitesEntitiesDao.update(
            Entity(
                entityId = folder.id,
                title = folder.name,
                url = "",
                type = FOLDER,
                lastModified = folder.lastModified ?: DatabaseDateFormatter.iso8601(),
            ),
        )

        // has folder parent changed?
        if (folder.parentId.isNotEmpty() && oldFolder.parentId != folder.parentId) {
            savedSitesRelationsDao.deleteRelationByEntity(folder.id)
            savedSitesRelationsDao.insert(Relation(folderId = folder.parentId, entityId = folder.id))
        }
    }

    override fun delete(folder: BookmarkFolder) {
        val relations = savedSitesEntitiesDao.entitiesInFolderSync(folder.id)
        val entities = mutableListOf<Entity>()
        relations.forEach {
            entities.add(it)
        }

        savedSitesRelationsDao.deleteRelationByEntity(folder.id)
        savedSitesRelationsDao.delete(folder.id)
        entities.forEach {
            savedSitesEntitiesDao.delete(it.entityId)
        }
        savedSitesEntitiesDao.delete(folder.id)
        savedSitesEntitiesDao.updateModified(folder.parentId)
    }

    override fun updateFolderRelation(
        folderId: String,
        entities: List<String>,
    ) {
        val reconciledList = relationsReconciler.reconcileRelations(
            originalRelations = savedSitesRelationsDao.relationsByFolderId(folderId).map { it.entityId },
            newFolderRelations = entities,
        )
        savedSitesRelationsDao.replaceBookmarkFolder(folderId, reconciledList)
    }

    override fun getFolder(folderId: String): BookmarkFolder? {
        val entity = savedSitesEntitiesDao.entityById(folderId)
        val relation = savedSitesRelationsDao.relationByEntityId(folderId)

        return if (entity != null) {
            val numFolders = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
            val numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
            BookmarkFolder(
                folderId,
                entity.title,
                relation?.folderId ?: "",
                numFolders = numFolders,
                numBookmarks = numBookmarks,
                lastModified = entity.lastModified,
                deleted = entity.deletedFlag(),
            )
        } else {
            null
        }
    }

    override fun getFolderByName(folderName: String): BookmarkFolder? {
        val entity = savedSitesEntitiesDao.entityByName(folderName)
        return if (entity != null) {
            val relation = savedSitesRelationsDao.relationByEntityId(entity.entityId)
            val numFolders = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
            val numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
            BookmarkFolder(
                entity.entityId,
                entity.title,
                relation?.folderId ?: "",
                numFolders = numFolders,
                numBookmarks = numBookmarks,
                lastModified = entity.lastModified,
                deleted = entity.deletedFlag(),
            )
        } else {
            null
        }
    }

    override fun deleteAll() {
        savedSitesRelationsDao.deleteAll()
        savedSitesEntitiesDao.deleteAll()
    }

    override fun bookmarksCount(): Long {
        return savedSitesEntitiesDao.entitiesByTypeSync(BOOKMARK).size.toLong()
    }

    override fun favoritesCount(): Long {
        return favoritesDelegate.favoritesCount()
    }

    override fun lastModified(): Flow<String> {
        return savedSitesEntitiesDao.lastModified().map { it.entityId }
    }

    override fun pruneDeleted() {
        logcat { "Sync-Bookmarks: pruning soft deleted entities" }
        savedSitesEntitiesDao.allDeleted().forEach {
            savedSitesRelationsDao.deleteRelationByEntity(it.entityId)
            savedSitesEntitiesDao.deletePermanently(it)
        }
    }

    override fun updateWithPosition(favorites: List<Favorite>) {
        favoritesDelegate.updateWithPosition(favorites)
    }

    private fun Entity.mapToBookmark(relationId: String, isFavourite: Boolean = false): Bookmark =
        Bookmark(this.entityId, this.title, this.url.orEmpty(), relationId, this.lastModified, deleted = this.deletedFlag(), isFavorite = isFavourite)

    private fun Entity.mapToBookmarkFolder(relationId: String): BookmarkFolder =
        BookmarkFolder(
            id = this.entityId,
            name = this.title,
            parentId = relationId,
            numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(this.entityId, BOOKMARK),
            numFolders = savedSitesRelationsDao.countEntitiesInFolder(this.entityId, FOLDER),
            lastModified = this.lastModified,
            deleted = this.deletedFlag(),
        )

    private fun Entity.modifiedAfter(since: String): Boolean {
        return if (this.lastModified == null) {
            false
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(since)
            entityModified.isAfter(sinceModified)
        }
    }

    private fun Entity.deletedFlag(): String? {
        return if (this.deleted) {
            this.lastModified
        } else {
            null
        }
    }

    private fun SavedSite.titleOrFallback(): String = this.title.takeIf { it.isNotEmpty() } ?: this.url
}
