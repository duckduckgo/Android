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

package com.duckduckgo.mobile.android.vpn.ui.notification

import android.graphics.Typeface
import android.text.style.StyleSpan
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerCompanyDao
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerCompany
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.TrackerListProvider
import com.jakewharton.threetenabp.AndroidThreeTen
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class DeviceShieldNotificationRepositoryTest {

    private lateinit var db: VpnDatabase
    private lateinit var vpnTrackerDao: VpnTrackerDao
    private lateinit var vpnTrackerCompanyDao: VpnTrackerCompanyDao
    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository

    private lateinit var repository: DeviceShieldNotificationRepository

    @Before
    fun before() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnTrackerDao = db.vpnTrackerDao()
        vpnTrackerCompanyDao = db.vpnTrackerCompanyDao()
        appTrackerBlockingStatsRepository = AppTrackerBlockingStatsRepository(db)

        vpnTrackerCompanyDao.insert(
            VpnTrackerCompany(
                TrackerListProvider.UNDEFINED_TRACKER_COMPANY.trackerCompanyId,
                TrackerListProvider.UNDEFINED_TRACKER_COMPANY.company
            )
        )
        repository = DeviceShieldNotificationRepository(InstrumentationRegistry.getInstrumentation().targetContext.resources, appTrackerBlockingStatsRepository)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun createsTotalTrackersNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = repository.createDailyNotification(0)

        Assert.assertEquals("Device Shield has blocked 1 tracker so far. Tap to view your full Privacy Report", notification.text.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.text
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(26, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(35, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenTotalTrackersNotificationWhenToTrackersFound() {
        val notification = repository.createDailyNotification(0)
        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsTopTrackerCompanyNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = repository.createDailyNotification(1)

        Assert.assertEquals("Tracking LLC is your top blocked company \u2028so far \uD83D\uDE2E", notification.text.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.text
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(12, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenTopTrackerCompanyNotificationWhenNoTrackersFound() {
        val notification = repository.createDailyNotification(1)

        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsTopTrackerCompanyNumbersNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = repository.createDailyNotification(2)

        Assert.assertEquals("Device Shield has blocked Tracking LLC 1 time so far", notification.text.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.text
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(26, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(38, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenTopTrackerCompanyNumbersNotificationWhenNoTrackersFound() {
        val notification = repository.createDailyNotification(2)

        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsLastCompanyAttemptNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = repository.createDailyNotification(3)

        Assert.assertEquals("Tracking LLC was the last company who tried to track you \uD83D\uDE31 (Device Shield blocked them!)", notification.text.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.text
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(12, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenLastCompanyAttemptNotificationWhenNoTrackersFound() {
        val notification = repository.createDailyNotification(3)
        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = repository.createWeeklyNotification(0)

        Assert.assertEquals("Device Shield blocked 1 company and 1 tracker (past week)", notification.text.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.text
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(36, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(45, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenWeeklyReportNotificationWhenNoTrackersFound() {
        val notification = repository.createWeeklyNotification(0)
        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsWeeklyTopTrackerCompanyNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = repository.createWeeklyNotification(1)

        Assert.assertEquals("See how many times Device Shield blocked Tracking LLC (past week)", notification.text.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.text
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(41, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(53, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenWeeklyTopTrackerCompanyNotificationWhenNoTrackersFound() {
        val notification = repository.createWeeklyNotification(1)
        Assert.assertTrue(notification.hidden)
    }

    private fun trackerFound(
        domain: String = "example.com",
        trackerCompanyId: Int = TrackerListProvider.UNDEFINED_TRACKER_COMPANY.trackerCompanyId,
        timestamp: String = DatabaseDateFormatter.bucketByHour()
    ) {
        val tracker = VpnTracker(trackerCompanyId = trackerCompanyId, domain = domain, timestamp = timestamp)
        vpnTrackerDao.insert(tracker)
    }

}