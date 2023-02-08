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

package com.duckduckgo.app.bookmarks.mapper

import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.bookmarks.model.TreeNode
import com.duckduckgo.app.bookmarks.service.FolderTreeItem
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation

interface SyncMapper {
    fun mapEntities(bookmarks: List<SavedSite>, folders: List<BookmarkFolder>, favourites: List<Favorite>): List<Entity>

    fun mapRelations(bookmarks: TreeNode<FolderTreeItem>): List<Relation>

}

class AppSyncMapper(): SyncMapper {
    override fun mapEntities(
        bookmarks: List<SavedSite>,
        folders: List<BookmarkFolder>,
        favorite: List<Favorite>
    ): List<Entity> {
        val entities = mutableListOf<Entity>()
        bookmarks.forEach {
            entities.add(Entity(it.id.toString(), it.title, it.url, BOOKMARK))
        }
        folders.forEach {
            entities.add(Entity(it.id.toString(), it.name, null, FOLDER))
        }
        favorite.forEach {
            entities.add(Entity(it.id.toString(), it.title, it.url, BOOKMARK))
        }
        return entities
    }

    override fun mapRelations(bookmarks: TreeNode<FolderTreeItem>): List<Relation> {
        TODO("Not yet implemented")
    }

}
