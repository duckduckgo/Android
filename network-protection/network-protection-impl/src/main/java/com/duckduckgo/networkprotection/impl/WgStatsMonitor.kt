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

package com.duckduckgo.networkprotection.impl

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
@SingleInstanceIn(VpnScope::class)
class WgStatsMonitor @Inject constructor(
    private val wgProtocol: WgProtocol,
    private val dispactherProvider: DispatcherProvider,
) : VpnServiceCallbacks {
    private var logConfigJob: Job? = null
    private var isRunning = false
    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        logcat { "onVpnStarted called" }
        isRunning = true
        logConfigJob = coroutineScope.launch(dispactherProvider.io()) {
            while (isRunning) {
                logcat { "NetP config: ${wgProtocol.getStatistics()}" }
                delay(2000)
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        logcat { "onVpnStopped called" }
        isRunning = false
        logConfigJob?.cancel()
        logConfigJob = null
    }
}
