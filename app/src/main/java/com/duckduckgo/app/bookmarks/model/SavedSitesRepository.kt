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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface SavedSitesRepository {

    // getFavorites and getFolderContent will be the same once we can merge Favorites / Bookmarks

    suspend fun getFolderContent(parentId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>>

    suspend fun getFlatFolderStructure(
        selectedFolderId: Long,
        currentFolder: BookmarkFolder?,
        rootFolderName: String,
    ): List<BookmarkFolderItem>

    suspend fun insertFolderBranch(branchToInsert: BookmarkFolderBranch)

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

    fun insert(folder: BookmarkFolder)
    fun update(folder: BookmarkFolder)

    fun delete(folder: BookmarkFolder)

    fun deleteFolderBranch(folder: BookmarkFolder): BookmarkFolderBranch

    fun getFolder(folderId: String): BookmarkFolder
    fun deleteAll()
    fun bookmarksCount(): Long
    fun favoritesCount(): Long
}

class RealSavedSitesRepository(
    private val syncEntitiesDao: SyncEntitiesDao,
    private val syncRelationsDao: SyncRelationsDao,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : SavedSitesRepository {

    override suspend fun getFolderContent(parentId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()

        return syncRelationsDao.relationById(parentId).map { entities ->
            entities.forEach { entity ->
                if (entity.type == FOLDER) {
                    folders.add(entity.mapToBookmarkFolder(parentId))
                } else {
                    bookmarks.add(entity.mapToBookmark(parentId))
                }
            }
            Pair(bookmarks, folders)
        }
            .flowOn(dispatcherProvider.io())
    }

    override suspend fun getFlatFolderStructure(
        selectedFolderId: Long,
        currentFolder: BookmarkFolder?,
        rootFolderName: String,
    ): List<BookmarkFolderItem> {
        return emptyList()
    }

    override suspend fun insertFolderBranch(branchToInsert: BookmarkFolderBranch) {
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
            val relation = syncRelationsDao.relationParentById(bookmark.entityId)
            bookmark.mapToBookmark(relation.relationId)
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
        return when (savedSite) {
            is Favorite -> insertFavorite(title = savedSite.title, url = savedSite.url)
            else -> insertBookmark(title = savedSite.title, url = savedSite.url)
        }
    }

    override fun delete(savedSite: SavedSite) {
        val entity = syncEntitiesDao.entityByUrl(savedSite.url)
        if (entity != null) {
            syncEntitiesDao.delete(entity)
            syncRelationsDao.deleteEntity(entity.entityId)
        }
    }

    override fun update(savedSite: SavedSite) {
        when (savedSite){
            is Bookmark -> updateBookmark(savedSite)
            is Favorite -> updateFavorite(savedSite)
        }
    }

    private fun updateBookmark(bookmark: Bookmark){
        val entity = syncEntitiesDao.entityById(bookmark.id)

        if (entity != null) {
            val relation = syncRelationsDao.relationParentById(entity.entityId)
            if (relation.relationId != bookmark.parentId){
                // bookmark moved to another folder
                syncRelationsDao.delete(relation.relationId)
                syncRelationsDao.insert(Relation(relationId = bookmark.parentId, entityId = entity.entityId))
            }
            syncEntitiesDao.update(Entity(entity.entityId, bookmark.title, bookmark.url, BOOKMARK))
        }
    }

    private fun updateFavorite(favorite: Favorite){
        val entity = syncEntitiesDao.entityById(favorite.id)

        if (entity != null) {
            syncEntitiesDao.update(Entity(entity.entityId, favorite.title, favorite.url, BOOKMARK))
        }
    }

    override fun insert(folder: BookmarkFolder) {
        val entity = Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER)
        syncEntitiesDao.insert(entity)
        syncRelationsDao.insert(Relation(relationId = folder.parentId, entityId = entity.entityId))
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

        syncRelationsDao.delete(folder.id)
        syncEntitiesDao.deleteList(entities.toList())
    }

    override fun deleteFolderBranch(folder: BookmarkFolder): BookmarkFolderBranch {
        // deletes folder and everything underneath, folders and bookmarks
        val entities = mutableListOf<Entity>()
        val relations = mutableListOf<Relation>()
        val relation = syncRelationsDao.relationById(folder.id).map {
        }
        return BookmarkFolderBranch(emptyList(), emptyList())
    }

    override fun getFolder(folderId: String): BookmarkFolder {
        val entity = syncEntitiesDao.entityById(folderId)
        return BookmarkFolder(folderId, entity.title, folderId)
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
        val parentId: String,
    ) : SavedSite(id, title, url)
}

private fun SavedSite.titleOrFallback(): String = this.title.takeIf { it.isNotEmpty() } ?: this.url
