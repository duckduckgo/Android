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

import android.content.pm.PackageItemInfo
import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityContent
import com.duckduckgo.sync.store.EntityType
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import io.reactivex.Single
import java.io.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.*

interface SavedSitesRepository {

    suspend fun getSavedSites(folderId: String): Flow<SavedSites>

    suspend fun getFolderEntityContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>>

    suspend fun getFolderContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>>

    suspend fun getFlatFolderStructure(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
        rootFolderId: String,
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
            getFavorites().combine(getFolderEntityContent(folderId)) { favorites, folderContent ->
                SavedSites(favorites.distinct(), folderContent.first, folderContent.second)
            }
        } else {
            getFolderEntityContent(folderId).map {
                SavedSites(emptyList(), it.first, it.second)
            }
        }
    }

    override suspend fun getFolderEntityContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()
        return syncRelationsDao.relationById(folderId).map { entities ->
            entities.forEach { entity ->
                if (entity.type == FOLDER) {
                    val numFolders = syncRelationsDao.getEntitiesInFolder(entity.entityId, FOLDER)
                    val numBookmarks = syncRelationsDao.getEntitiesInFolder(entity.entityId, BOOKMARK)
                    folders.add(BookmarkFolder(entity.entityId, entity.title, folderId, numBookmarks, numFolders))
                } else {
                    bookmarks.add(Bookmark(entity.entityId, entity.title, entity.url.orEmpty(), folderId))
                }
            }
            Pair(bookmarks.distinct(), folders.distinct())
        }
            .flowOn(dispatcherProvider.io())
    }

    override suspend fun getFolderContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()

        return syncRelationsDao.relationById(folderId).map { entities ->
            entities.forEach { entity ->
                if (entity.type == FOLDER) {
                    folders.add(entity.mapToBookmarkFolder(folderId))
                } else {
                    bookmarks.add(entity.mapToBookmark(folderId))
                }
            }
            Pair(bookmarks.distinct(), folders.distinct())
        }
            .flowOn(dispatcherProvider.io())
    }

    override suspend fun getFlatFolderStructure(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
        rootFolderId: String,
    ): List<BookmarkFolderItem> {
        val rootFolder = getFolder(rootFolderId)
        return if (rootFolder != null) {
            val rootFolderItem = BookmarkFolderItem(0, rootFolder, rootFolder.id == selectedFolderId)
            val folders = mutableListOf(rootFolderItem)
            val folderDepth = traverseFolderWithDepth(1, folders, rootFolderId, selectedFolderId)
            folderDepth
        } else {
            emptyList()
        }
    }

    //method to return all folders with the current one selected

    //method to return all folders, wiht the current one selected but removing the branch under the current folder

    private fun traverseFolderWithDepth(
        depth: Int = 0,
        folders: MutableList<BookmarkFolderItem>,
        folderId: String,
        selectedFolderId: String
    ): List<BookmarkFolderItem> {
        val folderContent = folderContent(folderId)

        folders.addAll(
            folderContent.second.map {
                BookmarkFolderItem(depth, it, it.id == selectedFolderId)
            },
        )

        folderContent.second.forEach {
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

    private fun folderContent(folderId: String): Pair<List<Bookmark>, List<BookmarkFolder>> {
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()
        syncRelationsDao.relationsByIdSync(folderId).forEach { relation ->
            val entity = syncEntitiesDao.entityById(relation.entityId)
            entity?.let {
                if (entity.type == FOLDER) {
                    folders.add(entity.mapToBookmarkFolder(folderId))
                } else {
                    bookmarks.add(entity.mapToBookmark(folderId))
                }
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
        return syncRelationsDao.relationById(Relation.FAVORITES_ROOT)
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
        return syncRelationsDao.relationByIdObservable(Relation.FAVORITES_ROOT).map { relations ->
            relations.forEachIndexed { index, entity ->
                favorites.add(entity.mapToFavorite(index))
            }
            favorites
        }
    }

    override fun getFavoritesSync(): List<Favorite> {
        return syncRelationsDao.relationByIdSync(Relation.FAVORITES_ROOT).mapToFavorites()
    }

    override fun getFavoritesCountByDomain(domain: String): Int {
        return syncRelationsDao.relationsCountByUrl(domain)
    }

    override fun getFavorite(url: String): Favorite? {
        val favorites = syncRelationsDao.relationByIdSync(Relation.FAVORITES_ROOT).mapIndexed { index, entity ->
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
            syncRelationsDao.relationParentById(bookmark.entityId)?.let {
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
            syncRelationsDao.deleteEntity(it.entityId)
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
            syncRelationsDao.relationParentById(entity.entityId)?.let { relation ->
                if (relation.relationId != bookmark.parentId) {
                    // bookmark moved to another folder
                    syncRelationsDao.delete(relation.relationId)
                    syncRelationsDao.insert(Relation(relationId = bookmark.parentId, entityId = relation.entityId))
                }
                syncEntitiesDao.update(Entity(relation.entityId, bookmark.title, bookmark.url, BOOKMARK))
            }
        }
    }

    private fun updateFavorite(favorite: Favorite) {
        val entity = syncEntitiesDao.entityById(favorite.id)

        if (entity != null) {
            syncEntitiesDao.update(Entity(entity.entityId, favorite.title, favorite.url, BOOKMARK))
        }
    }

    override fun insert(folder: BookmarkFolder): BookmarkFolder {
        val entity = Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER)
        syncEntitiesDao.insert(entity)
        syncRelationsDao.insert(Relation(relationId = folder.parentId, entityId = folder.id))
        return folder
    }

    override fun update(folder: BookmarkFolder) {
        syncEntitiesDao.update(Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER))
    }

    override fun delete(folder: BookmarkFolder) {
        val relations = syncRelationsDao.relationByIdSync(folder.id)
        val entities = mutableListOf<Entity>()
        relations.forEach {
            entities.add(it)
        }

        syncRelationsDao.deleteEntity(folder.id)
        syncRelationsDao.delete(folder.id)
        syncEntitiesDao.deleteList(entities.toList())
        syncEntitiesDao.delete(folder.id)
    }

    override fun getFolder(folderId: String): BookmarkFolder? {
        val entity = syncEntitiesDao.entityById(folderId)
        val relation = syncRelationsDao.relationParentById(folderId)
        return if (entity != null && relation != null) {
            BookmarkFolder(folderId, entity.title, relation.relationId)
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
        return syncRelationsDao.relationByIdSync(Relation.FAVORITES_ROOT).size.toLong()
    }

    override fun updateWithPosition(favorites: List<Favorite>) {
        // reorder the list of the relation
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
