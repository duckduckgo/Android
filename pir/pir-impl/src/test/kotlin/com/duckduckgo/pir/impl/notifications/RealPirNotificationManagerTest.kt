/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.notifications

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class RealPirNotificationManagerTest {

    private val mockNotificationManagerCompat: NotificationManagerCompat = mock()

    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var testee: RealPirNotificationManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        application = context.applicationContext as Application
        testee = RealPirNotificationManager(context, mockNotificationManagerCompat)
    }

    @Test
    fun whenPermissionGrantedThenFreemiumScanCompleteNotificationIsPostedWithFreemiumCopy() {
        shadowOf(application).grantPermissions(POST_NOTIFICATIONS)

        val posted = testee.showFreemiumScanCompleteNotification()

        assertTrue(posted)
        val notification = capturePostedNotification()
        assertEquals("Free Personal Information Scan", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            "Your free personal info scan is now complete. Check out the results…",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun whenPermissionDeniedThenFreemiumScanCompleteNotificationIsNotPosted() {
        shadowOf(application).denyPermissions(POST_NOTIFICATIONS)

        val posted = testee.showFreemiumScanCompleteNotification()

        assertFalse(posted)
        verify(mockNotificationManagerCompat, never()).notify(any<Int>(), any<Notification>())
    }

    @Test
    fun whenFreemiumScanCompleteNotificationIsPostedThenItCreatesThePirChannel() {
        shadowOf(application).grantPermissions(POST_NOTIFICATIONS)

        testee.showFreemiumScanCompleteNotification()

        val systemNotificationManager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(systemNotificationManager.getNotificationChannel(PIR_CHANNEL_ID))
    }

    @Test
    fun whenFreemiumScanCompleteNotificationIsPostedWhileProgressNotificationExistsThenTapIsStillAttributed() {
        shadowOf(application).grantPermissions(POST_NOTIFICATIONS)
        val progressNotification = testee.createScanStatusNotification(title = "title", message = "message")

        testee.showFreemiumScanCompleteNotification()

        val contentIntent = shadowOf(capturePostedNotification().contentIntent)
        assertEquals(PirDashboardWebViewActivity::class.java.name, contentIntent.savedIntent.component?.className)
        assertTrue(contentIntent.savedIntent.getBooleanExtra(PirDashboardWebViewActivity.EXTRA_FROM_FREEMIUM_SCAN_COMPLETE_NOTIFICATION, false))
        assertNotEquals(shadowOf(progressNotification.contentIntent).requestCode, contentIntent.requestCode)
    }

    @Test
    fun whenFreemiumScanCompleteNotificationIsPostedThenItIsAutoCancelled() {
        shadowOf(application).grantPermissions(POST_NOTIFICATIONS)

        testee.showFreemiumScanCompleteNotification()

        assertTrue(capturePostedNotification().flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun whenScanStatusNotificationIsCreatedThenItIsNotAutoCancelled() {
        val notification = testee.createScanStatusNotification(title = "title", message = "message")

        assertFalse(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    private fun capturePostedNotification(): Notification {
        val captor = argumentCaptor<Notification>()
        verify(mockNotificationManagerCompat).notify(eq(SCAN_COMPLETE_NOTIFICATION_ID), captor.capture())
        return captor.firstValue
    }

    private companion object {
        // Hardcoded, not shared with production: the freemium and paid scan complete notifications must share this ID so they replace each other.
        const val SCAN_COMPLETE_NOTIFICATION_ID = 987
        const val PIR_CHANNEL_ID = "com.duckduckgo.pir.PirNotificationChannel"
    }
}
