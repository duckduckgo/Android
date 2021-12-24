/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.report

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.content.edit
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import com.duckduckgo.mobile.android.vpn.ui.report.PrivacyReportViewModel
import com.jakewharton.threetenabp.AndroidThreeTen
import org.mockito.kotlin.mock
import dummy.ui.VpnPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.threeten.bp.LocalDateTime
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@ExperimentalTime
class PrivacyReportViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var repository: AppTrackerBlockingStatsRepository
    private lateinit var vpnPreferences: VpnPreferences
    private lateinit var db: VpnDatabase
    private lateinit var vpnTrackerDao: VpnTrackerDao
    private val deviceShieldPixels: DeviceShieldPixels = mock()
    private val onboardingStore = mock<DeviceShieldOnboardingStore>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testee: PrivacyReportViewModel

    @ExperimentalCoroutinesApi
    @Before
    fun before() {
        prepareDb()

        repository = AppTrackerBlockingStatsRepository(db)

        context.getSharedPreferences(VpnPreferences.PREFS_FILENAME, Context.MODE_PRIVATE).edit { clear() }
        vpnPreferences = VpnPreferences(context)

        testee = PrivacyReportViewModel(repository, deviceShieldPixels, onboardingStore, context)
    }

    private fun prepareDb() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnTrackerDao = db.vpnTrackerDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenTrackersBlockedThenReportHasTrackers() = runBlocking {

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
    fun whenTrackersBlockedOutsideTimeWindowThenReturnEmpty() = runBlocking {

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
    fun whenNoTrackersBlockedThenReportIsEmpty() = runBlocking {
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
        timestamp: String = DatabaseDateFormatter.bucketByHour()
    ) {
        val defaultTrackingApp = TrackingApp("app.foo.com", "Foo App")
        val tracker = VpnTracker(trackerCompanyId = trackerCompanyId, domain = domain, timestamp = timestamp, company = "", companyDisplayName = "", trackingApp = defaultTrackingApp)
        vpnTrackerDao.insert(tracker)
    }

    private fun yesterday(): String {
        val now = LocalDateTime.now()
        val yesterday = now.minusDays(1)
        return DatabaseDateFormatter.bucketByHour(yesterday)
    }
}
