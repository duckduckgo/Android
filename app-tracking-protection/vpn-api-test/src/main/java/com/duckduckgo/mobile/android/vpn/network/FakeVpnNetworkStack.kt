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

package com.duckduckgo.mobile.android.vpn.network

import android.os.ParcelFileDescriptor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason

class FakeVpnNetworkStack(override val name: String) : VpnNetworkStack {
    override fun onCreateVpn(): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun onPrepareVpn(): Result<VpnNetworkStack.VpnTunnelConfig> {
        return Result.success(
            VpnNetworkStack.VpnTunnelConfig(
                mtu = 1500,
                addresses = emptyMap(),
                dns = emptySet(),
                searchDomains = null,
                customDns = emptySet(),
                routes = emptyMap(),
                appExclusionList = emptySet(),
            ),
        )
    }

    override fun onStartVpn(tunfd: ParcelFileDescriptor): Result<Unit> {
        return Result.success(Unit)
    }

    override fun onStopVpn(reason: VpnStopReason): Result<Unit> {
        return Result.success(Unit)
    }

    override fun onDestroyVpn(): Result<Unit> {
        return Result.success(Unit)
    }
}
