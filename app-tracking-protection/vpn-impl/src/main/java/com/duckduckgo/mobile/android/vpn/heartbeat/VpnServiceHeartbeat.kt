/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.heartbeat

import android.content.Context
import android.os.Process
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.ERROR
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.RESTART
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.UNKNOWN
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.*
import logcat.logcat

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
@SingleInstanceIn(VpnScope::class)
class VpnServiceHeartbeat @Inject constructor(
    private val vpnDatabase: VpnDatabase,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {

    private var job = ConflatedJob()

    private suspend fun storeHeartbeat(type: String) = withContext(dispatcherProvider.io()) {
        logcat { "(${Process.myPid()}) - Sending heartbeat $type" }
        vpnDatabase.vpnHeartBeatDao().insertType(type)
    }

    companion object {
        private const val HEART_BEAT_PERIOD_MINUTES: Long = 7
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        logcat { "onVpnStarted called" }
        job += coroutineScope.launch(dispatcherProvider.io()) {
            while (true) {
                storeHeartbeat(VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE)
                delay(TimeUnit.MINUTES.toMillis(HEART_BEAT_PERIOD_MINUTES))
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        logcat { "onVpnStopped called" }
        when (vpnStopReason) {
            ERROR -> logcat { "HB monitor: sudden vpn stopped $vpnStopReason" }
            is SELF_STOP, REVOKED, RESTART, UNKNOWN -> {
                logcat { "HB monitor: self stopped or revoked or restart: $vpnStopReason" }
                // we absolutely want this to finish before VPN is stopped to avoid race conditions reading out the state
                runBlocking { storeHeartbeat(VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_STOPPED) }
            }
        }
        job.cancel()
    }
}
