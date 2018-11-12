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

package com.duckduckgo.app.privacy.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NetworkLeaderboardDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(siteVisited: SiteVisitedEntity)

    @Query("select count(distinct domain) from site_visited")
    fun domainsVisitedCount(): LiveData<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(leaderboardEntry: NetworkLeaderboardEntry)

    @Query(
        "select networkName, count(domainVisited) as domainCount " +
                "from network_leaderboard " +
                "group by networkName " +
                "order by domainCount desc"
    )
    fun trackerNetworkTally(): LiveData<List<NetworkTally>>

    data class NetworkTally(val networkName: String, val domainCount: Int)
}