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

import androidx.lifecycle.LiveData
import androidx.room.*
import io.reactivex.Single

@Dao
interface BookmarksDao {

    @Insert
    fun insert(bookmark: BookmarkEntity): Long

    @Query("select * from bookmarks")
    fun bookmarks(): LiveData<List<BookmarkEntity>>

    @Query("select * from bookmarks")
    fun bookmarksSync(): List<BookmarkEntity>

    @Delete
    fun delete(bookmark: BookmarkEntity)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(bookmarkEntity: BookmarkEntity)

    @Query("select * from bookmarks WHERE title LIKE :query OR url LIKE :query ")
    fun bookmarksByQuery(query: String): Single<List<BookmarkEntity>>

    @Query("select CAST(COUNT(*) AS BIT) from bookmarks")
    suspend fun hasBookmarks(): Boolean
}