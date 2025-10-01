/*
 * Copyright (c) 2023 DuckDuckGo
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter.Companion.bucketByHour
import com.duckduckgo.mobile.android.vpn.dao.*
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class AppTrackerBlockingStatsRepositoryTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: VpnDatabase
    private lateinit var vpnTrackerDao: VpnTrackerDao
    private lateinit var repository: AppTrackerBlockingStatsRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnTrackerDao = db.vpnTrackerDao()
        repository = RealAppTrackerBlockingStatsRepository(db, coroutineRule.testDispatcherProvider)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSingleTrackerEntryAddedButNoRelatedEntityThenReturnNoTracker() = runBlocking {
        val trackerDomain = "example.com"
        addTrackerAndReturn(trackerDomain)
        val vpnTrackers = repository.getVpnTrackers({ dateOfPreviousMidnightAsString() }).firstOrNull()
        assertNoTrackers(vpnTrackers)
    }

    @Test
    fun whenSingleTrackerEntryAddedForTodayBucketThenBlockerReturned() = runBlocking {
        val trackerDomain = "example.com"
        val entity = addTrackerAndReturn(trackerDomain).asEntity()
        db.vpnAppTrackerBlockingDao().insertTrackerEntities(listOf(entity))
        val vpnTrackers = repository.getVpnTrackers({ dateOfPreviousMidnightAsString() }).firstOrNull()
        assertTrackerFound(vpnTrackers, trackerDomain)
        assertEquals(1, vpnTrackers!!.size)
    }

    @Test
    fun whenSameTrackerFoundMultipleTimesTodayThenAllInstancesOfBlockerReturned() = runBlocking {
        val trackerDomain = "example.com"
        addTrackerAndReturn(trackerDomain)
        val entity = addTrackerAndReturn(trackerDomain).asEntity()
        db.vpnAppTrackerBlockingDao().insertTrackerEntities(listOf(entity, entity))
        val vpnTrackers = repository.getVpnTrackers({ dateOfPreviousMidnightAsString() }).firstOrNull()
        assertTrackerFound(vpnTrackers, trackerDomain)
        assertEquals(2, vpnTrackers!!.sumOf { it.count })
    }

    @Test
    fun whenTrackerFoundBeforeTodayThenNotReturned() = runBlocking {
        trackerFoundYesterday()
        val vpnTrackers = repository.getVpnTrackers({ dateOfPreviousMidnightAsString() }).firstOrNull()
        assertNoTrackers(vpnTrackers)
    }

    @Test
    fun whenContainsVpnTrackersAndDbTableEmptyThenReturnFalse() = runTest {
        assertFalse(repository.containsVpnTrackers())
    }

    @Test
    fun whenContainsVpnTrackersAndDbTableNotEmptyThenReturnTrue() = runTest {
        addTrackerAndReturn()

        assertTrue(repository.containsVpnTrackers())
    }

    private fun dateOfPreviousMidnightAsString(): String {
        val midnight = LocalDateTime.now().toLocalDate().atStartOfDay()
        return bucketByHour(midnight)
    }

    private fun trackerFoundYesterday(trackerDomain: String = "example.com") {
        addTrackerAndReturn(trackerDomain, timestamp = yesterday())
    }

    private fun addTrackerAndReturn(
        domain: String = "example.com",
        trackerCompanyId: Int = -1,
        timestamp: String = bucketByHour(),
    ): VpnTracker {
        val defaultTrackingApp = TrackingApp("app.foo.com", "Foo App")
        val tracker = VpnTracker(
            trackerCompanyId = trackerCompanyId,
            domain = domain,
            timestamp = timestamp,
            company = "",
            companyDisplayName = "",
            trackingApp = defaultTrackingApp,
        )
        vpnTrackerDao.insert(tracker)
        return tracker
    }

    private fun VpnTracker.asEntity(): AppTrackerEntity {
        return AppTrackerEntity(
            trackerCompanyId = this.trackerCompanyId,
            entityName = "name",
            score = 0,
            signals = emptyList(),
        )
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

    private fun assertTrackerFound(
        vpnTrackers: List<VpnTracker>?,
        trackerDomain: String,
    ) {
        assertFalse(vpnTrackers.isNullOrEmpty())
        assertTrue(isTrackerInList(vpnTrackers, trackerDomain))
    }

    private fun isTrackerInList(
        vpnTrackers: List<VpnTracker>?,
        trackerDomain: String,
    ): Boolean {
        vpnTrackers!!.forEach {
            if (it.domain == trackerDomain) {
                return true
            }
        }
        return false
    }
}
