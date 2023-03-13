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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RealSavedSitesRepository(
    private val savedSitesEntitiesDao: SavedSitesEntitiesDao,
    private val savedSitesRelationsDao: SavedSitesRelationsDao,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : SavedSitesRepository {

    override suspend fun getSavedSites(folderId: String): Flow<SavedSites> {
        return if (folderId == SavedSitesNames.BOOMARKS_ROOT) {
            getFavorites().combine(getFolderContent(folderId)) { favorites, folderContent ->
                SavedSites(favorites = favorites.distinct(), bookmarks = folderContent.first, folders = folderContent.second)
            }
        } else {
            getFolderContent(folderId).map {
                SavedSites(favorites = emptyList(), bookmarks = it.first, folders = it.second)
            }
        }
    }

    override suspend fun getFolderContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
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

    override suspend fun getFolderContentSync(folderId: String): Pair<List<Bookmark>, List<BookmarkFolder>> {
        val entities = savedSitesEntitiesDao.entitiesInFolderSync(folderId)
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()
        entities.forEach { entity ->
            mapEntity(entity, folderId, bookmarks, folders)
        }
        return Pair(bookmarks.distinct(), folders.distinct())
    }

    private fun mapEntity(entity: Entity, folderId: String, bookmarks: MutableList<Bookmark>, folders: MutableList<BookmarkFolder>) {
        if (entity.type == FOLDER) {
            val numFolders = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
            val numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
            folders.add(BookmarkFolder(entity.entityId, entity.title, folderId, numBookmarks, numFolders))
        } else {
            bookmarks.add(Bookmark(entity.entityId, entity.title, entity.url.orEmpty(), folderId))
        }
    }

    override suspend fun getFolderTree(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
    ): List<BookmarkFolderItem> {
        val rootFolder = getFolder(SavedSitesNames.BOOMARKS_ROOT)
        return if (rootFolder != null) {
            val rootFolderItem = BookmarkFolderItem(0, rootFolder, rootFolder.id == selectedFolderId)
            val folders = mutableListOf(rootFolderItem)
            val folderDepth = traverseFolderWithDepth(1, folders, SavedSitesNames.BOOMARKS_ROOT, selectedFolderId = selectedFolderId, currentFolder)
            if (currentFolder != null) {
                folderDepth.filterNot { it.bookmarkFolder == currentFolder }
            } else {
                folderDepth
            }
        } else {
            emptyList()
        }
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

    override suspend fun insertFolderBranch(branchToInsert: FolderBranch) {
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
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOMARKS_ROOT, entityId = entity.entityId))
        return entity.mapToBookmark(SavedSitesNames.BOOMARKS_ROOT)
    }

    override fun insertFavorite(
        url: String,
        title: String,
    ): Favorite {
        val titleOrFallback = title.takeIf { it.isNotEmpty() } ?: url
        val existentBookmark = savedSitesEntitiesDao.entityByUrl(url)
        if (existentBookmark == null) {
            val entity = Entity(title = titleOrFallback, url = url, type = BOOKMARK)
            savedSitesEntitiesDao.insert(entity)
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOMARKS_ROOT, entityId = entity.entityId))
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = entity.entityId))
        } else {
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = existentBookmark.entityId))
        }
        return getFavorite(url)!!
    }

    override fun insert(savedSite: SavedSite): SavedSite {
        val titleOrFallback = savedSite.titleOrFallback()
        return when (savedSite) {
            is Favorite -> {
                return insertFavorite(savedSite.url, savedSite.title)
            }

            is Bookmark -> {
                // a bookmark will have a parent folder that we must respect
                val entity = Entity(entityId = savedSite.id, title = titleOrFallback, url = savedSite.url, type = BOOKMARK)
                savedSitesEntitiesDao.insert(entity)
                savedSitesRelationsDao.insert(Relation(folderId = savedSite.parentId, entityId = entity.entityId))
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
    }

    private fun deleteBookmark(bookmark: Bookmark) {
        savedSitesEntitiesDao.delete(bookmark.id)
        savedSitesRelationsDao.deleteRelationByEntity(bookmark.id)
    }

    override fun update(savedSite: SavedSite) {
        when (savedSite) {
            is Bookmark -> updateBookmark(savedSite)
            is Favorite -> updateFavorite(savedSite)
        }
    }

    private fun updateBookmark(bookmark: Bookmark) {
        savedSitesEntitiesDao.entityById(bookmark.id)?.let { entity ->
            savedSitesRelationsDao.relationByEntityId(entity.entityId)?.let { relation ->
                if (relation.folderId != bookmark.parentId) {
                    // bookmark moved to another folder
                    savedSitesRelationsDao.deleteRelationByEntity(bookmark.id)
                    savedSitesRelationsDao.insert(Relation(folderId = bookmark.parentId, entityId = relation.entityId))
                }
                savedSitesEntitiesDao.update(Entity(relation.entityId, bookmark.title, bookmark.url, BOOKMARK))
            }
        }
    }

    private fun updateFavorite(favorite: Favorite) {
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

    override fun insert(folder: BookmarkFolder): BookmarkFolder {
        val entity = Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER)
        savedSitesEntitiesDao.insert(entity)
        savedSitesRelationsDao.insert(Relation(folderId = folder.parentId, entityId = folder.id))
        return folder
    }

    override fun update(folder: BookmarkFolder) {
        val oldFolder = getFolder(folder.id) ?: return

        savedSitesEntitiesDao.update(Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER))

        // has folder parent changed?
        if (oldFolder.parentId != folder.id) {
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
        savedSitesEntitiesDao.deleteList(entities.toList())
        savedSitesEntitiesDao.delete(folder.id)
    }

    override fun getFolder(folderId: String): BookmarkFolder? {
        val entity = savedSitesEntitiesDao.entityById(folderId)
        val relation = savedSitesRelationsDao.relationByEntityId(folderId)

        return if (entity != null) {
            val numFolders = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
            val numBookmarks = savedSitesRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
            BookmarkFolder(folderId, entity.title, relation?.folderId ?: "", numFolders = numFolders, numBookmarks = numBookmarks)
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
            BookmarkFolder(entity.entityId, entity.title, relation?.folderId ?: "", numFolders = numFolders, numBookmarks = numBookmarks)
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

    override fun updateWithPosition(favorites: List<Favorite>) {
        savedSitesRelationsDao.delete(SavedSitesNames.FAVORITES_ROOT)
        val relations = favorites.map { Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = it.id) }
        savedSitesRelationsDao.insertList(relations)
    }

    private fun Entity.mapToBookmark(relationId: String): Bookmark = Bookmark(this.entityId, this.title, this.url.orEmpty(), relationId)
    private fun Entity.mapToBookmarkFolder(relationId: String): BookmarkFolder =
        BookmarkFolder(this.entityId, this.title, relationId)

    private fun Entity.mapToFavorite(index: Int = 0): Favorite = Favorite(this.entityId, this.title, this.url.orEmpty(), index)
    private fun List<Entity>.mapToBookmarks(folderId: String = SavedSitesNames.BOOMARKS_ROOT): List<Bookmark> = this.map { it.mapToBookmark(folderId) }
    private fun List<Entity>.mapToFavorites(): List<Favorite> = this.mapIndexed { index, relation -> relation.mapToFavorite(index) }

    private fun SavedSite.titleOrFallback(): String = this.title.takeIf { it.isNotEmpty() } ?: this.url
}
