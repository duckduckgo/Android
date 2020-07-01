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
import com.duckduckgo.app.notification.NotificationScheduler.DripA1NotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.DripA2NotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.DripB1NotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.DripB2NotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.UseOurAppNotificationWorker
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DripNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1PrivacyNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripA1Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripA2Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripB1Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1DripB2Notification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day3ClearDataNotification
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
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
    private val mockUserStageStore: UserStageStore = mock()
    private val clearNotification: SchedulableNotification = mock()
    private val privacyNotification: SchedulableNotification = mock()
    private val dripA1Notification: SchedulableNotification = mock()
    private val dripA2Notification: SchedulableNotification = mock()
    private val dripB1Notification: SchedulableNotification = mock()
    private val dripB2Notification: SchedulableNotification = mock()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        initializeWorkManager()
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        testee = NotificationScheduler(
            workManager,
            clearNotification,
            privacyNotification,
            dripA1Notification,
            dripA2Notification,
            dripB1Notification,
            dripB2Notification,
            variantManager,
            mockUserStageStore
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
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCanShowButClearDataCannotThenPrivacyNotificationIsScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCannotShowAndClearNotificationCanShowThenClearNotificationIsScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationAndClearNotificationCannotShowThenNoNotificationScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    // Drip A1
    @Test
    fun whenDripA1VariantAndDripA1NotificationCanShowAndClearNotificationCannotShowThenDripA1NotificationIsScheduled() = runBlocking<Unit> {
        setDripA1Variant()
        whenever(dripA1Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripA1NotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripA1VariantAndClearNotificationCanShotAndDripA1NotificationCannotShowThenClearDataNotificationScheduled() = runBlocking<Unit> {
        setDripA1Variant()
        whenever(dripA1Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripA1VariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setDripA1Variant()
        whenever(dripA1Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenDripA1VariantAndDripA1NotificationAndClearDataNotificationCanShowThenDripA1NotificationScheduled() = runBlocking<Unit> {
        setDripA1Variant()
        whenever(dripA1Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripA1NotificationWorker::class.jvmName)
    }

    // Drip A2
    @Test
    fun whenDripA2VariantAndDripA2NotificationCanShowAndClearNotificationCannotShowThenDripA2NotificationIsScheduled() = runBlocking<Unit> {
        setDripA2Variant()
        whenever(dripA2Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripA2NotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripA2VariantAndClearNotificationCanShotAndDripA2NotificationCannotShowThenClearDataNotificationScheduled() = runBlocking<Unit> {
        setDripA2Variant()
        whenever(dripA2Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripA2VariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setDripA2Variant()
        whenever(dripA2Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenDripA2VariantAndDripA2NotificationAndClearDataNotificationCanShowThenDripA2NotificationScheduled() = runBlocking<Unit> {
        setDripA2Variant()
        whenever(dripA2Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripA2NotificationWorker::class.jvmName)
    }

    // Drip B1

    @Test
    fun whenDripB1VariantAndDripB1NotificationCanShowAndClearNotificationCannotShowThenDripB1NotificationIsScheduled() = runBlocking<Unit> {
        setDripB1Variant()
        whenever(dripB1Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripB1NotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripB1VariantAndClearNotificationCanShotAndDripB1NotificationCannotShowThenClearDataNotificationScheduled() = runBlocking<Unit> {
        setDripB1Variant()
        whenever(dripB1Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripB1VariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setDripB1Variant()
        whenever(dripB1Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenDripB1VariantAndDripB1NotificationAndClearDataNotificationCanShowThenDripB1NotificationScheduled() = runBlocking<Unit> {
        setDripB1Variant()
        whenever(dripB1Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripB1NotificationWorker::class.jvmName)
    }

    // Drip B2

    @Test
    fun whenDripB2VariantAndDripB2NotificationCanShowAndClearNotificationCannotShowThenDripB2NotificationIsScheduled() = runBlocking<Unit> {
        setDripB2Variant()
        whenever(dripB2Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripB2NotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripB2VariantAndClearNotificationCanShotAndDripB2NotificationCannotShowThenClearDataNotificationScheduled() = runBlocking<Unit> {
        setDripB2Variant()
        whenever(dripB2Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenDripB2VariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setDripB2Variant()
        whenever(dripB2Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenDripB2VariantAndDripB2NotificationAndClearDataNotificationCanShowThenDripB2NotificationScheduled() = runBlocking<Unit> {
        setDripB2Variant()
        whenever(dripB2Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(DripB2NotificationWorker::class.jvmName)
    }

    // Control
    @Test
    fun whenControlVariantAndPrivacyNotificationAndClearDataNotificationCanShowThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationCanShowAndClearDataCannotShowThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationCannotShowAndClearNotificationCanThenClearNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenControlVariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    // Null variant
    @Test
    fun whenNullVariantAndAllNotificationsCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setNotificationNullVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(dripA1Notification.canShow()).thenReturn(true)
        whenever(dripA2Notification.canShow()).thenReturn(true)
        whenever(dripB1Notification.canShow()).thenReturn(true)
        whenever(dripB2Notification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenNullVariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setNotificationNullVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(dripA1Notification.canShow()).thenReturn(false)
        whenever(dripA2Notification.canShow()).thenReturn(false)
        whenever(dripB1Notification.canShow()).thenReturn(false)
        whenever(dripB2Notification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenStageIsUseOurAppNotificationThenNotificationScheduled() = runBlocking {
        givenNoInactiveUserNotifications()
        givenStageIsUseOurAppNotification()

        testee.scheduleNextNotification()

        assertNotificationScheduled(UseOurAppNotificationWorker::class.jvmName, NotificationScheduler.USE_OUR_APP_WORK_REQUEST_TAG)
    }

    @Test
    fun whenStageIsUseOurAppNotificationAndNotificationScheduledThenStageCompleted() = runBlocking<Unit> {
        givenNoInactiveUserNotifications()
        givenStageIsUseOurAppNotification()

        testee.scheduleNextNotification()

        verify(mockUserStageStore).stageCompleted(AppStage.USE_OUR_APP_NOTIFICATION)
    }

    @Test
    fun whenStageIsUseOurAppNotificationThenNoNotificationScheduled() = runBlocking {
        givenStageIsEstablished()

        testee.scheduleNextNotification()

        assertNoNotificationScheduled(NotificationScheduler.USE_OUR_APP_WORK_REQUEST_TAG)
    }

    @Test
    fun whenStageIsNotUseOurAppNotificationThenNoNotificationScheduled() = runBlocking {
        givenStageIsEstablished()

        testee.scheduleNextNotification()

        assertNoNotificationScheduled(NotificationScheduler.USE_OUR_APP_WORK_REQUEST_TAG)
    }

    private fun setDripA1Variant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1DripA1Notification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setDripA2Variant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1DripA2Notification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setDripB1Variant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1DripB1Notification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setDripB2Variant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1DripB2Notification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setNotificationControlVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1PrivacyNotification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setNotificationNullVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification), filterBy = { true })
        )
    }

    private suspend fun givenNoInactiveUserNotifications() {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
    }

    private suspend fun givenStageIsUseOurAppNotification() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.USE_OUR_APP_NOTIFICATION)
    }

    private suspend fun givenStageIsEstablished() {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
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
