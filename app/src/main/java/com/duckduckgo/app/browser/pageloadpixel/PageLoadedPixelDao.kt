/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.pageloadpixel

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
abstract class PageLoadedPixelDao {

    @Insert
    abstract fun add(pageLoadedPixelEntity: PageLoadedPixelEntity)

    @Query("SELECT * FROM page_loaded_pixel_entity")
    abstract fun all(): List<PageLoadedPixelEntity>

    @Delete
    abstract fun delete(pageLoadedPixelEntity: PageLoadedPixelEntity)

    @Query("DELETE FROM page_loaded_pixel_entity")
    abstract fun deleteAll()
}
