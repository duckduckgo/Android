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
import androidx.work.WorkManager
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationWorker
import com.duckduckgo.mobile.android.vpn.ui.notification.AndroidDeviceShieldAlertNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn

interface VpnFeatureRemover {
    fun manuallyRemoveFeature()
    fun scheduledRemoveFeature()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultVpnFeatureRemover(
    private val deviceShieldOnboarding: DeviceShieldOnboardingStore,
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat,
    private val vpnTrackerDao: VpnTrackerDao
) : VpnFeatureRemover {

    // Disabling reminder notifications
    // Disable daily / weekly notifications
    // Remove CTA in New Tab
    // Remove all app trackers blocked
    // Remove app shortcut
    // If manual removal
    // Force users to complete AppTP Onboarding

    override fun manuallyRemoveFeature() {
        disableNotifications()
        removeNotificationChannels()
        deleteAllVpnTrackers()
        disableFeature()
    }

    override fun scheduledRemoveFeature() {
        manuallyRemoveFeature()
        resetAppTPOnboarding()
    }

    private fun disableNotifications() {
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_UNDESIRED_TAG)
        workManager.cancelAllWorkByTag(VpnReminderNotificationWorker.WORKER_VPN_REMINDER_DAILY_TAG)
        notificationManager.cancel(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID)
    }

    private fun removeNotificationChannels() {
        notificationManager.deleteNotificationChannel(AndroidDeviceShieldAlertNotificationBuilder.VPN_ALERTS_CHANNEL_ID)
        notificationManager.deleteNotificationChannel(AndroidDeviceShieldAlertNotificationBuilder.VPN_STATUS_CHANNEL_ID)
    }

    private fun resetAppTPOnboarding() {
        deviceShieldOnboarding.onboardingDidNotShow()
    }

    private fun deleteAllVpnTrackers() {
        vpnTrackerDao.deleteAllTrackers()
    }

    private fun disableFeature() {
        deviceShieldOnboarding.onFeatureDisabled()
    }
}
