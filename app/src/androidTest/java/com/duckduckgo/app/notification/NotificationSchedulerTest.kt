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

import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifcationManagerCompat = NotificationManagerCompat.from(context)
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private val mockNotificationsDao: NotificationDao = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()

    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        testee = NotificationScheduler(
            mockNotificationsDao,
            notifcationManagerCompat,
            mockSettingsDataStore
        )
    }

    @Test
    fun whenNotificationtNotSeenAndOptionNotSetThenNotificationScheduled() {
        setup(false, ClearWhatOption.CLEAR_NONE)
        testee.scheduleNextNotification(testScope)
        assertTrue(notificationScheduled())
    }

    @Test
    fun whenNotificationNotSeenAndClearOptionsAlreadySetThenNotificationNotScheduled() {
        setup(false, ClearWhatOption.CLEAR_TABS_ONLY)
        testee.scheduleNextNotification(testScope)
        assertFalse(notificationScheduled())
    }

    @Test
    fun whenNotificationAlreadySeenAndOptionNotSetThenNotificationNotScheduled() {
        setup(true, ClearWhatOption.CLEAR_NONE)
        testee.scheduleNextNotification(testScope)
        assertFalse(notificationScheduled())
    }

    private fun setup(notificationSeen: Boolean, clearWhatOption: ClearWhatOption) {
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