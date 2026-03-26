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

package com.duckduckgo.mobile.android.vpn.ui.alwayson

import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerActivityWithEmptyParams
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldAlertNotificationBuilder
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
@SingleInstanceIn(VpnScope::class)
class AlwaysOnLockDownDetector @Inject constructor(
    private val vpnServiceStateStatsDao: VpnServiceStateStatsDao,
    private val dispatcherProvider: DispatcherProvider,
    private val deviceShieldAlertNotificationBuilder: DeviceShieldAlertNotificationBuilder,
    private val context: Context,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val networkProtectionState: NetworkProtectionState,
    private val appTrackingProtection: AppTrackingProtection,
    private val globalActivityStarter: GlobalActivityStarter,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()
    private val resources = context.resources
    private val notificationId = Random.nextInt()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            vpnServiceStateStatsDao.getStateStats()
                .distinctUntilChanged()
                .mapNotNull { it?.alwaysOnState?.alwaysOnLockedDown }
                .cancellable()
                .collect { alwaysOnLockedDown ->
                    if (alwaysOnLockedDown) {
                        showNotification()
                    } else {
                        removeNotification()
                    }
                }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        job.cancel()
        removeNotification()
    }

    private suspend fun showNotification() {
        val text = SpannableStringBuilder(getNotificationText())
        val intent = getNotificationIntent()

        val notification = DeviceShieldNotificationFactory.DeviceShieldNotification(text = text)
        deviceShieldAlertNotificationBuilder.buildAlwaysOnLockdownNotification(
            context,
            notification,
            intent,
        ).also {
            notificationManagerCompat.checkPermissionAndNotify(context, notificationId, it)
        }
    }

    private suspend fun getNotificationText(): String {
        val isAppTPEnabled = appTrackingProtection.isEnabled()
        val isNetPEnabled = networkProtectionState.isEnabled()
        return when {
            isAppTPEnabled && isNetPEnabled -> R.string.vpn_LockdownNotificationTextWithNetPAndAppTPEnabled
            isNetPEnabled -> R.string.vpn_LockdownNotificationTextWithNetPOnlyEnabled
            else -> R.string.atp_AlwaysOnLockDownNotificationTitle
        }.run {
            resources.getString(this)
        }
    }

    private suspend fun getNotificationIntent(): Intent {
        val isNetPEnabled = networkProtectionState.isEnabled()
        return when {
            isNetPEnabled -> NetworkProtectionManagementScreenNoParams
            else -> AppTrackerActivityWithEmptyParams
        }.run {
            globalActivityStarter.startIntent(context, this)!!
        }
    }

    private fun removeNotification() {
        notificationManagerCompat.cancel(notificationId)
    }
}
