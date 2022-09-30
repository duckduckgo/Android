/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.cookies.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class CookiesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertFirstPartyCookiesTrackersPolicy(firstPartyTrackerCookiePolicy: FirstPartyTrackerCookiePolicyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAllTrackingCookie1pExceptions(exceptions: List<TrackingCookie1pExceptionEntity>)

    @Transaction
    open fun updateAll(exceptions: List<TrackingCookie1pExceptionEntity>, firstPartyTrackerCookiePolicy: FirstPartyTrackerCookiePolicyEntity) {
        deleteFirstPartyCookiesTrackersPolicy()
        deleteAllTrackingCookie1pExceptions()
        insertAllTrackingCookie1pExceptions(exceptions)
        insertFirstPartyCookiesTrackersPolicy(firstPartyTrackerCookiePolicy)
    }

    @Query("select * from first_party_cookie_tracker_policy")
    abstract fun getFirstPartyCookieTrackersPolicy(): FirstPartyTrackerCookiePolicyEntity?

    @Query("select * from tracking_cookies_1p_exceptions")
    abstract fun getAllTrackingCookie1pExceptions(): List<TrackingCookie1pExceptionEntity>

    @Query("delete from first_party_cookie_tracker_policy")
    abstract fun deleteFirstPartyCookiesTrackersPolicy()

    @Query("delete from tracking_cookies_1p_exceptions")
    abstract fun deleteAllTrackingCookie1pExceptions()
}
