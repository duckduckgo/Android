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

package com.duckduckgo.networkprotection.impl.reconnect

import android.content.Context
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.connectivity.VpnConnectivityLossListenerPlugin
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.alerts.reconnect.NetPReconnectNotifications
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.NotReconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.Reconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.ReconnectingFailed
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnConnectivityLossListenerPlugin::class,
)
@SingleInstanceIn(VpnScope::class)
class NetpVpnConnectivityLossListenerPlugin @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val repository: NetworkProtectionRepository,
    private val reconnectNotifications: NetPReconnectNotifications,
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : VpnConnectivityLossListenerPlugin {

    override fun onVpnConnectivityLoss(coroutineScope: CoroutineScope) {
        if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
            coroutineScope.launch(dispatcherProvider.io()) {
                handleConnectivityLoss(coroutineScope)
            }
        }
    }

    override fun onVpnConnected(coroutineScope: CoroutineScope) {
        if (vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
            coroutineScope.launch(dispatcherProvider.io()) {
                logcat { "onVpnConnected called." }
                if (repository.reconnectStatus == Reconnecting) {
                    repository.reconnectStatus = NotReconnecting
                    successfullyRecovered()
                } else if (repository.reconnectStatus == ReconnectingFailed) {
                    repository.reconnectStatus = NotReconnecting
                    logcat { "Resetting reconnectStatus from ReconnectingFailed to NotReconnecting" }
                }
            }
        }
    }

    private fun handleConnectivityLoss(coroutineScope: CoroutineScope) {
        logcat { " Vpn connectivity loss detected. attempted ${repository.reconnectAttemptCount} times" }
        repository.reconnectStatus = Reconnecting

        if (repository.reconnectAttemptCount < MAX_RECOVERY_ATTEMPTS) {
            repository.reconnectAttemptCount++
            initiateRecovery(coroutineScope)
        } else {
            giveUpRecovering()
        }
    }

    private fun successfullyRecovered() {
        resetReconnectValues()

        /**
         * TODO:
         * - Hide banner and kill all related notification
         */
        reconnectNotifications.clearNotifications()
        reconnectNotifications.launchReconnectedNotification(context)
        logcat { "Successfully recovered from VPN connectivity loss." }
    }

    private fun initiateRecovery(coroutineScope: CoroutineScope) {
        logcat { "Attempting to recover from vpn connectivity loss." }
        /**
         * TODO:
         * - Show notif if not yet shown
         * - restart vpn
         */
        reconnectNotifications.clearNotifications()
        reconnectNotifications.launchReconnectingNotification(context)
        coroutineScope.launch {
            vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
        }
    }

    private fun giveUpRecovering() {
        logcat { "Failed to recover from vpn connectivity loss after $MAX_RECOVERY_ATTEMPTS attempts" }
        repository.reconnectStatus = ReconnectingFailed
        resetReconnectValues()
        /**
         * TODO:
         * - Replace banner and notification
         * - stop VPN
         */
        reconnectNotifications.clearNotifications()
        reconnectNotifications.launchReconnectionFailedNotification(context)
        vpnFeaturesRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
    }

    private fun resetReconnectValues() {
        repository.reconnectAttemptCount = 0
    }

    companion object {
        private const val MAX_RECOVERY_ATTEMPTS = 1
    }
}
