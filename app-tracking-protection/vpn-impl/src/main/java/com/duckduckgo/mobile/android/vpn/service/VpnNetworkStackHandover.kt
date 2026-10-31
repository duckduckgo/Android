/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

/**
 * Moves a running VPN from [current] to [next] during an in-place restart.
 *
 * The network stacks are process-wide singletons, so when the provider hands back the instance that is
 * already running there is nothing to create or destroy: the stack is only stopped with [VpnStopReason.RESTART]
 * and started again on the new tunnel. Each stack decides what a restart stop means for its own native resources.
 */
internal class VpnNetworkStackHandover(
    val current: VpnNetworkStack,
    val next: VpnNetworkStack,
) {
    val isSameStack: Boolean
        get() = current === next

    /** Stops [current] for a restart and tears it down only when it is being replaced. */
    fun switch() {
        current.onStopVpn(VpnStopReason.RESTART).onFailure {
            logcat(ERROR) { "VPN log: error stopping ${current.name} for restart: ${it.asLog()}" }
        }
        if (!isSameStack) {
            current.onDestroyVpn()
        }
    }

    /** Releases [next] when the restart fails before it is started and [next] is not the running stack. */
    fun abort() {
        if (!isSameStack) {
            next.onDestroyVpn()
        }
    }
}
