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

package com.duckduckgo.mobile.android.vpn.service.state

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.DISABLED
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.ENABLED
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.ERROR
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.REVOKED
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.SELF_STOP
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealVpnStateMonitor @Inject constructor(val database: VpnDatabase) : VpnStateMonitor {

    override fun getStateFlow(): Flow<VpnState> {
        return database.vpnServiceStateDao().getStateStats().map {
            mapState(it)
        }
    }

    override fun getState(): VpnState {
        val lastState = database.vpnServiceStateDao().getLastStateStats()
        return mapState(lastState)
    }

    private fun mapState(lastState: VpnServiceStateStats?): VpnState {
        val stoppingReason = when (lastState?.stopReason) {
            SELF_STOP -> VpnStopReason.SELF_STOP
            REVOKED -> VpnStopReason.REVOKED
            ERROR -> VpnStopReason.ERROR
            else -> VpnStopReason.UNKNOWN
        }
        val runningState = when (lastState?.state) {
            ENABLED -> VpnRunningState.ENABLED
            DISABLED -> VpnRunningState.DISABLED
            else -> VpnRunningState.INVALID
        }
        return VpnState(runningState, stoppingReason)
    }
}
