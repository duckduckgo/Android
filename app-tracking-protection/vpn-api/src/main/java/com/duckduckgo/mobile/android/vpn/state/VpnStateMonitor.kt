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

package com.duckduckgo.mobile.android.vpn.state
import com.duckduckgo.mobile.android.vpn.VpnFeature
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.AlwaysOnState.Companion.DEFAULT
import kotlinx.coroutines.flow.Flow

interface VpnStateMonitor {

    /**
     * Returns a flow of VPN changes for the given [vpnFeature]
     * It follows the following truth table:
     * * when the VPN is disabled the flow will emit a [VpnState.DISABLED]
     * * else it will return the state of the feature
     */
    fun getStateFlow(vpnFeature: VpnFeature): Flow<VpnState>
    data class VpnState(
        val state: VpnRunningState,
        val stopReason: VpnStopReason? = null,
        val alwaysOnState: AlwaysOnState = DEFAULT
    )

    data class AlwaysOnState(val enabled: Boolean, val lockedDown: Boolean) {
        companion object {
            val DEFAULT = AlwaysOnState(enabled = false, lockedDown = false)
            val ALWAYS_ON_ENABLED = AlwaysOnState(enabled = true, lockedDown = false)
            val ALWAYS_ON_LOCKED_DOWN = AlwaysOnState(enabled = true, lockedDown = true)
        }

        fun isAlwaysOnLockedDown(): Boolean = enabled && lockedDown
    }

    enum class VpnRunningState {
        ENABLED,
        DISABLED,
        INVALID
    }

    enum class VpnStopReason {
        SELF_STOP,
        ERROR,
        REVOKED,
        UNKNOWN
    }
}
