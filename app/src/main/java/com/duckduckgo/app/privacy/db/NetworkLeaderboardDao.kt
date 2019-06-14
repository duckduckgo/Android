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
import androidx.room.*

@Dao
abstract class NetworkLeaderboardDao {

    @Query("select count from sites_visited")
    abstract fun sitesVisited(): LiveData<Int>

    @Transaction
    open fun incrementSitesVisited() {
        val changedRows = incrementSitesVisitedIfExists()
        if (changedRows == 0) {
            initializeSitesVisited(SitesVisitedEntity(count = 1))
        }
    }

    @Insert
    protected abstract fun initializeSitesVisited(entity: SitesVisitedEntity)

    @Query("UPDATE sites_visited SET count = count + 1")
    protected abstract fun incrementSitesVisitedIfExists(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(leaderboardEntry: NetworkLeaderboardEntry)

    @Query(
        "select networkName, count(domainVisited) as domainCount " +
                "from network_leaderboard " +
                "group by networkName " +
                "order by domainCount desc"
    )
    abstract fun trackerNetworkTally(): LiveData<List<NetworkTally>>

    data class NetworkTally(val networkName: String, val domainCount: Int)
}