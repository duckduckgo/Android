/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.notification

import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayOne
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.NotificationDayThree
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotificationSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifcationManagerCompat = NotificationManagerCompat.from(context)
    private val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private val mockNotificationsDao: NotificationDao = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockVariantManager: VariantManager = mock()
    private val mockPixel: Pixel = mock()

    private lateinit var testee: NotificationScheduler

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Before
    fun before() {
        whenever(mockVariantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        testee = NotificationScheduler(
            mockNotificationsDao,
            notifcationManagerCompat,
            notificationManager,
            mockSettingsDataStore,
            mockVariantManager,
            mockPixel
        )
    }

    @Test
    fun whenNotificationsPreviouslyOffAndNowOnThenPixelIsFiredAndSettingsUpdated() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(false)
        testee.updateNotificationStatus(true)
        verify(mockPixel).fire(eq(Pixel.PixelName.NOTIFICATIONS_ENABLED), any())
        verify(mockSettingsDataStore).appNotificationsEnabled = true
    }

    @Test
    fun whenNotificationsPreviouslyOffAndStillOffThenNoPixelIsFiredAndSettingsUnchanged() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(false)
        testee.updateNotificationStatus(false)
        verify(mockPixel, never()).fire(any(), any())
        verify(mockSettingsDataStore, never()).appNotificationsEnabled = true
    }

    @Test
    fun whenNotificationsPreviouslyOnAndStillOnThenNoPixelIsFiredAndSettingsUnchanged() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(true)
        testee.updateNotificationStatus(true)
        verify(mockPixel, never()).fire(any(), any())
        verify(mockSettingsDataStore, never()).appNotificationsEnabled = false
    }

    @Test
    fun whenNotificationsPreviouslyOnAndNowOffPixelIsFiredAndSettingsUpdated() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(true)
        testee.updateNotificationStatus(false)
        verify(mockPixel).fire(eq(Pixel.PixelName.NOTIFICATIONS_DISABLED), any())
        verify(mockSettingsDataStore).appNotificationsEnabled = false
    }

    @Test
    fun whenInNotificationDayOneFeatureAndNotificaiontNotSeenAndOptionNotSetThenNotificationScheduled() {
        setup(NotificationDayOne, false, ClearWhatOption.CLEAR_NONE)
        testee.scheduleNextNotification()
        assertTrue(notificationScheduled())
    }

    @Test
    fun whenInNotificationDayThreeFeatureAndNotificaionNotSeenAndOptionNotSetThenNotificationScheduled() {
        setup(NotificationDayThree, false, ClearWhatOption.CLEAR_NONE)
        testee.scheduleNextNotification()
        assertTrue(notificationScheduled())
    }

    @Test
    fun whenndNotificaionNotSeenAndOptionNotSetButNoNotificationFeatureOffThenNotificationNotScheduled() {
        setup(null, false, ClearWhatOption.CLEAR_NONE)
        testee.scheduleNextNotification()
        assertFalse(notificationScheduled())
    }

    @Test
    fun whenInNotificationCohortAndNotificationNotSeenButClearOptionsAlreadySetThenNotificationNotScheduled() {
        setup(NotificationDayOne, false, ClearWhatOption.CLEAR_TABS_ONLY)
        testee.scheduleNextNotification()
        assertFalse(notificationScheduled())
    }

    @Test
    fun whenInNotificationCohortAndOptionNotSetButNotificationAlreadySeenThenNotificationNotScheduled() {
        setup(NotificationDayOne, true, ClearWhatOption.CLEAR_NONE)
        testee.scheduleNextNotification()
        assertFalse(notificationScheduled())
    }

    private fun setup(feature: VariantManager.VariantFeature?, notificationSeen: Boolean, clearWhatOption: ClearWhatOption) {
        val variant = if (feature == null) DEFAULT_VARIANT else VariantManager.ACTIVE_VARIANTS.find { it.hasFeature(feature) }
        whenever(mockVariantManager.getVariant(any())).thenReturn(variant)
        whenever(mockNotificationsDao.exists(any())).thenReturn(notificationSeen)
        whenever(mockSettingsDataStore.automaticallyClearWhatOption).thenReturn(clearWhatOption)
    }

    private fun notificationScheduled(): Boolean {
        return WorkManager
            .getInstance()
            .getWorkInfosByTag(NotificationScheduler.WORK_REQUEST_TAG)
            .get()
            .any { it.state == WorkInfo.State.ENQUEUED }
    }
}