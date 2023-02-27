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

package com.duckduckgo.app.bookmarks.model

import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import io.reactivex.Single
import java.io.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface SavedSitesRepository {

    suspend fun getSavedSites(folderId: String): Flow<SavedSites>

    suspend fun getFolderContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>>

    suspend fun getFolderTree(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?
    ): List<BookmarkFolderItem>

    suspend fun insertFolderBranch(branchToInsert: FolderBranch)

    fun deleteFolderBranch(folder: BookmarkFolder): FolderBranch

    fun getFolderBranch(folder: BookmarkFolder): FolderBranch

    fun getBookmarks(): Flow<List<Bookmark>>

    fun getBookmarksObservable(): Single<List<Bookmark>>

    fun getBookmark(url: String): Bookmark?

    fun getFavorites(): Flow<List<Favorite>>

    fun getFavoritesObservable(): Single<List<Favorite>>

    fun getFavoritesSync(): List<Favorite>

    fun getFavoritesCountByDomain(domain: String): Int

    fun getFavorite(url: String): Favorite?

    fun hasBookmarks(): Boolean
    fun hasFavorites(): Boolean

    fun insertBookmark(
        url: String,
        title: String,
    ): Bookmark

    fun insertFavorite(
        url: String,
        title: String,
    ): Favorite

    fun insert(savedSite: SavedSite): SavedSite
    fun delete(savedSite: SavedSite)
    fun update(savedSite: SavedSite)
    fun updateWithPosition(favorites: List<Favorite>)

    fun insert(folder: BookmarkFolder): BookmarkFolder
    fun update(folder: BookmarkFolder)

    fun delete(folder: BookmarkFolder)
    fun getFolder(folderId: String): BookmarkFolder?
    fun deleteAll()
    fun bookmarksCount(): Long
    fun favoritesCount(): Long
}

