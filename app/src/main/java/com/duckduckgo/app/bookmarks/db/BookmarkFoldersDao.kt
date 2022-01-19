/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkFoldersDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bookmarkFolder: BookmarkFolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(bookmarkFolders: List<BookmarkFolderEntity>)

    @Query(
        "select *, (select count(*) from bookmarks where bookmarks.parentId = bookmark_folders.id) as numBookmarks, (select count(*) " +
            "from bookmark_folders as inner_bookmark_folders " +
            "where inner_bookmark_folders.parentId = bookmark_folders.id) as numFolders from bookmark_folders"
    )
    fun getBookmarkFolders(): Flow<List<BookmarkFolder>>

    @Query("select * from bookmark_folders")
    fun getBookmarkFoldersSync(): List<BookmarkFolderEntity>

    @Query(
        "select *, (select count(*) from bookmarks where bookmarks.parentId = bookmark_folders.id) as numBookmarks, " +
            "(select count(*) from bookmark_folders as inner_bookmark_folders " +
            "where inner_bookmark_folders.parentId = bookmark_folders.id) as numFolders " +
            "from bookmark_folders where bookmark_folders.parentId = :parentId"
    )
    fun getBookmarkFoldersByParentId(parentId: Long): Flow<List<BookmarkFolder>>

    @Query(
        "select *, (select count(*) from bookmarks " +
            "where bookmarks.parentId = bookmark_folders.id) as numBookmarks, (select count(*) " +
            "from bookmark_folders as inner_bookmark_folders " +
            "where inner_bookmark_folders.parentId = bookmark_folders.id) as numFolders " +
            "from bookmark_folders where bookmark_folders.parentId = :parentId"
    )
    fun getBookmarkFoldersByParentIdSync(parentId: Long): List<BookmarkFolder>

    @Query(
        "select *, (select count(*) from bookmarks " +
            "where bookmarks.parentId = bookmark_folders.id) as numBookmarks, (select count(*) " +
            "from bookmark_folders as inner_bookmark_folders " +
            "where inner_bookmark_folders.parentId = bookmark_folders.id) as numFolders " +
            "from bookmark_folders where bookmark_folders.id = :parentId"
    )
    fun getBookmarkFolderByParentId(parentId: Long): BookmarkFolder

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(bookmarkFolder: BookmarkFolderEntity)

    @Delete
    fun delete(bookmarkFolderEntities: List<BookmarkFolderEntity>)
}
