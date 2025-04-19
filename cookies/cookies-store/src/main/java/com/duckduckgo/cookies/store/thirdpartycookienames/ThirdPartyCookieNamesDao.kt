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

package com.duckduckgo.cookies.store.thirdpartycookienames

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.cookies.store.CookieNamesEntity

@Dao
abstract class ThirdPartyCookieNamesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertCookieNames(cookieNames: List<CookieNamesEntity>)

    @Transaction
    open fun updateAllCookieNames(cookieNames: List<CookieNamesEntity>) {
        deleteCookieNames()
        insertCookieNames(cookieNames)
    }

    @Query("select * from third_party_cookie_names")
    abstract fun getCookieNames(): List<CookieNamesEntity>

    @Query("delete from third_party_cookie_names")
    abstract fun deleteCookieNames()
}
