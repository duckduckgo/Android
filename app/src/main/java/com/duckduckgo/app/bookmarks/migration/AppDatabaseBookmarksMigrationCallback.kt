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

package com.duckduckgo.app.bookmarks.migration

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.bookmarks.model.TreeNode
import com.duckduckgo.app.bookmarks.service.FolderTreeItem
import com.duckduckgo.app.bookmarks.service.RealSavedSitesParser
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor

@OptIn(ExperimentalCoroutinesApi::class)
class AppDatabaseBookmarksMigrationCallback(
    private val appDatabase: Lazy<AppDatabase>,
    private val dispatcherProvider: DispatcherProvider
): RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        ioThread {
            migrateBookmarks()
        }
    }

    fun migrateBookmarks(){
        with(appDatabase.get()) {
            if (favoritesDao().userHasFavorites()){
                val favourites = favoritesDao().favoritesSync()
                val favouriteChildren = mutableListOf<String>()
                favourites.forEach {
                    syncEntitiesDao().insert(Entity(it.id.toString(), it.title, it.url, BOOKMARK))
                    favouriteChildren.add(it.id.toString())
                }
                syncRelationsDao().insert(Relation("favorites_root", favouriteChildren))
            }
        }
    }

    fun mapEntities(db: SupportSQLiteDatabase): List<Entity> {
        val entities = mutableListOf<Entity>()
        val folders = emptyList<BookmarkFolder>()
        val favourites = emptyList<Favorite>()

        val bookmarks = getBookmarks(db)

        bookmarks.forEach {
            entities.add(Entity(it.id.toString(), it.title.orEmpty(), it.url, BOOKMARK))
        }
        folders.forEach {
            entities.add(Entity(it.id.toString(), it.name, null, FOLDER))
        }
        favourites.forEach {
            entities.add(Entity(it.id.toString(), it.title, it.url, BOOKMARK))
        }
        return entities
    }

    private fun getBookmarks(db: SupportSQLiteDatabase): List<BookmarkEntity> {
        val _sql = "select * from bookmarks"
        val cursor: Cursor = db.query(SimpleSQLiteQuery(_sql))
        val bookmarks = mutableListOf<BookmarkEntity>()

        kotlin.runCatching {
            val _cursorIndexOfId = cursor.getColumnIndexOrThrow("id")
            val _cursorIndexOfTitle = cursor.getColumnIndexOrThrow("title")
            val _cursorIndexOfUrl = cursor.getColumnIndexOrThrow("url")
            val _cursorIndexOfParentId = cursor.getColumnIndexOrThrow("parentId")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(_cursorIndexOfId)
                val title = if (cursor.isNull(_cursorIndexOfTitle)) {
                    ""
                } else {
                    cursor.getString(_cursorIndexOfTitle)
                }
                val url = cursor.getString(_cursorIndexOfUrl)
                val parentId = cursor.getLong(_cursorIndexOfParentId)
                bookmarks.add(BookmarkEntity(id, title, url, parentId))
            }
            cursor.close()
        }
        return bookmarks
    }

    fun mapRelations(): List<Relation> {
        val treeNode = getTreeFolderStructure()
        return emptyList()
    }

    private fun getTreeFolderStructure(): TreeNode<FolderTreeItem> {
        return TreeNode(FolderTreeItem(0, RealSavedSitesParser.BOOKMARKS_FOLDER, -1, null, 0))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ioThread(f: () -> Unit) {
        // At most 1 thread will be doing IO
        dispatcherProvider.io().limitedParallelism(1).asExecutor().execute(f)
    }
}
