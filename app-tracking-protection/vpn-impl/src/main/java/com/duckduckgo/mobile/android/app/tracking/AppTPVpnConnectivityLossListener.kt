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

package com.duckduckgo.mobile.android.app.tracking

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.feature.AppTpRemoteFeatures
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.connectivity.VpnConnectivityLossListenerPlugin
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnConnectivityLossListenerPlugin::class,
)
@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
@SingleInstanceIn(VpnScope::class)
class AppTPVpnConnectivityLossListener @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
    private val appTrackingProtection: AppTrackingProtection,
    private val appTpRemoteFeatures: AppTpRemoteFeatures,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
) : VpnConnectivityLossListenerPlugin, VpnServiceCallbacks {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    private var connectivityLostEvents = 0
    private var reconnectAttempts: Int
        get() = preferences.getInt(RECONNECT_ATTEMPTS, 0)
        set(value) = preferences.edit(commit = true) { putInt(RECONNECT_ATTEMPTS, value) }

    override fun onVpnConnected(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isActive()) {
                connectivityLostEvents = 0
                reconnectAttempts = 0
            }
        }
    }

    override fun onVpnConnectivityLoss(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isActive() && connectivityLostEvents++ > 1) {
                connectivityLostEvents = 0
                if (reconnectAttempts++ > 2) {
                    logcat(WARN) { "AppTP detected connectivity loss, again, give up..." }
                    reconnectAttempts = 0
                    appTrackingProtection.stop()
                } else {
                    logcat(WARN) { "AppTP detected connectivity loss, re-configuring..." }
                    appTrackingProtection.restart()
                }
            }
        }
    }

    private suspend fun isActive(): Boolean = withContext(dispatcherProvider.io()) {
        fun isFeatureEnabled(): Boolean {
            return appTpRemoteFeatures.restartOnConnectivityLoss().isEnabled()
        }

        return@withContext isFeatureEnabled() && appTrackingProtection.isEnabled() && !networkProtectionState.isEnabled()
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        connectivityLostEvents = 0
        coroutineScope.launch(dispatcherProvider.io()) {
            reconnectAttempts = 0
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        connectivityLostEvents = 0
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        connectivityLostEvents = 0
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.mobile.android.app.tracking.conn.loss"
        private const val RECONNECT_ATTEMPTS = "RECONNECT_ATTEMPTS"
    }
}
