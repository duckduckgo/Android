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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

interface SavedSitesRepository {

    // getFavorites and getFolderContent will be the same once we can merge Favorites / Bookmarks

    suspend fun getFolderContent(parentId: Long?): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>>

    fun getBookmarks(): Flow<List<Bookmark>>

    fun getBookmark(url: String): Bookmark?

    fun getFavorites(): Flow<List<Favorite>>

    fun getFavorite(url: String): Favorite?

    fun hasBookmarks(): Boolean

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

    fun getFolder(folderId: Long): BookmarkFolder
}

class RealSavedSitesRepository(
    private val syncEntitiesDao: SyncEntitiesDao,
    private val syncRelationsDao: SyncRelationsDao,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) : SavedSitesRepository {

    // this signature will change when the migration can happen, using our own Entities data model
    override suspend fun getFolderContent(parentId: Long?): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
        val parentId = parentId ?: Relation.BOOMARKS_ROOT_ID
        val folderId = Relation.migrateId(parentId)

        val bookmarks = mutableListOf<Bookmark>()
        val folders = mutableListOf<BookmarkFolder>()

        return syncRelationsDao.relationById(folderId).map { entities ->
            entities.forEachIndexed { index, entity ->
                if (entity.type == FOLDER) {
                    folders.add(BookmarkFolder(index.toLong(), entity.title, parentId))
                } else {
                    bookmarks.add(Bookmark(index.toLong(), entity.title, entity.url!!, parentId))
                }
            }
            Pair(bookmarks, folders)
        }
            .flowOn(dispatcherProvider.io())
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

    override fun getFavorite(url: String): Favorite? {
        return syncEntitiesDao.favorite(url = url)?.mapToFavorite()
    }

    override fun getBookmarks(): Flow<List<Bookmark>> {
        return syncEntitiesDao.entitiesByType(BOOKMARK).map { bookmarks -> bookmarks.mapToBookmarks() }
    }

    override fun getBookmark(url: String): Bookmark? {
        return syncEntitiesDao.entityByUrl(url)?.mapToBookmark()
    }

    override fun hasBookmarks(): Boolean {
        return syncEntitiesDao.hasEntitiesByType(BOOKMARK)
    }

    override fun insertBookmark(
        url: String,
        title: String,
    ): Bookmark {
        val entity = Entity(title = title, url = url, type = BOOKMARK)
        syncEntitiesDao.insert(entity)
        syncRelationsDao.insert(Relation(Relation.BOOMARKS_ROOT, entity))
        return entity.mapToBookmark()
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
        val id = when (savedSite) {
            is Bookmark -> {
                Entity.generateBookmarkId(savedSite.id)
            }

            else -> {
                Entity.generateFavoriteId(savedSite.id)
            }
        }
        syncEntitiesDao.insert(Entity(id, savedSite.title, savedSite.url, BOOKMARK))
    }

    override fun delete(savedSite: SavedSite) {
        // we delete by url until Bookmark takes String as id instead of Long
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
        syncEntitiesDao.insert(Entity(Entity.generateFolderId(folder.id), folder.name, "", FOLDER))
    }

    override fun update(folder: BookmarkFolder) {
        syncEntitiesDao.update(Entity(Entity.generateFolderId(folder.id), folder.name, "", FOLDER))
    }

    override fun delete(folder: BookmarkFolder) {
        val relations = syncRelationsDao.relationByIdSync(Entity.generateFolderId(folder.id))
        val entities = mutableListOf<Entity>()
        relations.forEach {
            entities.add(it.entity)
        }

        syncRelationsDao.delete(Entity.generateFolderId(folder.id))
        syncEntitiesDao.deleteList(entities.toList())
    }

    override fun getFolder(folderId: Long): BookmarkFolder {
        val id = if (folderId == Relation.BOOMARKS_ROOT_ID) {
            Relation.BOOMARKS_ROOT
        } else {
            Entity.generateFolderId(folderId)
        }
        val entity = syncEntitiesDao.entityById(id)
        return BookmarkFolder(folderId, entity.title, folderId)
    }

    override fun updateWithPosition(favorites: List<Favorite>) {
        // reorder the list of the relation
    }

    // TODO: Saved Site will be using Strings as id instead of number
    private fun Entity.mapToBookmark(): Bookmark = Bookmark(0, this.title, this.url.orEmpty(), 0)
    private fun Entity.mapToFavorite(): Favorite = Favorite(0, this.title, this.url.orEmpty(), 0)
    private fun Entity.mapIndexedToBookmark(index: Int): Bookmark = Bookmark(index.toLong(), this.title, this.url.orEmpty(), index.toLong())
    private fun Entity.mapIndexedToFavorite(index: Int): Favorite = Favorite(index.toLong(), this.title, this.url.orEmpty(), index)
    private fun List<Entity>.mapToBookmarks(): List<Bookmark> = this.map { it.mapToBookmark() }
}
