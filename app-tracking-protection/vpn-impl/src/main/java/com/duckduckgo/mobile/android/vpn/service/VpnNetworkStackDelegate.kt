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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import kotlin.reflect.KProperty

class VpnNetworkStackDelegate constructor(
    networkStack: VpnNetworkStack? = null,
    private val provider: () -> VpnNetworkStack,
) {

    private var vpnNetworkStack: VpnNetworkStack? = networkStack

    operator fun getValue(thisRef: TrackerBlockingVpnService, property: KProperty<*>): VpnNetworkStack {
        if (vpnNetworkStack == null) {
            vpnNetworkStack = provider.invoke()
        }

        return vpnNetworkStack!!
    }

    operator fun setValue(thisRef: TrackerBlockingVpnService, property: KProperty<*>, value: Any?) {
        if (value is VpnNetworkStack) {
            vpnNetworkStack = value
        }
    }
}
