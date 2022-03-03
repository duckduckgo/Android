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

package com.duckduckgo.mobile.android.vpn.stats

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.di.VpnDispatcherProvider
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.ERROR
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.REVOKED
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.SELF_STOP
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.UNKNOWN
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class
)
@SingleInstanceIn(VpnScope::class)
class VpnServiceStateLogger @Inject constructor(
    private val dispatcherProvider: VpnDispatcherProvider,
    private val vpnDatabase: VpnDatabase
) : VpnServiceCallbacks {

    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcher) {
            Timber.d("VpnServiceStateLogge, new state ENABLED")
            vpnDatabase.vpnServiceStateDao().insert(VpnServiceStateStats(state = VpnServiceState.ENABLED))
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        coroutineScope.launch(dispatcher) {
            Timber.d("VpnServiceStateLogge, new state DISABLED, reason $vpnStopReason")
            vpnDatabase.vpnServiceStateDao().insert(VpnServiceStateStats(state = VpnServiceState.DISABLED, stopReason = mapStopReason(vpnStopReason)))
        }
    }

    private fun mapStopReason(vpnStopReason: VpnStopReason): VpnStoppingReason {
        return when (vpnStopReason) {
            VpnStopReason.SELF_STOP -> SELF_STOP
            VpnStopReason.REVOKED -> REVOKED
            VpnStopReason.ERROR -> ERROR
            VpnStopReason.UNKNOWN -> UNKNOWN
        }
    }
}
