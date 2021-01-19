/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.statistics.store

import androidx.room.*
import com.duckduckgo.app.statistics.model.PixelEntity
import io.reactivex.Observable

@Dao
interface PendingPixelDao {

    @Query("select * from pixel_store")
    fun pixels(): Observable<List<PixelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pixel: PixelEntity): Long

    @Delete
    fun delete(pixel: PixelEntity)
}
