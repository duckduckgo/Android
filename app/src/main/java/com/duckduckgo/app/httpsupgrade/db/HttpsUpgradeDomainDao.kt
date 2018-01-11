/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import org.intellij.lang.annotations.Language

@Dao
interface HttpsUpgradeDomainDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg domains: HttpsUpgradeDomain)

    @Language("RoomSql")
    @Query("select count(1) > 0 from https_upgrade_domain where domain = :domain")
    fun hasDomain(domain: String) : Boolean

    @Query("delete from https_upgrade_domain")
    fun deleteAll()

}