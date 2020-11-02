/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.stats

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.VpnCoroutineTestRule
import com.duckduckgo.mobile.android.vpn.dao.VpnStatsDao
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
@ExperimentalCoroutinesApi
class AppTrackerBlockingStatsRepositoryTest {


    @get:Rule
    @Suppress("unused")
    val coroutineRule = VpnCoroutineTestRule()

    private lateinit var db: VpnDatabase
    private lateinit var vpnStatsDao: VpnStatsDao
    private lateinit var repository: AppTrackerBlockingStatsRepository

    @Before
    fun before() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)

        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnStatsDao = db.vpnStatsDao()
        repository = AppTrackerBlockingStatsRepository(db)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenCompletedConnectionThenTotalTimeRunningIsCalculated() {
        givenConnection(OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2))

        val connections = whenConnectionStatsAreReturned()
        assertTrue(connections.timeRunning in 3600000..3600999)
    }

    @Test
    fun whenConnectionNotCompletedYetThenTimeRunningIsCalculated() {
        givenConnection(OffsetDateTime.now().minusHours(3), OffsetDateTime.now().plusHours(2))

        val connections = whenConnectionStatsAreReturned()
        assertTrue(connections.timeRunning in 18000000..18000999)
    }

    @Test
    fun whenConnectionsCompletedAndVpnStillRunningThenTimeRunningIsCalculated() {
        givenConnection(OffsetDateTime.now().minusHours(6), OffsetDateTime.now().minusHours(4))

        val connections = whenConnectionStatsAreReturned()
        assertTrue(connections.timeRunning in 7200000..7200099)
    }

    private fun whenConnectionStatsAreReturned(): VpnStats{
        return repository.getConnectionStats()!!
    }

    private fun givenConnection(startedAt: OffsetDateTime, finishedAt: OffsetDateTime) {
        val timeRunning = startedAt.until(finishedAt, ChronoUnit.MILLIS)
        val vpnStats = VpnStats(0, startedAt, finishedAt, timeRunning, 0, 0, 0, 0)
        vpnStatsDao.insert(vpnStats)
    }

}
