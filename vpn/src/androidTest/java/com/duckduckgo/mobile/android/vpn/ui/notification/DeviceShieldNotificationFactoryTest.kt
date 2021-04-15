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
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.TrackerListProvider
import com.jakewharton.threetenabp.AndroidThreeTen
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class DeviceShieldNotificationFactoryTest {

    private lateinit var db: VpnDatabase
    private lateinit var vpnTrackerDao: VpnTrackerDao
    private lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository

    private lateinit var factory: DeviceShieldNotificationFactory

    @Before
    fun before() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        vpnTrackerDao = db.vpnTrackerDao()
        appTrackerBlockingStatsRepository = AppTrackerBlockingStatsRepository(db)

        factory = DeviceShieldNotificationFactory(InstrumentationRegistry.getInstrumentation().targetContext.resources, appTrackerBlockingStatsRepository)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun createsDeviceShieldEnabledNotification() {
        val notification = factory.createEnabledDeviceShieldNotification()
        Assert.assertEquals("Device Shield is enabled and blocking trackers across your whole device", notification.title.toString())
        Assert.assertFalse(notification.hidden)
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenTrackersFoundFromOneCompany() {
        val trackers = listOf(aTrackerAndCompany())
        val notification = factory.createTrackersCountDeviceShieldNotification(trackers)

        Assert.assertEquals("1 tracker blocked (past hour)", notification.title.toString())
        Assert.assertEquals("from Tracking LLC", notification.message.toString())

        val spannedText = notification.title
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(17, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenTrackersFoundFromTwoCompanies() {
        val trackers = listOf(aTrackerAndCompany(), aTrackerAndCompany(trackerCompanyName = "NotGoogle", trackerCompanyId = 1))
        val notification = factory.createTrackersCountDeviceShieldNotification(trackers)

        Assert.assertEquals("2 trackers blocked (past hour)", notification.title.toString())
        Assert.assertEquals("Tracking LLC and NotGoogle", notification.message.toString())

        val spannedText = notification.title
        val textSpans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        Assert.assertEquals(1, textSpans.size)
        val styleTextSpan = textSpans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleTextSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleTextSpan).toLong())
        Assert.assertEquals(18, spannedText.getSpanEnd(styleTextSpan).toLong())

        val spannedMessage = notification.message
        val messageSpans = spannedMessage.getSpans(0, spannedMessage.length, Any::class.java)

        Assert.assertEquals(2, messageSpans.size)
        val firstCompanySpan = messageSpans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), firstCompanySpan.style.toLong())
        Assert.assertEquals(0, spannedMessage.getSpanStart(firstCompanySpan).toLong())
        Assert.assertEquals(12, spannedMessage.getSpanEnd(firstCompanySpan).toLong())
        val secondCompanySpan = messageSpans[1] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), secondCompanySpan.style.toLong())
        Assert.assertEquals(17, spannedMessage.getSpanStart(secondCompanySpan).toLong())
        Assert.assertEquals(26, spannedMessage.getSpanEnd(secondCompanySpan).toLong())
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenTrackersFoundFromThreeCompanies() {
        val trackers = listOf(
            aTrackerAndCompany(),
            aTrackerAndCompany(trackerCompanyName = "NotGoogle", trackerCompanyId = 1),
            aTrackerAndCompany(trackerCompanyName = "NotFacebook", trackerCompanyId = 2)
        )
        val notification = factory.createTrackersCountDeviceShieldNotification(trackers)

        Assert.assertEquals("3 trackers blocked (past hour)", notification.title.toString())
        Assert.assertEquals("Tracking LLC, NotGoogle and NotFacebook", notification.message.toString())

        val spannedText = notification.title
        val textSpans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        Assert.assertEquals(1, textSpans.size)
        val styleTextSpan = textSpans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleTextSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleTextSpan).toLong())
        Assert.assertEquals(18, spannedText.getSpanEnd(styleTextSpan).toLong())

        val spannedMessage = notification.message
        val messageSpans = spannedMessage.getSpans(0, spannedMessage.length, Any::class.java)

        Assert.assertEquals(3, messageSpans.size)
        val firstCompanySpan = messageSpans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), firstCompanySpan.style.toLong())
        Assert.assertEquals(0, spannedMessage.getSpanStart(firstCompanySpan).toLong())
        Assert.assertEquals(12, spannedMessage.getSpanEnd(firstCompanySpan).toLong())
        val secondCompanySpan = messageSpans[1] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), secondCompanySpan.style.toLong())
        Assert.assertEquals(14, spannedMessage.getSpanStart(secondCompanySpan).toLong())
        Assert.assertEquals(23, spannedMessage.getSpanEnd(secondCompanySpan).toLong())
        val thirdCompanySpan = messageSpans[2] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), thirdCompanySpan.style.toLong())
        Assert.assertEquals(28, spannedMessage.getSpanStart(thirdCompanySpan).toLong())
        Assert.assertEquals(39, spannedMessage.getSpanEnd(thirdCompanySpan).toLong())
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenTrackersFoundFromThreeOrMoreCompanies() {
        val trackers = listOf(
            aTrackerAndCompany(),
            aTrackerAndCompany(trackerCompanyName = "NotGoogle", trackerCompanyId = 1),
            aTrackerAndCompany(trackerCompanyName = "NotFacebook", trackerCompanyId = 2),
            aTrackerAndCompany(trackerCompanyName = "NotAmazon", trackerCompanyId = 3)
        )
        val notification = factory.createTrackersCountDeviceShieldNotification(trackers)

        Assert.assertEquals("4 trackers blocked (past hour)", notification.title.toString())
        Assert.assertEquals("Tracking LLC, NotGoogle and 2 more companies", notification.message.toString())

        val spannedText = notification.title
        val textSpans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        Assert.assertEquals(1, textSpans.size)
        val styleTextSpan = textSpans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleTextSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleTextSpan).toLong())
        Assert.assertEquals(18, spannedText.getSpanEnd(styleTextSpan).toLong())

        val spannedMessage = notification.message
        val messageSpans = spannedMessage.getSpans(0, spannedMessage.length, Any::class.java)

        Assert.assertEquals(2, messageSpans.size)
        val firstCompanySpan = messageSpans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), firstCompanySpan.style.toLong())
        Assert.assertEquals(0, spannedMessage.getSpanStart(firstCompanySpan).toLong())
        Assert.assertEquals(12, spannedMessage.getSpanEnd(firstCompanySpan).toLong())
        val secondCompanySpan = messageSpans[1] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), secondCompanySpan.style.toLong())
        Assert.assertEquals(14, spannedMessage.getSpanStart(secondCompanySpan).toLong())
        Assert.assertEquals(23, spannedMessage.getSpanEnd(secondCompanySpan).toLong())
    }

    @Test
    fun createTrackersCountDeviceShieldNotificationWhenNoTrackersFound() {
        val notification = factory.createTrackersCountDeviceShieldNotification(emptyList())

        Assert.assertEquals("Scanning for trackers… beep… boop", notification.title.toString())
        Assert.assertEquals("No trackers blocked", notification.message.toString())
    }

    @Test
    fun createsTotalTrackersNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.createDailyDeviceShieldNotification(0)

        Assert.assertEquals("Device Shield has blocked 1 tracker so far. Tap to view your full Privacy Report.", notification.title.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.title
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(26, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(35, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenTotalTrackersNotificationWhenNoTrackersFound() {
        val notification = factory.createDailyDeviceShieldNotification(0)
        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsTopTrackerCompanyNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.createDailyDeviceShieldNotification(1)

        Assert.assertEquals("Tracking LLC is your top blocked company so far \uD83D\uDE2E", notification.title.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.title
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(12, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenTopTrackerCompanyNotificationWhenNoTrackersFound() {
        val notification = factory.createDailyDeviceShieldNotification(1)

        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsTopTrackerCompanyNumbersNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.createDailyDeviceShieldNotification(2)

        Assert.assertEquals("Device Shield has blocked Tracking LLC 1 time so far", notification.title.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.title
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(26, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(38, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenTopTrackerCompanyNumbersNotificationWhenNoTrackersFound() {
        val notification = factory.createDailyDeviceShieldNotification(2)

        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsLastCompanyAttemptNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.createDailyDeviceShieldNotification(3)

        Assert.assertEquals(
            "Tracking LLC was the last company who tried to track you \uD83D\uDE31 (Device Shield blocked them!)",
            notification.title.toString()
        )
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.title
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(0, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(12, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenLastCompanyAttemptNotificationWhenNoTrackersFound() {
        val notification = factory.createDailyDeviceShieldNotification(3)
        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsWeeklyReportNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.createWeeklyDeviceShieldNotification(0)

        Assert.assertEquals("Device Shield blocked 1 company and 1 tracker (past week)", notification.title.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.title
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(36, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(45, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenWeeklyReportNotificationWhenNoTrackersFound() {
        val notification = factory.createWeeklyDeviceShieldNotification(0)
        Assert.assertTrue(notification.hidden)
    }

    @Test
    fun createsWeeklyTopTrackerCompanyNotificationWhenTrackersFound() {
        val trackerDomain = "example.com"
        trackerFound(trackerDomain)

        val notification = factory.createWeeklyDeviceShieldNotification(1)

        Assert.assertEquals("See how many times Device Shield blocked Tracking LLC (past week)", notification.title.toString())
        Assert.assertFalse(notification.hidden)

        val spannedText = notification.title
        val spans = spannedText.getSpans(0, spannedText.length, Any::class.java)

        val styleSpan = spans[0] as StyleSpan
        Assert.assertEquals(Typeface.BOLD.toLong(), styleSpan.style.toLong())
        Assert.assertEquals(41, spannedText.getSpanStart(styleSpan).toLong())
        Assert.assertEquals(53, spannedText.getSpanEnd(styleSpan).toLong())
    }

    @Test
    fun createsHiddenWeeklyTopTrackerCompanyNotificationWhenNoTrackersFound() {
        val notification = factory.createWeeklyDeviceShieldNotification(1)
        Assert.assertTrue(notification.hidden)
    }

    private fun trackerFound(
        domain: String = "example.com",
        trackerCompanyId: Int = -1,
        timestamp: String = DatabaseDateFormatter.bucketByHour()
    ) {
        val tracker = VpnTracker(trackerCompanyId = trackerCompanyId, domain = domain, timestamp = timestamp, company = "Tracking LLC")
        vpnTrackerDao.insert(tracker)
    }

    private fun aTrackerAndCompany(
        domain: String = "example.com",
        trackerCompanyName: String = "Tracking LLC",
        trackerCompanyId: Int = -1,
        timestamp: String = DatabaseDateFormatter.bucketByHour()
    ): VpnTracker {
        return VpnTracker(trackerCompanyId = trackerCompanyId, domain = domain, timestamp = timestamp, company = trackerCompanyName)
    }

}
