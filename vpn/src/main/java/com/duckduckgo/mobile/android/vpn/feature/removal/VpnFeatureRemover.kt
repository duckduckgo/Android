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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.ui.notification.AndroidDeviceShieldAlertNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationScheduler.Companion.VPN_DAILY_NOTIFICATION_ID
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationScheduler.Companion.VPN_WEEKLY_NOTIFICATION_ID
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface VpnFeatureRemover {
    fun manuallyRemoveFeature()
    fun scheduledRemoveFeature()
}

@ContributesBinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class DefaultVpnFeatureRemover @Inject constructor(
    private val deviceShieldOnboarding: DeviceShieldOnboardingStore,
    private val notificationManager: NotificationManagerCompat,
    private val vpnDatabase: VpnDatabase,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) : VpnFeatureRemover {

    // Disabling reminder notifications
    // Disable daily / weekly notifications
    // Remove CTA in New Tab
    // Remove all app trackers blocked
    // Remove app shortcut
    // If manual removal
    // Force users to complete AppTP Onboarding

    override fun manuallyRemoveFeature() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            disableNotifications()
            removeNotificationChannels()
            deleteAllVpnTrackers()
            disableFeature()
        }
    }

    override fun scheduledRemoveFeature() {
        manuallyRemoveFeature()
        resetAppTPOnboarding()
    }

    private fun disableNotifications() {
        notificationManager.cancel(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID)
        notificationManager.cancel(VPN_DAILY_NOTIFICATION_ID)
        notificationManager.cancel(VPN_WEEKLY_NOTIFICATION_ID)
    }

    private fun removeNotificationChannels() {
        notificationManager.deleteNotificationChannel(AndroidDeviceShieldAlertNotificationBuilder.VPN_ALERTS_CHANNEL_ID)
    }

    private fun resetAppTPOnboarding() {
        deviceShieldOnboarding.onboardingDidNotShow()
    }

    private fun deleteAllVpnTrackers() {
        vpnDatabase.vpnTrackerDao().deleteAllTrackers()
    }

    private fun disableFeature() {
        deviceShieldOnboarding.onFeatureDisabled()
    }
}
