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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class NetPReminderNotificationScheduler @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
) : VpnServiceCallbacks {

    private var isNetPEnabled = false

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        runBlocking {
            isNetPEnabled = networkProtectionState.isEnabled()
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        when (vpnStopReason) {
            VpnStopReason.RESTART -> {} // no-op
            VpnStopReason.SELF_STOP -> onVPNManuallyStopped()
            VpnStopReason.REVOKED -> {}
            else -> {}
        }
    }

    private fun onVPNManuallyStopped() {
        if (isNetPEnabled) {
            logcat { "TESTING: SHOW NETP DISABLED NOTIF" }
            isNetPEnabled = false
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        super.onVpnReconfigured(coroutineScope)
        runBlocking {
            val reconfiguredNetPState = networkProtectionState.isEnabled()
            if (isNetPEnabled != reconfiguredNetPState) {
                if (!reconfiguredNetPState) {
                    logcat { "TESTING: SHOW NETP DISABLED NOTIF" }
                }
            }
            isNetPEnabled = reconfiguredNetPState
        }
    }
}
