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

package com.duckduckgo.app.privacymonitor.model

import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardEntry
import javax.inject.Inject

interface NetworkLeaderboard {

    fun onNetworkDetected(networkName: String, domainVisited: String)
    fun networkPercents(): Array<NetworkPercent>
    fun shouldShow(): Boolean
    fun totalDomainsVisited(): Int

}

class NetworkPercent(val networkName: String, val percent: Float)

class DatabaseNetworkLeaderboard @Inject constructor(private val dao: NetworkLeaderboardDao) : NetworkLeaderboard {

    override fun onNetworkDetected(networkName: String, domainVisited: String) {
        dao.insert(NetworkLeaderboardEntry(networkName, domainVisited))
    }

    override fun networkPercents(): Array<NetworkPercent> {
        return dao.networkPercents(dao.totalDomainsVisited())
    }

    override fun shouldShow(): Boolean {
        return dao.shouldShow()
    }

    override fun totalDomainsVisited(): Int {
        return dao.totalDomainsVisited()
    }

}