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

package com.duckduckgo.app.privacymonitor.db

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import com.duckduckgo.app.privacymonitor.model.NetworkPercent

@Dao
interface NetworkLeaderboardDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(leaderboardEntry: NetworkLeaderboardEntry)

    @Query("select networkName, (1.0 * count(*) / :totalSites) as percent from network_leaderboard group by networkName order by percent desc")
    fun networkPercents(totalSites: Int) : Array<NetworkPercent>

    @Query("select (count(distinct networkName) >= 3) & (count(distinct domainVisited) > 10) from network_leaderboard")
    fun shouldShow(): Boolean

    @Query("select count(distinct domainVisited) from network_leaderboard")
    fun totalDomainsVisited(): Int

}
