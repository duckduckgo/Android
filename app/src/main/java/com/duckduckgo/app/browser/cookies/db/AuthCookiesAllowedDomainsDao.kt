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

package com.duckduckgo.app.browser.cookies.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AuthCookiesAllowedDomainsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(authCookieAllowedDomainEntity: AuthCookieAllowedDomainEntity): Long

    @Delete
    fun delete(authCookieAllowedDomainEntity: AuthCookieAllowedDomainEntity)

    @Query("SELECT * FROM auth_cookies_allowed_domains WHERE domain = :host limit 1")
    fun getDomain(host: String): AuthCookieAllowedDomainEntity?

    @Query("DELETE FROM auth_cookies_allowed_domains WHERE domain NOT IN (:exceptionList)")
    fun deleteAll(exceptionList: String)
}
