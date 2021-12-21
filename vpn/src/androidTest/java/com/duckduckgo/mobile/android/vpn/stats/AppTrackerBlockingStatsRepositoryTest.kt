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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.dao.*
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter.Companion.bucketByHour
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.threeten.bp.LocalDateTime

@ExperimentalCoroutinesApi
class AppTrackerBlockingStatsRepositoryTest {

    @get:Rule @Suppress("unused") val coroutineRule = CoroutineTestRule()

    private lateinit var db: VpnDatabase
    private lateinit var vpnRunningStatsDao: VpnRunningStatsDao
    private lateinit var vpnTrackerDao: VpnTrackerDao
    private lateinit var vpnPhoenixDao: VpnPhoenixDao
    private lateinit var repository: AppTrackerBlockingStatsRepository

    @Before
    fun before() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        db =
            Room.inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    VpnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        vpnRunningStatsDao = db.vpnRunningStatsDao()
        vpnTrackerDao = db.vpnTrackerDao()
        vpnPhoenixDao = db.vpnPhoenixDao()
        repository = AppTrackerBlockingStatsRepository(db)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSingleTrackerEntryAddedForTodayBucketThenBlockerReturned() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)
        val vpnTrackers =
            repository.getVpnTrackers({ dateOfPreviousMidnightAsString() }).firstOrNull()
        assertTrackerFound(vpnTrackers, trackerDomain)
        assertEquals(1, vpnTrackers!!.size)
    }

    @Test
    fun whenSameTrackerFoundMultipleTimesTodayThenAllInstancesOfBlockerReturned() = runBlocking {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)
        trackerFound(trackerDomain)
        val vpnTrackers =
            repository.getVpnTrackers({ dateOfPreviousMidnightAsString() }).firstOrNull()
        assertTrackerFound(vpnTrackers, trackerDomain)
        assertEquals(2, vpnTrackers!!.size)
    }

    @Test
    fun whenTrackerFoundBeforeTodayThenNotReturned() = runBlocking {
        trackerFoundYesterday()
        val vpnTrackers =
            repository.getVpnTrackers({ dateOfPreviousMidnightAsString() }).firstOrNull()
        assertNoTrackers(vpnTrackers)
    }

    @Test
    fun whenSingleRunningTimeRecordedTodayThenThatTimeIsReturned() = runBlocking {
        val midnight = dateOfPreviousMidnightAsString()
        vpnRunningStatsDao.upsert(timeRunningMillis = 10, midnight)
        assertEquals(10L, repository.getRunningTimeMillis({ midnight }).first())
    }

    @Test
    fun whenMultipleRunningTimesRecordedTodayThenTotalTimeIsReturned() = runBlocking {
        val midnight = dateOfPreviousMidnight()
        vpnRunningStatsDao.upsert(timeRunningMillis = 10, bucketByHour(midnight))
        vpnRunningStatsDao.upsert(timeRunningMillis = 20, bucketByHour(midnight.plusMinutes(5)))
        vpnRunningStatsDao.upsert(timeRunningMillis = 30, bucketByHour(midnight.plusMinutes(10)))
        assertEquals(60L, repository.getRunningTimeMillis({ bucketByHour(midnight) }).first())
    }

    @Test
    fun whenRunningTimeRecordedYesterdayThenPreviousEventNoCounted() = runBlocking {
        val midnight = dateOfPreviousMidnight()
        vpnRunningStatsDao.upsert(timeRunningMillis = 10, bucketByHour(midnight))
        vpnRunningStatsDao.upsert(timeRunningMillis = 20, bucketByHour(midnight.plusMinutes(5)))
        vpnRunningStatsDao.upsert(timeRunningMillis = 30, bucketByHour(midnight.plusMinutes(10)))
        vpnRunningStatsDao.upsert(timeRunningMillis = 30, bucketByHour(midnight.minusMinutes(5)))
        assertEquals(60L, repository.getRunningTimeMillis({ bucketByHour(midnight) }).first())
    }

    @Test
    fun whenGetVpnRestartHistoryThenReturnHistory() {
        val entity = VpnPhoenixEntity(id = 1, reason = "foo")
        vpnPhoenixDao.insert(entity)

        assertEquals(listOf(entity), repository.getVpnRestartHistory())
    }

    @Test
    fun whenGetVpnRestartHistoryAndTableEmptyThenReturnEmptyList() {
        assertEquals(0, repository.getVpnRestartHistory().size)
    }

    @Test
    fun whenDeleteVpnRestartHistoryThenDeleteTableContents() {
        val entity = VpnPhoenixEntity(reason = "foo")
        vpnPhoenixDao.insert(entity)

        repository.deleteVpnRestartHistory()

        assertEquals(0, repository.getVpnRestartHistory().size)
    }

    private fun dateOfPreviousMidnight(): LocalDateTime {
        return LocalDateTime.now().toLocalDate().atStartOfDay()
    }

    private fun dateOfPreviousMidnightAsString(): String {
        val midnight = LocalDateTime.now().toLocalDate().atStartOfDay()
        return bucketByHour(midnight)
    }

    private fun trackerFoundYesterday(trackerDomain: String = "example.com") {
        trackerFound(trackerDomain, timestamp = yesterday())
    }

    private fun trackerFound(
        domain: String = "example.com",
        trackerCompanyId: Int = -1,
        timestamp: String = bucketByHour()
    ) {
        val defaultTrackingApp = TrackingApp("app.foo.com", "Foo App")
        val tracker =
            VpnTracker(
                trackerCompanyId = trackerCompanyId,
                domain = domain,
                timestamp = timestamp,
                company = "",
                companyDisplayName = "",
                trackingApp = defaultTrackingApp)
        vpnTrackerDao.insert(tracker)
    }

    private fun assertNoTrackers(vpnTrackers: List<VpnTracker>?) {
        assertNotNull(vpnTrackers)
        assertTrue(vpnTrackers!!.isEmpty())
    }

    private fun yesterday(): String {
        val now = LocalDateTime.now()
        val yesterday = now.minusDays(1)
        return bucketByHour(yesterday)
    }

    private fun assertTrackerFound(vpnTrackers: List<VpnTracker>?, trackerDomain: String) {
        assertFalse(vpnTrackers.isNullOrEmpty())
        assertTrue(isTrackerInList(vpnTrackers, trackerDomain))
    }

    private fun isTrackerInList(vpnTrackers: List<VpnTracker>?, trackerDomain: String): Boolean {
        vpnTrackers!!.forEach {
            if (it.domain == trackerDomain) {
                return true
            }
        }
        return false
    }
}
