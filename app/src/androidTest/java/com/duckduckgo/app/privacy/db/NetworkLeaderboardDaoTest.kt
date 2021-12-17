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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.global.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NetworkLeaderboardDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NetworkLeaderboardDao

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun before() {
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        dao = db.networkLeaderboardDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenNetworkThatDoesNotExistIncrementedThenAddedToDatabase() {
        dao.incrementNetworkCount("Network1")
        val data: List<NetworkLeaderboardEntry>? = dao.trackerNetworkLeaderboard().blockingObserve()
        assertEquals(1, data!!.size)
        assertTrue(data.contains(NetworkLeaderboardEntry("Network1", 1)))
    }

    @Test
    fun whenNetworksIncrementedMultipleTimesThenReturnedWithCountInDescendingOrder() {
        dao.incrementNetworkCount("Network1")
        dao.incrementNetworkCount("Network2")
        dao.incrementNetworkCount("Network2")
        dao.incrementNetworkCount("Network2")
        dao.incrementNetworkCount("Network3")
        dao.incrementNetworkCount("Network3")

        val data: List<NetworkLeaderboardEntry> =
            dao.trackerNetworkLeaderboard().blockingObserve()!!
        assertEquals(NetworkLeaderboardEntry("Network2", 3), data[0])
        assertEquals(NetworkLeaderboardEntry("Network3", 2), data[1])
        assertEquals(NetworkLeaderboardEntry("Network1", 1), data[2])
    }

    @Test
    fun whenSitesVisitedIncrementedThenSiteVisitedCountIncreasesByOne() {
        dao.incrementSitesVisited()
        assertEquals(1, dao.sitesVisited().blockingObserve())
        dao.incrementSitesVisited()
        assertEquals(2, dao.sitesVisited().blockingObserve())
    }
}
