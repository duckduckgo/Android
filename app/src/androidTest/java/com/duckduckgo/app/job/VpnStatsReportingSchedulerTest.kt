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

package com.duckduckgo.app.job

import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.mobile.android.vpn.dao.VpnStatsDao
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import org.junit.*
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit

class VpnStatsReportingSchedulerTest {

    private lateinit var testee: VpnStatsReportingScheduler

    private lateinit var db: VpnDatabase
    private lateinit var vpnStatsDao: VpnStatsDao

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager

    @Before
    fun setup() {

        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnStatsDao = db.vpnStatsDao()

        initializeWorkManager()
        testee = VpnStatsReportingScheduler(VpnStatsReportingRequestBuilder(), workManager, db)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenVpnHasBeenEnabledThenDailyPixelIsScheduled() {
        givenVpnHasBeenEnabled()

        testee.schedule()

        val reportingWorker = getWorkers(VpnStatsReportingRequestBuilder.VPN_STATS_REPORTING_WORK_TAG)
        Assert.assertFalse(reportingWorker.isEmpty())
    }

    @Test
    fun whenVpnHasNotBeenEnabledThenDailyPixelIsNotScheduled() {
        testee.schedule()

        val reportingWorker = getWorkers(VpnStatsReportingRequestBuilder.VPN_STATS_REPORTING_WORK_TAG)
        Assert.assertTrue(reportingWorker.isEmpty())
    }

    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    private fun getWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }

    private fun givenVpnHasBeenEnabled() {
        val startedAt = OffsetDateTime.now().minusHours(3)
        val finishedAt = OffsetDateTime.now().minusHours(1)
        val timeRunning = startedAt.until(finishedAt, ChronoUnit.MILLIS)

        val vpnStats = VpnStats(0, startedAt, finishedAt, timeRunning, 0, 0, 0, 0)
        vpnStatsDao.insert(vpnStats)
    }

}
