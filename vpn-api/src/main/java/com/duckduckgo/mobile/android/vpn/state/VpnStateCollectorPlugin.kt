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

package com.duckduckgo.mobile.android.vpn.state

import org.json.JSONObject

/**
 * Implement this interface and return the multibinding to return VPN-relevant state information
 *
 * The VPN will call all the [collectVpnRelatedState] methods when it wants to send information
 * about the VPN state. This is generally done when an event happens that requires an AppTP bugreport
 * to be sent, eg. user unprotects an app or reports breakage.
 */
interface VpnStateCollectorPlugin {
    suspend fun collectVpnRelatedState(appPackageId: String? = null): JSONObject
    val collectorName: String
}
