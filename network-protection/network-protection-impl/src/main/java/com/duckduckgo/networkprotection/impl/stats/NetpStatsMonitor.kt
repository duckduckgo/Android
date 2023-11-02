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

package com.duckduckgo.networkprotection.impl.stats

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.WgProtocol
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.DataVolume
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class NetpStatsMonitor @Inject constructor(
    private val wgProtocol: WgProtocol,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val netpRepository: NetworkProtectionRepository,
) : VpnServiceCallbacks {
    private var job: ConflatedJob = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += attemptToStartMonitor(coroutineScope)
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        job += attemptToStartMonitor(coroutineScope)
    }

    private fun attemptToStartMonitor(coroutineScope: CoroutineScope): Job {
        return coroutineScope.launch(dispatcherProvider.io()) {
            if (networkProtectionState.isRunning()) {
                while (true) {
                    delay(5_000)
                    wgProtocol.getStatistics().also {
                        netpRepository.dataVolume = DataVolume(
                            transmitted = it.transmittedBytes,
                            received = it.receivedBytes,
                        )
                    }
                }
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        job.cancel()
    }
}
