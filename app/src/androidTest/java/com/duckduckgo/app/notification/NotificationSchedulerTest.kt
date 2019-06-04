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


@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.notification

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.duckduckgo.app.notification.NotificationScheduler.ClearDataNotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.PrivacyNotificationWorker
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.reflect.jvm.jvmName

class NotificationSchedulerTest {

    private val variantManager: VariantManager = mock()
    private val clearNotification: SchedulableNotification = mock()
    private val privacyNotification: SchedulableNotification = mock()

    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        testee = NotificationScheduler(
            clearNotification,
            privacyNotification
        )
    }

    @Test
    fun whenBothPrivacyNotificationAndCleatDataCanShowThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()
        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCanShowAndCleatDataCannotThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()
        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCannotShowAndClearNotificationCanShowThenNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()
        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()
        assertNoNotificationScheduled()
    }

    private fun assertNotificationScheduled(workerName: String) {
        assertTrue(getScheduledWorkers().any { it.tags.contains(workerName) })
    }

    private fun assertNoNotificationScheduled() {
        assertTrue(getScheduledWorkers().isEmpty())
    }

    private fun getScheduledWorkers(): List<WorkInfo> {
        return WorkManager
            .getInstance()
            .getWorkInfosByTag(NotificationScheduler.WORK_REQUEST_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}