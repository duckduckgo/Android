/*
 * Copyright (c) 2018 DuckDuckGo
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

import androidx.room.*
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarksDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bookmark: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(bookmarks: List<BookmarkEntity>)

    @Query("select * from bookmarks")
    fun getBookmarks(): Flow<List<BookmarkEntity>>

    @Query("select * from bookmarks where parentId = :parentId")
    fun getBookmarksByParentId(parentId: Long): Flow<List<BookmarkEntity>>

    @Query("select * from bookmarks where parentId = :parentId")
    fun getBookmarksByParentIdSync(parentId: Long): List<BookmarkEntity>

    @Query("select * from bookmarks where parentId in (:parentIds)")
    fun getBookmarksByParentIds(parentIds: List<Long>): List<BookmarkEntity>

    @Query("select count(*) from bookmarks WHERE url LIKE :url")
    fun bookmarksCountByUrl(url: String): Int

    @Delete
    fun delete(bookmark: BookmarkEntity)

    @Delete
    fun deleteList(bookmarkEntities: List<BookmarkEntity>)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(bookmarkEntity: BookmarkEntity)

    @Query("select * from bookmarks")
    fun bookmarksObservable(): Single<List<BookmarkEntity>>

    @Query("select CAST(COUNT(*) AS BIT) from bookmarks")
    suspend fun hasBookmarks(): Boolean

    @Query("select * from bookmarks where url = :url limit 1")
    fun getBookmarkByUrl(url: String): BookmarkEntity?
}
