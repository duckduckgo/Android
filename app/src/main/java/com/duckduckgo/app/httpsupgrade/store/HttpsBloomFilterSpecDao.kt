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

package com.duckduckgo.app.httpsupgrade.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec
import com.duckduckgo.di.scopes.AppObjectGraph
import dagger.SingleInstanceIn

@Dao
@SingleInstanceIn(AppObjectGraph::class)
interface HttpsBloomFilterSpecDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(specification: HttpsBloomFilterSpec)

    @Query("select * from https_bloom_filter_spec limit 1")
    fun get(): HttpsBloomFilterSpec?
}
