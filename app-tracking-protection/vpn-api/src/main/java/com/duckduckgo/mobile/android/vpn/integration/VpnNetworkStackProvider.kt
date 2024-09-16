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

package com.duckduckgo.mobile.android.vpn.integration

import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack

/**
 * This class is used to provide the VPN network stack to the app tracking protection module.
 *
 * Note: This class is exposed in the vpn-api module just temporarily
 * TODO move this class back into the vpn-impl module
 */
interface VpnNetworkStackProvider {

    /**
     * CAll this method to get the VPN network stack to be used in the VPN service.
     */
    @Throws(IllegalStateException::class)
    suspend fun provideNetworkStack(): VpnNetworkStack
}
