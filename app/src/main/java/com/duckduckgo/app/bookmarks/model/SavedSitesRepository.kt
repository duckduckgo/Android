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

    fun insert(savedSite: SavedSite)
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
            entities.forEachIndexed { index, entity ->
                if (entity.type == FOLDER) {
                    folders.add(BookmarkFolder(entity.entityId, entity.title, parentId))
                } else {
                    bookmarks.add(Bookmark(entity.entityId, entity.title, entity.url!!, parentId))
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
            .map { entities ->
                entities.forEachIndexed { index, entity ->
                    favorites.add(entity.mapIndexedToFavorite(index))
                }
                favorites
            }
            .flowOn(dispatcherProvider.io())
    }

    override fun getFavoritesObservable(): Single<List<Favorite>> {
        val favorites = mutableListOf<Favorite>()
        return syncRelationsDao.relationByIdObservable(Relation.FAVORITES_ROOT).map { relations ->
            relations.forEachIndexed { index, entity ->
                favorites.add(entity.mapIndexedToFavorite(index))
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
        return syncEntitiesDao.favorite(url = url)?.mapToFavorite()
    }

    override fun getBookmarks(): Flow<List<Bookmark>> {
        return syncRelationsDao.entitiesByType(BOOKMARK).map { entities -> entities.mapToBookmarks() }
    }

    override fun getBookmarksObservable(): Single<List<Bookmark>> {
        return syncRelationsDao.entitiesByTypeObservable(BOOKMARK).map { entities -> entities.mapToBookmarks() }
    }

    override fun getBookmark(url: String): Bookmark? {
        val bookmark = syncEntitiesDao.entityByUrl(url)
        if (bookmark != null) {
            val relation = syncRelationsDao.relationParentById(bookmark.entityId)
            return bookmark.mapToBookmark(relation.relationId)
        } else {
            return null
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
        val entity = Entity(title = title, url = url, type = BOOKMARK)
        syncEntitiesDao.insert(entity)
        syncRelationsDao.insert(Relation(Relation.BOOMARKS_ROOT, entity))
        return entity.mapToBookmark(Relation.BOOMARKS_ROOT)
    }

    override fun insertFavorite(
        url: String,
        title: String,
    ): Favorite {
        val existentBookmark = syncEntitiesDao.entityByUrl(url)
        if (existentBookmark == null) {
            val entity = Entity(title = title, url = url, type = BOOKMARK)
            syncEntitiesDao.insert(entity)
            syncRelationsDao.insert(Relation(Relation.BOOMARKS_ROOT, entity))
            syncRelationsDao.insert(Relation(Relation.FAVORITES_ROOT, entity))
        } else {
            syncRelationsDao.insert(Relation(Relation.FAVORITES_ROOT, existentBookmark))
        }
        return getFavorite(url)!!
    }

    override fun insert(savedSite: SavedSite) {
        syncEntitiesDao.insert(Entity(savedSite.id, savedSite.title, savedSite.url, BOOKMARK))
    }

    override fun delete(savedSite: SavedSite) {
        val entity = syncEntitiesDao.entityByUrl(savedSite.url)
        if (entity != null) {
            syncEntitiesDao.delete(entity)
            syncRelationsDao.deleteEntity(entity.entityId)
        }
    }

    override fun update(savedSite: SavedSite) {
        val entity = syncEntitiesDao.entityByUrl(savedSite.url)
        if (entity != null) {
            syncEntitiesDao.update(Entity(entity.entityId, savedSite.title, savedSite.url, BOOKMARK))
        }
    }

    override fun insert(folder: BookmarkFolder) {
        val entity = Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER)
        syncEntitiesDao.insert(entity)
        syncRelationsDao.insert(Relation(folder.parentId, entity))
    }

    override fun update(folder: BookmarkFolder) {
        syncEntitiesDao.update(Entity(entityId = folder.id, title = folder.name, url = "", type = FOLDER))
    }

    override fun delete(folder: BookmarkFolder) {
        val relations = syncRelationsDao.relationByIdSync(folder.id)
        val entities = mutableListOf<Entity>()
        relations.forEach {
            entities.add(it.entity)
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
    private fun Relation.mapToBookmark(relationId: String): Bookmark =
        Bookmark(this.entity.entityId, this.entity.title, this.entity.url.orEmpty(), relationId)

    private fun Relation.mapToFavorite(position: Int): Favorite =
        Favorite(this.entity.entityId, this.entity.title, this.entity.url.orEmpty(), position)

    private fun Entity.mapToFavorite(): Favorite = Favorite(this.entityId, this.title, this.url.orEmpty(), 0)
    private fun Entity.mapIndexedToFavorite(index: Int): Favorite = Favorite(this.entityId, this.title, this.url.orEmpty(), index)
    private fun List<Relation>.mapToBookmarks(): List<Bookmark> = this.map { it.mapToBookmark(it.relationId) }
    private fun List<Relation>.mapToFavorites(): List<Favorite> = this.mapIndexed { index, relation -> relation.mapToFavorite(index) }
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
