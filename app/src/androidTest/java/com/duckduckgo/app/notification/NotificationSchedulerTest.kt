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
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    private val variantManager: VariantManager = mock()
    private val clearNotification: SchedulableNotification = mock()
    private val privacyNotification: SchedulableNotification = mock()

    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        testee = NotificationScheduler(
            variantManager,
            clearNotification,
            privacyNotification
        )
    }

    @Test
    fun whenClearNotificationCanShowThenNotificationScheduled() {
        whenever(runBlocking { clearNotification.canShow() }).thenReturn(true)
        testee.scheduleNextNotification(testScope)
        assertTrue(notificationScheduled())
    }

    @Test
    fun whenClearNotificationCannotShowThenNotificationNotScheduled() {
        whenever(runBlocking { clearNotification.canShow() }).thenReturn(false)
        testee.scheduleNextNotification(testScope)
        assertFalse(notificationScheduled())
    }

    private fun notificationScheduled(): Boolean {
        return WorkManager
            .getInstance()
            .getWorkInfosByTag(NotificationScheduler.WORK_REQUEST_TAG)
            .get()
            .any { it.state == WorkInfo.State.ENQUEUED }
    }
}