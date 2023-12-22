/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.subscription.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.notification.NetPDisabledNotificationScheduler
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(VpnScope::class)
class NetpAccessRevokedNotificationScheduler @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {
    override fun onVpnStarted(coroutineScope: CoroutineScope) {}

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
    }

    override fun onVpnStartFailed(coroutineScope: CoroutineScope) {
        if (networkProtectionRepository.vpnAccessRevoked) {
            notificationManager.notify(
                NetPDisabledNotificationScheduler.NETP_REMINDER_NOTIFICATION_ID,
                buildVpnAccessRevokedNotification(context),
            )
            coroutineScope.launch(dispatcherProvider.io()) {
                // This is to clear the registered features and remove NetP
                networkProtectionState.stop()
            }
        }
    }
}
