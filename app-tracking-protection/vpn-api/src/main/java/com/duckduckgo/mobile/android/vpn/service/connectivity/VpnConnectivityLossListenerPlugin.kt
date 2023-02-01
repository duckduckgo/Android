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

package com.duckduckgo.mobile.android.vpn.service.connectivity

import kotlinx.coroutines.CoroutineScope

interface VpnConnectivityLossListenerPlugin {
    /**
     * This method will be called whenever we detect a VPN connectivity loss while the device actually has connectivity.
     */
    fun onVpnConnectivityLoss(coroutineScope: CoroutineScope) {}

    /**
     * This method will be called whenever both the VPN and Device have connectivity.
     * Be aware that this would be called periodically, regardless if the VPN connectivity loss was detected prior or not.
     */
    fun onVpnConnected(coroutineScope: CoroutineScope) {}
}
