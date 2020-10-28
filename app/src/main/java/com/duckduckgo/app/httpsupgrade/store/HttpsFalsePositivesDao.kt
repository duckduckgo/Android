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

import androidx.room.*
import com.duckduckgo.app.httpsupgrade.model.HttpsFalsePositiveDomain
import javax.inject.Singleton

@Dao
@Singleton
abstract class HttpsFalsePositivesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(domains: List<HttpsFalsePositiveDomain>)

    @Transaction
    open fun updateAll(domains: List<HttpsFalsePositiveDomain>) {
        deleteAll()
        insertAll(domains)
    }

    @Query("select count(1) > 0 from https_false_positive_domain where domain = :domain")
    abstract fun contains(domain: String): Boolean

    @Query("delete from https_false_positive_domain")
    abstract fun deleteAll()

    @Query("select count(1) from https_false_positive_domain")
    abstract fun count(): Int
}
