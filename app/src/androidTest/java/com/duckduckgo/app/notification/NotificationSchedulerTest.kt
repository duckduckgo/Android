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

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.AndroidNotificationScheduler.ClearDataNotificationWorker
import com.duckduckgo.app.notification.AndroidNotificationScheduler.PrivacyNotificationWorker
import com.duckduckgo.app.notification.AndroidNotificationScheduler.SearchPromptNotificationWorker
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SearchNotification
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.jvm.jvmName

class NotificationSchedulerTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val variantManager: VariantManager = mock()
    private val clearNotification: SchedulableNotification = mock()
    private val privacyNotification: SchedulableNotification = mock()
    private val searchPromptNotification: SearchNotification = mock()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var workManager = WorkManager.getInstance(context)
    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        testee = AndroidNotificationScheduler(
            workManager,
            clearNotification,
            privacyNotification,
            searchPromptNotification
        )
    }

    @After
    fun resetWorkers(){
        workManager.cancelAllWorkByTag(AndroidNotificationScheduler.CONTINUOUS_APP_USE_REQUEST_TAG)
    }

    @Test
    fun whenPrivacyNotificationClearDataAndSearchPromptCanShowThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenPrivacyNotificationCanShowButClearDataAndSearchPromptCannotThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenPrivacyNotificationAndSearchPromptCanShowButClearDataCannotThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenPrivacyNotificationAndSearchPromptCannotShowAndClearNotificationCanShowThenNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenPrivacyNotificationAndClearNotificationCannotShowButSearchPromptCanShowThenNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenPrivacyNotificationAndClearNotificationCannotShowButSearchPromptCanThenSearchPromptNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    private fun assertUnusedAppNotificationScheduled(workerName: String) {
        assertTrue(getUnusedAppScheduledWorkers().any { it.tags.contains(workerName) })
    }

    private fun assertContinuousAppUseNotificationScheduled(workerName: String) {
        assertTrue(getContinuousAppUseScheduledWorkers().any { it.tags.contains(workerName) })
    }

    private fun assertNoUnusedAppNotificationScheduled() {
        assertTrue(getUnusedAppScheduledWorkers().isEmpty())
    }

    private fun assertNoContinuousAppNotificationScheduled() {
        assertTrue(getContinuousAppUseScheduledWorkers().isEmpty())
    }

    private fun assertNoNotificationScheduled() {
        assertTrue(getUnusedAppScheduledWorkers().isEmpty())
        assertTrue(getContinuousAppUseScheduledWorkers().isEmpty())
    }

    private fun getUnusedAppScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(AndroidNotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }

    private fun getContinuousAppUseScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(AndroidNotificationScheduler.CONTINUOUS_APP_USE_REQUEST_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}