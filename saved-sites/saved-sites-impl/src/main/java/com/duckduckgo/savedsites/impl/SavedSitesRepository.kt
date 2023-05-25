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

import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.api.models.FolderBranch
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.threeten.bp.OffsetDateTime
import timber.log.Timber

class RealSavedSitesRepository(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : SavedSitesRepository {

    override fun getSavedSites(folderId: String): Flow<SavedSites> {
        return if (folderId == SavedSitesNames.BOOKMARKS_ROOT) {
            getFavorites().combine(getFolderContent(folderId)) { favorites, folderContent ->
                SavedSites(favorites = favorites.distinct(), bookmarks = folderContent.first, folders = folderContent.second)
            }
        } else {
            getFolderContent(folderId).map {
                SavedSites(favorites = emptyList(), bookmarks = it.first, folders = it.second)
            }
        }
    }

    override fun getFolderContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
        return savedSitesEntitiesDao.entitiesInFolder(folderId).map { entities ->
            val bookmarks = mutableListOf<Bookmark>()
            val folders = mutableListOf<BookmarkFolder>()
            entities.map { entity ->
                mapEntity(entity, folderId, bookmarks, folders)
            }
            Pair(bookmarks.distinct(), folders.distinct())
        }
            .flowOn(dispatcherProvider.io())
    }

    override fun getFolderContentSync(folderId: String): Pair<List<Bookmark>, List<BookmarkFolder>> {
        val entities = savedSitesEntitiesDao.entitiesInFolderSync(folderId)
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()
        entities.forEach { entity ->
            mapEntity(entity, folderId, bookmarks, folders)
        }
        return Pair(bookmarks.distinct(), folders.distinct())
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
        return savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_ROOT).map { relations ->
            relations.mapIndexed { index, relation ->
                savedSitesEntitiesDao.entityById(relation.entityId)!!.mapToFavorite(index)
            }
        }.flowOn(dispatcherProvider.io())
    }

    override fun getFavoritesObservable(): Single<List<Favorite>> {
        return savedSitesRelationsDao.relationsObservable(SavedSitesNames.FAVORITES_ROOT).map { relations ->
            relations.mapIndexed { index, relation ->
                savedSitesEntitiesDao.entityById(relation.entityId)!!.mapToFavorite(index)
            }
        }
    }

    override fun getFavoritesSync(): List<Favorite> {
        return savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).mapToFavorites()
    }

    override fun getFavoritesCountByDomain(domain: String): Int {
        return savedSitesRelationsDao.countFavouritesByUrl(domain)
    }

    override fun getFavorite(url: String): Favorite? {
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.url == url }
    }

    override fun getFavoriteById(id: String): Favorite? {
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.id == id }
    }

    override fun getBookmarks(): Flow<List<Bookmark>> {
        return savedSitesEntitiesDao.entitiesByType(BOOKMARK).map { entities -> entities.mapToBookmarks() }
    }

    override fun getBookmarksObservable(): Single<List<Bookmark>> {
        return savedSitesEntitiesDao.entitiesByTypeObservable(BOOKMARK).map { entities -> entities.mapToBookmarks() }
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
            savedSitesRelationsDao.relationByEntityId(bookmark.entityId)?.let {
                bookmark.mapToBookmark(it.folderId)
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
        val idOrFallback = id.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
        val titleOrFallback = title.takeIf { it.isNotEmpty() } ?: url
        val existentBookmark = savedSitesEntitiesDao.entityByUrl(url)
        val lastModifiedOrFallback = lastModified ?: DatabaseDateFormatter.iso8601()
        return if (existentBookmark == null) {
            val entity = Entity(entityId = idOrFallback, title = titleOrFallback, url = url, type = BOOKMARK, lastModified = lastModifiedOrFallback)
            savedSitesEntitiesDao.insert(entity)
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = entity.entityId))
            savedSitesEntitiesDao.updateModified(SavedSitesNames.BOOKMARKS_ROOT, lastModifiedOrFallback)
            savedSitesEntitiesDao.updateModified(SavedSitesNames.FAVORITES_ROOT, lastModifiedOrFallback)
            getFavoriteById(entity.entityId)!!
        } else {
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = existentBookmark.entityId))
            savedSitesEntitiesDao.updateModified(SavedSitesNames.FAVORITES_ROOT, lastModifiedOrFallback)
            savedSitesEntitiesDao.updateModified(id, lastModifiedOrFallback)
            getFavorite(url)!!
        }
    }

    override fun insert(savedSite: SavedSite): SavedSite {
        Timber.d("Sync-Feature: inserting Saved Site $savedSite")
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
                savedSitesRelationsDao.insert(Relation(folderId = savedSite.parentId, entityId = entity.entityId))
                savedSitesEntitiesDao.updateModified(savedSite.parentId, savedSite.lastModified ?: DatabaseDateFormatter.iso8601())
                entity.mapToBookmark(savedSite.parentId)
            }
        }
    }

    override fun delete(savedSite: SavedSite) {
        when (savedSite) {
            is Bookmark -> deleteBookmark(savedSite)
            is Favorite -> deleteFavorite(savedSite)
        }
    }

    private fun deleteFavorite(favorite: Favorite) {
        savedSitesRelationsDao.deleteRelationByEntity(favorite.id, SavedSitesNames.FAVORITES_ROOT)
        savedSitesEntitiesDao.updateModified(SavedSitesNames.FAVORITES_ROOT)
    }

    private fun deleteBookmark(bookmark: Bookmark) {
        if (savedSitesRelationsDao.countFavouritesByUrl(bookmark.url) > 0) {
            savedSitesEntitiesDao.updateModified(SavedSitesNames.FAVORITES_ROOT)
        }
        savedSitesEntitiesDao.updateModified(bookmark.parentId)
        savedSitesEntitiesDao.delete(bookmark.id)
        savedSitesRelationsDao.deleteRelationByEntity(bookmark.id)
    }

    override fun updateFavourite(favorite: Favorite) {
        savedSitesEntitiesDao.entityById(favorite.id)?.let { entity ->
            savedSitesEntitiesDao.update(Entity(entity.entityId, favorite.title, favorite.url, BOOKMARK))

            val favorites = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).mapIndexed { index, entity ->
                entity.mapToFavorite(index)
            }.toMutableList()

            val currentFavorite = favorites.find { it.id == favorite.id }
            val currentIndex = favorites.indexOf(currentFavorite)
            if (currentIndex < 0) return
            favorites.removeAt(currentIndex)
            favorites.add(favorite.position, favorite)

            updateWithPosition(favorites)
        }
    }

    override fun updateBookmark(
        bookmark: Bookmark,
        fromFolderId: String,
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
                lastModified = folder.lastModified ?: DatabaseDateFormatter.iso8601(),
            ),
        )

        // has folder parent changed?
        if (oldFolder.parentId != folder.id) {
            savedSitesRelationsDao.deleteRelationByEntity(folder.id)
            savedSitesRelationsDao.insert(Relation(folderId = folder.parentId, entityId = folder.id))
        }
    }

    override fun replaceFolder(
        remoteId: String,
        localId: String,
    ) {
        savedSitesEntitiesDao.updateId(localId, remoteId)
        savedSitesRelationsDao.updateEntityId(localId, remoteId)
        savedSitesRelationsDao.updateFolderId(localId, remoteId)
    }

    override fun replaceFolderContent(
        folder: BookmarkFolder,
        localId: String,
    ) {
        savedSitesEntitiesDao.updateId(localId, folder.id)
        savedSitesRelationsDao.updateEntityId(localId, folder.id)
        savedSitesRelationsDao.updateFolderId(localId, folder.id)
        update(folder)
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

    override fun replaceFavourite(
        favorite: Favorite,
        localId: String,
    ) {
        savedSitesEntitiesDao.updateId(localId, favorite.id)
        savedSitesRelationsDao.updateEntityId(localId, favorite.id)
        updateFavourite(favorite)
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
        return savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT).size.toLong()
    }

    override fun lastModified(): Flow<String> {
        return savedSitesEntitiesDao.lastModified().map { it.entityId }
    }

    override fun getFoldersModifiedSince(since: String): List<BookmarkFolder> {
        val folders = savedSitesEntitiesDao.allEntitiesByTypeSync(FOLDER).filter { it.modifiedSince(since) }
        Timber.d("Sync-Feature: folders modified since $since are $folders")
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

    override fun getBookmarksModifiedSince(since: String): List<Bookmark> {
        val bookmarks = savedSitesEntitiesDao.allEntitiesByTypeSync(BOOKMARK).filter { it.modifiedSince(since) }
        Timber.d("Sync-Feature: bookmarks modified since $since are $bookmarks")
        return bookmarks.map { mapToBookmark(it)!! }
    }

    override fun pruneDeleted() {
        savedSitesEntitiesDao.allDeleted().forEach {
            savedSitesRelationsDao.deleteRelationByEntity(it.entityId)
            savedSitesEntitiesDao.deletePermanently(it)
        }
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

    override fun updateWithPosition(favorites: List<Favorite>) {
        savedSitesRelationsDao.delete(SavedSitesNames.FAVORITES_ROOT)
        val relations = favorites.map { Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = it.id) }
        savedSitesRelationsDao.insertList(relations)
        savedSitesEntitiesDao.updateModified(SavedSitesNames.FAVORITES_ROOT)
    }

    private fun Entity.mapToBookmark(relationId: String): Bookmark =
        Bookmark(this.entityId, this.title, this.url.orEmpty(), relationId, this.lastModified, deleted = this.deletedFlag())

    private fun Entity.mapToBookmarkFolder(relationId: String): BookmarkFolder =
        BookmarkFolder(id = this.entityId, name = this.title, parentId = relationId, lastModified = this.lastModified, deleted = this.deletedFlag())

    private fun Entity.mapToFavorite(index: Int = 0): Favorite =
        Favorite(this.entityId, this.title, this.url.orEmpty(), lastModified = this.lastModified, index, this.deletedFlag())

    private fun Entity.modifiedSince(since: String): Boolean {
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

    private fun List<Entity>.mapToBookmarks(folderId: String = SavedSitesNames.BOOKMARKS_ROOT): List<Bookmark> =
        this.map { it.mapToBookmark(folderId) }

    private fun List<Entity>.mapToFavorites(): List<Favorite> = this.mapIndexed { index, relation -> relation.mapToFavorite(index) }

    private fun SavedSite.titleOrFallback(): String = this.title.takeIf { it.isNotEmpty() } ?: this.url
}
