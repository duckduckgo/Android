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

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.NotificationScheduler.ClearDataNotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.PrivacyNotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.UseOurAppNotificationWorker
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.jvm.jvmName

class AndroidNotificationSchedulerTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val variantManager: VariantManager = mock()
    private val clearNotification: SchedulableNotification = mock()
    private val privacyNotification: SchedulableNotification = mock()
    private val useOurAppNotification: SchedulableNotification = mock()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        initializeWorkManager()

        testee = NotificationScheduler(
            workManager,
            clearNotification,
            privacyNotification,
            useOurAppNotification,
            variantManager
        )
    }

    // https://developer.android.com/topic/libraries/architecture/workmanager/how-to/integration-testing
    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun whenPrivacyNotificationClearDataCanShowThenPrivacyNotificationIsScheduled() = runBlocking<Unit> {
        setDefaultVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCanShowButClearDataCannotThenPrivacyNotificationIsScheduled() = runBlocking<Unit> {
        setDefaultVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCannotShowAndClearNotificationCanShowThenClearNotificationIsScheduled() = runBlocking<Unit> {
        setDefaultVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationAndClearNotificationCannotShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setDefaultVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenInAppUsageVariantAndUseOurAppNotificationCanShowThenNotificationScheduled() = runBlocking {
        givenNoInactiveUserNotifications()
        setInAppUsageVariant()
        whenever(useOurAppNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(UseOurAppNotificationWorker::class.jvmName, NotificationScheduler.USE_OUR_APP_WORK_REQUEST_TAG)
    }

    @Test
    fun whenInAppUsageVariantUseOurAppNotificationCannotShowThenNoNotificationScheduled() = runBlocking {
        givenNoInactiveUserNotifications()
        setInAppUsageVariant()
        whenever(useOurAppNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled(NotificationScheduler.USE_OUR_APP_WORK_REQUEST_TAG)
    }

    @Test
    fun whenInAppUsageSecondControlVariantThenNoNotificationScheduled() = runBlocking<Unit> {
        setInAppUsageSecondControlVariant()
        whenever(useOurAppNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled(NotificationScheduler.USE_OUR_APP_WORK_REQUEST_TAG)
    }

    @Test
    fun whenInAppUsageControlVariantThenNoNotificationScheduled() = runBlocking<Unit> {
        givenNoInactiveUserNotifications()
        setInAppUsageControlVariant()
        whenever(useOurAppNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled(NotificationScheduler.USE_OUR_APP_WORK_REQUEST_TAG)
    }

    @Test
    fun whenInAppUsageControlVariantAndPrivacyNotificationClearDataCanShowThenPrivacyNotificationIsScheduled() = runBlocking<Unit> {
        setInAppUsageControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenInAppUsageControlVariantAndPrivacyNotificationCanShowButClearDataCannotThenPrivacyNotificationIsScheduled() = runBlocking<Unit> {
        setInAppUsageControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenInAppUsageControlVariantAndPrivacyNotificationCannotShowAndClearNotificationCanShowThenClearNotificationScheduled() = runBlocking<Unit> {
        setInAppUsageControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenInAppUsageControlVariantAndPrivacyNotificationAndClearNotificationCannotShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setDefaultVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    private suspend fun givenNoInactiveUserNotifications() {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
    }

    private fun setInAppUsageVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant(
                "test",
                features = listOf(
                    VariantManager.VariantFeature.InAppUsage,
                    VariantManager.VariantFeature.RemoveDay1AndDay3Notifications,
                    VariantManager.VariantFeature.KillOnboarding
                ),
                filterBy = { true })
        )
    }

    private fun setInAppUsageSecondControlVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant(
                "test",
                features = listOf(
                    VariantManager.VariantFeature.RemoveDay1AndDay3Notifications,
                    VariantManager.VariantFeature.KillOnboarding
                ),
                filterBy = { true })
        )
    }

    private fun setInAppUsageControlVariant() {
        whenever(variantManager.getVariant()).thenReturn(Variant("test", features = emptyList(), filterBy = { true }))
    }

    private fun setDefaultVariant() {
        whenever(variantManager.getVariant()).thenReturn(DEFAULT_VARIANT)
    }

    private fun assertNotificationScheduled(workerName: String, tag: String = NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG) {
        assertTrue(getScheduledWorkers(tag).any { it.tags.contains(workerName) })
    }

    private fun assertNoNotificationScheduled(tag: String = NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG) {
        assertTrue(getScheduledWorkers(tag).isEmpty())
    }

    private fun getScheduledWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}
