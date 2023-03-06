/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationWorker
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiverManager
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DeviceShieldReminderNotificationSchedulerTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var testee: DeviceShieldReminderNotificationScheduler
    private val notificationBuilder: DeviceShieldAlertNotificationBuilder = mock()
    private val vpnFeatureRemover: VpnFeatureRemover = mock()
    private val mockVpnReminderReceiverManager: VpnReminderReceiverManager = mock()
    private val vpnFeaturesRegistry: VpnFeaturesRegistry = mock()

    @Before
    fun before() {
        initializeWorkManager()
        notificationManager = NotificationManagerCompat.from(context)
        testee =
            DeviceShieldReminderNotificationScheduler(
                context,
                workManager,
                notificationManager,
                notificationBuilder,
                vpnFeatureRemover,
                coroutinesTestRule.testDispatcherProvider,
                TestScope(),
                vpnFeaturesRegistry,
            )
        configureMockNotifications()
    }

    @After
    fun after() {
        workManager.cancelAllWork()
    }

    // https://developer.android.com/topic/libraries/architecture/workmanager/how-to/integration-testing
    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(testWorkerFactory())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun whenVPNStartsThenUndesiredReminderIsEnqueued() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)).thenReturn(true)
        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)

        testee.onVpnStarted(TestScope())

        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
    }

    @Test
    fun whenVPNStartsThenDailyReminderIsNotEnqueued() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)).thenReturn(true)
        testee.onVpnStarted(TestScope())

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    @Test
    fun whenVPNStartsAndDailyReminderWasEnqueuedThenDailyReminderIsNotEnqueued() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)).thenReturn(true)
        enqueueDailyReminderNotificationWorker()
        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)

        testee.onVpnStarted(TestScope())

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    @Test
    fun whenVPNManuallyStopsThenDailyReminderIsEnqueued() = runTest {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn(AppTpVpnFeature.APPTP_VPN.featureName to false)
        whenever(vpnFeatureRemover.isFeatureRemoved()).thenReturn(false)
        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)

        testee.onVpnStopped(TestScope(), SELF_STOP)

        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    @Test
    fun whenVPNStoppedBecauseFeatureWasRemovedThenNothingIsEnqueued() = runTest {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn(AppTpVpnFeature.APPTP_VPN.featureName to false)
        whenever(vpnFeatureRemover.isFeatureRemoved()).thenReturn(true)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)

        testee.onVpnStopped(TestScope(), SELF_STOP)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    @Test
    fun whenVPNManuallyStopsAndDailyReminderWasEnqueuedThenDailyReminderIsStillEnqueued() = runTest {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn(AppTpVpnFeature.APPTP_VPN.featureName to false)
        whenever(vpnFeatureRemover.isFeatureRemoved()).thenReturn(false)
        enqueueDailyReminderNotificationWorker()
        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)

        testee.onVpnStopped(TestScope(), SELF_STOP)

        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    @Test
    fun whenVPNManuallyStopsThenUndesiredReminderIsNotScheduled() = runTest {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn(AppTpVpnFeature.APPTP_VPN.featureName to false)
        whenever(vpnFeatureRemover.isFeatureRemoved()).thenReturn(false)
        testee.onVpnStopped(TestScope(), SELF_STOP)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
    }

    @Test
    fun whenVPNManuallyStopsAndUndesiredReminderWasScheduledThenUndesiredReminderIsNoLongerScheduled() = runTest {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn(AppTpVpnFeature.APPTP_VPN.featureName to false)
        whenever(vpnFeatureRemover.isFeatureRemoved()).thenReturn(false)
        enqueueUndesiredReminderNotificationWorker()
        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)

        testee.onVpnStopped(TestScope(), SELF_STOP)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
    }

    @Test
    fun whenVPNIsKilledDueToOtherFeatureDisabledThenUndesiredReminderIsNotEnqueued() {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn("something else" to false)
        enqueueUndesiredReminderNotificationWorker()

        testee.onVpnStopped(TestScope(), REVOKED)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    @Test
    fun whenVPNIsKilledDueToAppTPDisabledThenUndesiredReminderIsNotEnqueued() {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn(AppTpVpnFeature.APPTP_VPN.featureName to false)
        testee.onVpnStopped(TestScope(), REVOKED)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
    }

    @Test
    fun whenVPNIsKilledDueToAppTPDisabledAndReminderWasScheduledThenUndesiredReminderIsNoLongerScheduled() {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn(AppTpVpnFeature.APPTP_VPN.featureName to false)
        enqueueUndesiredReminderNotificationWorker()
        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)

        testee.onVpnStopped(TestScope(), REVOKED)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
    }

    @Test
    fun whenLastRegisteredChangeIsNotAppTPDisabledThenUndesiredReminderAndDailyReminderAreNotEnqueued() = runTest {
        whenever(vpnFeaturesRegistry.getLastRegistryChange()).thenReturn("something else" to false)
        whenever(vpnFeatureRemover.isFeatureRemoved()).thenReturn(false)

        enqueueUndesiredReminderNotificationWorker()
        assertWorkersAreEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
        testee.onVpnStopped(TestScope(), SELF_STOP)

        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
        assertWorkersAreNotEnqueued(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)

    }

    private fun configureMockNotifications() {
        whenever(notificationBuilder.buildReminderNotification(any(), any())).thenReturn(
            NotificationCompat.Builder(context, "")
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .build(),
        )
        whenever(notificationBuilder.buildRevokedNotification(any())).thenReturn(
            NotificationCompat.Builder(context, "")
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .build(),
        )
    }

    private fun enqueueDailyReminderNotificationWorker() {
        val requestBuilder = OneTimeWorkRequestBuilder<VpnReminderNotificationWorker>()
        val request = requestBuilder
            .addTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG, ExistingWorkPolicy.KEEP, request)
    }

    private fun enqueueUndesiredReminderNotificationWorker() {
        val requestBuilder = PeriodicWorkRequestBuilder<VpnReminderNotificationWorker>(5, TimeUnit.HOURS)
        val request = requestBuilder
            .addTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
            .setInitialDelay(5, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun assertWorkersAreNotEnqueued(tag: String) {
        val scheduledWorkers = getScheduledWorkers(tag)
        scheduledWorkers.forEach { workInfo ->
            Log.d("WorkManager", "workInfo: $workInfo")
            Assert.assertTrue(workInfo.state != WorkInfo.State.ENQUEUED)
        }
    }

    private fun assertWorkersAreEnqueued(tag: String) {
        val scheduledWorkers = getScheduledWorkers(tag)
        Assert.assertTrue(scheduledWorkers.isNotEmpty())
        scheduledWorkers.forEach { workInfo ->
            Log.d("WorkManager", "workInfo: $workInfo")
            Assert.assertTrue(workInfo.state == WorkInfo.State.ENQUEUED)
        }
    }

    private fun testWorkerFactory(): WorkerFactory {
        return object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? {
                return VpnReminderNotificationWorker(appContext, workerParameters).also {
                    it.vpnReminderReceiverManager = mockVpnReminderReceiverManager
                }
            }
        }
    }

    private fun getScheduledWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
    }
}
