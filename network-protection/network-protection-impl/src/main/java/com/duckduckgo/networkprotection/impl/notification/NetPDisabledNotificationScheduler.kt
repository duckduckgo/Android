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

package com.duckduckgo.networkprotection.impl.notification

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
class NetPDisabledNotificationScheduler @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
) : VpnServiceCallbacks {

    private var isNetPEnabled: AtomicReference<Boolean> = AtomicReference(false)

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        runBlocking {
            isNetPEnabled.set(networkProtectionState.isEnabled())
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        coroutineScope.launch {
            when (vpnStopReason) {
                VpnStopReason.RESTART -> {} // no-op
                VpnStopReason.SELF_STOP -> onVPNManuallyStopped()
                VpnStopReason.REVOKED -> onVPNRevoked()
                else -> {}
            }
        }
    }

    private suspend fun shouldShowImmediateNotification(): Boolean {
        // When VPN is stopped and if AppTP has been enabled AND user has been onboarded, then we show the disabled notif
        return isNetPEnabled.get() && networkProtectionState.isOnboarded()
    }

    private suspend fun onVPNManuallyStopped() {
        if (shouldShowImmediateNotification()) {
            logcat { "VPN Manually stopped, showing disabled notification for NetP" }
            // TODO: Show NetP disabled notifications
            isNetPEnabled.set(false)
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        runBlocking {
            val reconfiguredNetPState = networkProtectionState.isEnabled()
            if (isNetPEnabled.getAndSet(reconfiguredNetPState) != reconfiguredNetPState) {
                if (!reconfiguredNetPState) {
                    logcat { "VPN has been reconfigured, showing disabled notification for NetP" }
                    // TODO: Show NetP disabled notifications
                }
            }
        }
    }
    private suspend fun onVPNRevoked() {
        if (shouldShowImmediateNotification()) {
            logcat { "VPN has been revoked, showing revoked notification for NetP" }
            isNetPEnabled.set(false)
        }
    }
}
