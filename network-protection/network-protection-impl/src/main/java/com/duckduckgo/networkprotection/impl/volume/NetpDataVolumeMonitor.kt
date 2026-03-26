/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.volume

import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.WgProtocol
import com.duckduckgo.networkprotection.impl.volume.NetpDataVolumeStore.DataVolume
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@ContributesMultibinding(VpnScope::class)
class NetpDataVolumeMonitor @Inject constructor(
    private val wgProtocol: WgProtocol,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val netpDataVolumeStore: NetpDataVolumeStore,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch {
            startDataVolumeMonitoring()
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch {
            startDataVolumeMonitoring()
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        job.cancel()
        netpDataVolumeStore.dataVolume = DataVolume()
    }

    private suspend fun startDataVolumeMonitoring() = withContext(dispatcherProvider.io()) {
        if (networkProtectionState.isEnabled()) {
            while (isActive && networkProtectionState.isEnabled()) {
                wgProtocol.getStatistics().also {
                    netpDataVolumeStore.dataVolume = DataVolume(
                        receivedBytes = it.receivedBytes,
                        transmittedBytes = it.transmittedBytes,
                    )
                }
                delay(1.seconds.inWholeMilliseconds)
            }
        } else {
            netpDataVolumeStore.dataVolume = DataVolume()
        }
    }
}
