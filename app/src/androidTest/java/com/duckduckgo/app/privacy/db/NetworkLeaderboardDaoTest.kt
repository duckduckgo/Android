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

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao.NetworkTally
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkLeaderboardDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NetworkLeaderboardDao

    @Before
    fun before() {
        db = Room
            .inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(), AppDatabase::class.java)
            .build()

        dao = db.networkLeaderboardDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenNetworksInsertedThenAddedToTallyWithCount() {
        dao.insert(NetworkLeaderboardEntry("Network1", domainVisited = "www.example1.com"))
        dao.insert(NetworkLeaderboardEntry("Network2", domainVisited = "www.example2.com"))
        dao.insert(NetworkLeaderboardEntry("Network3", domainVisited = "www.example3.com"))

        val data: List<NetworkTally>? = dao.trackerNetworkTally().blockingObserve()
        assertEquals(3, data!!.size)
        assertTrue(data.contains(NetworkTally("Network1", 1)))
        assertTrue(data.contains(NetworkTally("Network2", 1)))
        assertTrue(data.contains(NetworkTally("Network3", 1)))
    }

    @Test
    fun whenNetworksHaveMultipleDomainsThenAddedToTallyWithCountInDescendingOrder() {
        dao.insert(NetworkLeaderboardEntry("Network1", domainVisited = "www.example1.com"))
        dao.insert(NetworkLeaderboardEntry("Network2", domainVisited = "www.example1.com"))
        dao.insert(NetworkLeaderboardEntry("Network2", domainVisited = "www.example2.com"))
        dao.insert(NetworkLeaderboardEntry("Network2", domainVisited = "www.example3.com"))
        dao.insert(NetworkLeaderboardEntry("Network3", domainVisited = "www.example3.com"))
        dao.insert(NetworkLeaderboardEntry("Network3", domainVisited = "www.example4.com"))


        val data: List<NetworkTally> = dao.trackerNetworkTally().blockingObserve()!!
        assertEquals(NetworkTally("Network2", 3), data[0])
        assertEquals(NetworkTally("Network3", 2), data[1])
        assertEquals(NetworkTally("Network1", 1), data[2])
    }
}
