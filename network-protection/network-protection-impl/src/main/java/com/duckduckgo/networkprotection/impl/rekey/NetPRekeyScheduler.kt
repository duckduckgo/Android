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

package com.duckduckgo.networkprotection.impl.rekey

import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.*
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class NetPRekeyScheduler @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val dispatcherProvider: DispatcherProvider,
    private val netPRekeyer: NetPRekeyer,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            while (isActive && vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) {
                logcat { "Start periodic re-keying attempts" }
                delay(TimeUnit.HOURS.toMillis(1)) // try to re-key every 1h

                netPRekeyer.doRekey()
            }
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        // When we reconfigure the VPN we also need to make sure we schedule the re-key process
        onVpnStarted(coroutineScope)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        job.cancel()
    }
}