class RealSavedSitesRepository(
    private val syncEntitiesDao: SyncEntitiesDao,
    private val syncRelationsDao: SyncRelationsDao,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : SavedSitesRepository {

    override suspend fun getSavedSites(folderId: String): Flow<SavedSites> {
        return if (folderId == Relation.BOOMARKS_ROOT) {
            getFavorites().combine(getFolderContent(folderId)) { favorites, folderContent ->
                SavedSites(favorites.distinct(), folderContent.first, folderContent.second)
            }
        } else {
            getFolderContent(folderId).map {
                SavedSites(emptyList(), it.first, it.second)
            }
        }
    }

    override suspend fun getFolderContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
        return syncEntitiesDao.entitiesInFolder(folderId).map { entities ->
            val bookmarks = mutableListOf<Bookmark>()
            val folders = mutableListOf<BookmarkFolder>()
            entities.map { entity ->
                if (entity.type == FOLDER) {
                    val numFolders = syncRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
                    val numBookmarks = syncRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
                    folders.add(BookmarkFolder(entity.entityId, entity.title, folderId, numBookmarks, numFolders))
                } else {
                    bookmarks.add(Bookmark(entity.entityId, entity.title, entity.url.orEmpty(), folderId))
                }
            }
            Pair(bookmarks.distinct(), folders.distinct())
        }
            .flowOn(dispatcherProvider.io())
    }

    override suspend fun getFolderTree(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?
    ): List<BookmarkFolderItem> {
        val rootFolder = getFolder(Relation.BOOMARKS_ROOT)
        return if (rootFolder != null) {
            val rootFolderItem = BookmarkFolderItem(0, rootFolder, rootFolder.id == selectedFolderId)
            val folders = mutableListOf(rootFolderItem)
            val folderDepth = traverseFolderWithDepth(1, folders, Relation.BOOMARKS_ROOT, selectedFolderId)
            folderDepth
        } else {
            emptyList()
        }
    }

    private fun traverseFolderWithDepth(
        depth: Int = 0,
        folders: MutableList<BookmarkFolderItem>,
        folderId: String,
        selectedFolderId: String
    ): List<BookmarkFolderItem> {
        getFolders(folderId).map {
            folders.add(BookmarkFolderItem(depth, it, it.id == selectedFolderId))
            traverseFolderWithDepth(depth + 1, folders, it.id, selectedFolderId)
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
        folderId: String
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
        return syncEntitiesDao.entitiesInFolder(folderId, FOLDER).map { entity ->
            entity.mapToBookmarkFolder(folderId)
        }
    }

    private fun folderContent(folderId: String): Pair<List<Bookmark>, List<BookmarkFolder>> {
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()
        syncEntitiesDao.entitiesInFolderSync(folderId).forEach { entity ->
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
        val favorites = mutableListOf<Favorite>()
        return syncEntitiesDao.entitiesInFolder(Relation.FAVORITES_ROOT)
            .map { relations ->
                relations.forEachIndexed { index, relation ->
                    favorites.add(relation.mapToFavorite(index))
                }
                favorites
            }
            .flowOn(dispatcherProvider.io())
    }

    override fun getFavoritesObservable(): Single<List<Favorite>> {
        val favorites = mutableListOf<Favorite>()
        return syncEntitiesDao.entitiesInFolderObservable(Relation.FAVORITES_ROOT).map { relations ->
            relations.forEachIndexed { index, entity ->
                favorites.add(entity.mapToFavorite(index))
            }
            favorites
        }
    }

    override fun getFavoritesSync(): List<Favorite> {
        return syncEntitiesDao.entitiesInFolderSync(Relation.FAVORITES_ROOT).mapToFavorites()
    }

    override fun getFavoritesCountByDomain(domain: String): Int {
        return syncRelationsDao.countRelationsByUrl(domain)
    }

    override fun getFavorite(url: String): Favorite? {
        val favorites = syncEntitiesDao.entitiesInFolderSync(Relation.FAVORITES_ROOT).mapIndexed { index, entity ->
            entity.mapToFavorite(index)
        }
        return favorites.firstOrNull { it.url == url }
    }

    override fun getBookmarks(): Flow<List<Bookmark>> {
        return syncEntitiesDao.entitiesByType(BOOKMARK).map { entities -> entities.mapToBookmarks() }
    }

    override fun getBookmarksObservable(): Single<List<Bookmark>> {
        return syncEntitiesDao.entitiesByTypeObservable(BOOKMARK).map { entities -> entities.mapToBookmarks() }
    }

    override fun getBookmark(url: String): Bookmark? {
        val bookmark = syncEntitiesDao.entityByUrl(url)
        return if (bookmark != null) {
            syncRelationsDao.relationByEntityId(bookmark.entityId)?.let {
                bookmark.mapToBookmark(it.relationId)
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
        syncEntitiesDao.insert(entity)
        syncRelationsDao.insert(Relation(relationId = Relation.BOOMARKS_ROOT, entityId = entity.entityId))
        return entity.mapToBookmark(Relation.BOOMARKS_ROOT)
    }

    override fun insertFavorite(
        url: String,
        title: String,
    ): Favorite {
        val titleOrFallback = title.takeIf { it.isNotEmpty() } ?: url
        val existentBookmark = syncEntitiesDao.entityByUrl(url)
        if (existentBookmark == null) {
            val entity = Entity(title = titleOrFallback, url = url, type = BOOKMARK)
            syncEntitiesDao.insert(entity)
            syncRelationsDao.insert(Relation(relationId = Relation.BOOMARKS_ROOT, entityId = entity.entityId))
            syncRelationsDao.insert(Relation(relationId = Relation.FAVORITES_ROOT, entityId = entity.entityId))
        } else {
            syncRelationsDao.insert(Relation(relationId = Relation.FAVORITES_ROOT, entityId = existentBookmark.entityId))
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
                syncEntitiesDao.insert(entity)
                syncRelationsDao.insert(Relation(relationId = savedSite.parentId, entityId = entity.entityId))
                entity.mapToBookmark(savedSite.parentId)
            }
        }
    }

    override fun delete(savedSite: SavedSite) {
        syncEntitiesDao.entityByUrl(savedSite.url)?.let {
            syncEntitiesDao.delete(it)
            syncRelationsDao.deleteRelationByEntity(it.entityId)
        }
    }

    override fun update(savedSite: SavedSite) {
        when (savedSite) {
            is Bookmark -> updateBookmark(savedSite)
            is Favorite -> updateFavorite(savedSite)
        }
    }

    private fun updateBookmark(bookmark: Bookmark) {
        syncEntitiesDao.entityById(bookmark.id)?.let { entity ->
            syncRelationsDao.relationByEntityId(entity.entityId)?.let { relation ->
                if (relation.relationId != bookmark.parentId) {
                    // bookmark moved to another folder
                    syncRelationsDao.deleteRelationByEntity(bookmark.id)
                    syncRelationsDao.insert(Relation(relationId = bookmark.parentId, entityId = relation.entityId))
                }
                syncEntitiesDao.update(Entity(relation.entityId, bookmark.title, bookmark.url, BOOKMARK))
            }
        }
    }

    private fun updateFavorite(favorite: Favorite) {
        syncEntitiesDao.entityById(favorite.id)?.let { entity ->
            syncEntitiesDao.update(Entity(entity.entityId, favorite.title, favorite.url, BOOKMARK))

            val favorites = syncEntitiesDao.entitiesInFolderSync(Relation.FAVORITES_ROOT).mapIndexed { index, entity ->
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
        syncEntitiesDao.insert(entity)
        syncRelationsDao.insert(Relation(relationId = folder.parentId, entityId = folder.id))
        return folder
    }

    override fun update(folder: BookmarkFolder) {
        val oldFolder = getFolder(folder.id) ?: return

        syncEntitiesDao.update(Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER))

        // has folder parent changed?
        if (oldFolder.parentId != folder.id) {
            syncRelationsDao.deleteRelationByEntity(folder.id)
            syncRelationsDao.insert(Relation(relationId = folder.parentId, entityId = folder.id))
        }
    }

    override fun delete(folder: BookmarkFolder) {
        val relations = syncEntitiesDao.entitiesInFolderSync(folder.id)
        val entities = mutableListOf<Entity>()
        relations.forEach {
            entities.add(it)
        }

        syncRelationsDao.deleteRelationByEntity(folder.id)
        syncRelationsDao.delete(folder.id)
        syncEntitiesDao.deleteList(entities.toList())
        syncEntitiesDao.delete(folder.id)
    }

    override fun getFolder(folderId: String): BookmarkFolder? {
        val entity = syncEntitiesDao.entityById(folderId)
        val relation = syncRelationsDao.relationByEntityId(folderId)

        return if (entity != null) {
            val numFolders = syncRelationsDao.countEntitiesInFolder(entity.entityId, FOLDER)
            val numBookmarks = syncRelationsDao.countEntitiesInFolder(entity.entityId, BOOKMARK)
            BookmarkFolder(folderId, entity.title, relation?.relationId ?: "", numFolders = numFolders, numBookmarks = numBookmarks)
        } else {
            null
        }
    }

    override fun deleteAll() {
        syncRelationsDao.deleteAll()
        syncEntitiesDao.deleteAll()
    }

    override fun bookmarksCount(): Long {
        return syncEntitiesDao.entitiesByTypeSync(BOOKMARK).size.toLong()
    }

    override fun favoritesCount(): Long {
        return syncEntitiesDao.entitiesInFolderSync(Relation.FAVORITES_ROOT).size.toLong()
    }

    override fun updateWithPosition(favorites: List<Favorite>) {
        syncRelationsDao.delete(Relation.FAVORITES_ROOT)
        val relations = favorites.map { Relation(relationId = Relation.FAVORITES_ROOT, entityId = it.id) }
        syncRelationsDao.insertList(relations)
    }

    private fun Entity.mapToBookmark(relationId: String): Bookmark = Bookmark(this.entityId, this.title, this.url.orEmpty(), relationId)
    private fun Entity.mapToBookmarkFolder(relationId: String): BookmarkFolder =
        BookmarkFolder(this.entityId, this.title, relationId)

    private fun Entity.mapToFavorite(index: Int = 0): Favorite = Favorite(this.entityId, this.title, this.url.orEmpty(), index)
    private fun List<Entity>.mapToBookmarks(folderId: String = Relation.BOOMARKS_ROOT): List<Bookmark> = this.map { it.mapToBookmark(folderId) }
    private fun List<Entity>.mapToFavorites(): List<Favorite> = this.mapIndexed { index, relation -> relation.mapToFavorite(index) }
}

data class SavedSites(
    val favorites: List<Favorite>,
    val bookmarks: List<Bookmark>,
    val folders: List<BookmarkFolder>
)

sealed class SavedSite(
    open val id: String,
    open val title: String,
    open val url: String,
) : Serializable {
    data class Favorite(
        override val id: String,
        override val title: String,
        override val url: String,
        val position: Int,
    ) : SavedSite(id, title, url)

    data class Bookmark(
        override val id: String,
        override val title: String,
        override val url: String,
        val parentId: String = Relation.BOOMARKS_ROOT,
    ) : SavedSite(id, title, url)
}

private fun SavedSite.titleOrFallback(): String = this.title.takeIf { it.isNotEmpty() } ?: this.url
data class FolderBranch(
    val bookmarks: List<Bookmark>,
    val folders: List<BookmarkFolder>,
)
