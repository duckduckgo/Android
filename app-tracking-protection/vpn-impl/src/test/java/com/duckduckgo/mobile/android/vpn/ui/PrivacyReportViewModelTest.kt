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

package com.duckduckgo.mobile.android.vpn.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel
import java.time.LocalDateTime
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@ExperimentalTime
@RunWith(AndroidJUnit4::class)
class PrivacyReportViewModelTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var repository: AppTrackerBlockingStatsRepository
    private lateinit var db: VpnDatabase
    private val vpnStore = mock<VpnStore>()
    private val vpnStateMonitor = mock<VpnStateMonitor>()
    private val vpnFeatureRemover = mock<VpnFeatureRemover>()

    private lateinit var testee: PrivacyReportViewModel

    @Before
    fun before() {
        prepareDb()

        repository = RealAppTrackerBlockingStatsRepository(db, coroutineRule.testDispatcherProvider)

        testee = PrivacyReportViewModel(repository, vpnStore, vpnFeatureRemover, vpnStateMonitor, coroutineRule.testDispatcherProvider)
    }

    private fun prepareDb() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenTrackersBlockedThenReportHasTrackers() =
        runBlocking {
            trackerFound("dax.com")
            trackerFound("dax.com")
            trackerFound("dax.com")

            testee.getReport().test {
                val result = awaitItem()
                assertEquals(3, result.trackers)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenTrackersBlockedOutsideTimeWindowThenReturnEmpty() =
        runBlocking {
            trackerFoundYesterday("dax.com")
            trackerFoundYesterday("dax.com")
            trackerFoundYesterday("dax.com")

            testee.getReport().test {
                val result = awaitItem()
                assertEquals(0, result.trackers)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenNoTrackersBlockedThenReportIsEmpty() =
        runBlocking {
            testee.getReport().test {
                val result = awaitItem()
                assertEquals(0, result.trackers)
                cancelAndConsumeRemainingEvents()
            }
        }

    private fun trackerFoundYesterday(trackerDomain: String = "example.com") {
        trackerFound(trackerDomain, timestamp = yesterday())
    }

    private fun trackerFound(
        domain: String = "example.com",
        trackerCompanyId: Int = -1,
        timestamp: String = DatabaseDateFormatter.bucketByHour(),
    ) {
        val defaultTrackingApp = TrackingApp("app.foo.com", "Foo App")
        val tracker =
            VpnTracker(
                trackerCompanyId = trackerCompanyId,
                domain = domain,
                timestamp = timestamp,
                company = "",
                companyDisplayName = "",
                trackingApp = defaultTrackingApp,
            )
        val trackers = listOf(tracker)
        repository.insert(trackers)
        db.vpnAppTrackerBlockingDao().insertTrackerEntities(trackers.map { it.asEntity() })
    }

    private fun yesterday(): String {
        val now = LocalDateTime.now()
        val yesterday = now.minusDays(1)
        return DatabaseDateFormatter.bucketByHour(yesterday)
    }

    private fun VpnTracker.asEntity(): AppTrackerEntity =
        AppTrackerEntity(
            trackerCompanyId = this.trackerCompanyId,
            entityName = "name",
            score = 0,
            signals = emptyList(),
        )
}
