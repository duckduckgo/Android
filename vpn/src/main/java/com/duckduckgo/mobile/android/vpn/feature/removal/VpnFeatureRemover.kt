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

package com.duckduckgo.mobile.android.vpn.feature.removal

import androidx.core.app.NotificationManagerCompat
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.dao.VpnFeatureRemoverState
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationWorker
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.ui.notification.AndroidDeviceShieldAlertNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationScheduler.Companion.VPN_DAILY_NOTIFICATION_ID
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationScheduler.Companion.VPN_WEEKLY_NOTIFICATION_ID
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

interface VpnFeatureRemover {
    fun manuallyRemoveFeature()
    fun scheduledRemoveFeature()
    suspend fun isFeatureRemoved(): Boolean
}

@ContributesBinding(scope = AppScope::class, boundType = VpnFeatureRemover::class)
@ContributesMultibinding(scope = AppScope::class, boundType = WorkerInjectorPlugin::class)
class DefaultVpnFeatureRemover @Inject constructor(
    private val vpnStore: VpnStore,
    private val notificationManager: NotificationManagerCompat,
    private val vpnDatabase: VpnDatabase,
    // we use the Provider to avoid a cycle dependency
    private val workManagerProvider: Provider<WorkManager>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) : VpnFeatureRemover, WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is VpnFeatureRemoverWorker) {
            worker.vpnFeatureRemover = this
            return true
        }

        return false
    }

    override fun manuallyRemoveFeature() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            removeVpnFeature()
            disableNotifications()
            disableNotificationReminders()
            removeNotificationChannels()
            deleteAllVpnTrackers()
        }
    }

    private fun disableNotificationReminders() {
        val workManager = workManagerProvider.get()
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
    }

    override fun scheduledRemoveFeature() {
        manuallyRemoveFeature()
        resetAppTPOnboarding()
    }

    override suspend fun isFeatureRemoved(): Boolean {
        return vpnDatabase.vpnFeatureRemoverDao().getState()?.isFeatureRemoved ?: false
    }

    private fun disableNotifications() {
        notificationManager.cancel(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID)
        notificationManager.cancel(VPN_DAILY_NOTIFICATION_ID)
        notificationManager.cancel(VPN_WEEKLY_NOTIFICATION_ID)
    }

    private fun removeNotificationChannels() {
        notificationManager.deleteNotificationChannel(AndroidDeviceShieldAlertNotificationBuilder.VPN_ALERTS_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(AndroidDeviceShieldAlertNotificationBuilder.VPN_STATUS_CHANNEL_ID)
    }

    private fun resetAppTPOnboarding() {
        vpnStore.onboardingDidNotShow()
    }

    private fun deleteAllVpnTrackers() {
        vpnDatabase.vpnTrackerDao().deleteAllTrackers()
    }

    private suspend fun removeVpnFeature() {
        vpnDatabase.vpnFeatureRemoverDao().insert(VpnFeatureRemoverState(isFeatureRemoved = true))
    }
}
