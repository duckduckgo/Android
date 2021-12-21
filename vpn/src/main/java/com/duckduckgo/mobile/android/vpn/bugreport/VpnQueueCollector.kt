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

package com.duckduckgo.mobile.android.vpn.bugreport

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.service.VpnQueuesTimeLogger
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import org.json.JSONObject

@ContributesMultibinding(VpnScope::class)
class VpnQueueCollector
@Inject
constructor(
    private val vpnQueues: VpnQueues,
    private val vpnQueuesTimeLogger: VpnQueuesTimeLogger
) : VpnStateCollectorPlugin {

    override val collectorName: String
        get() = "VpnQueues"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return JSONObject().apply {
            put("vpnQueueSizes", queueSizeData())
            put("vpnQueueActivity", queueTimingData())
        }
    }

    private fun queueSizeData(): JSONObject {
        return JSONObject().apply {
            put(TCP_DEVICE_TO_NETWORK, vpnQueues.tcpDeviceToNetwork.size)
            put(UDP_DEVICE_TO_NETWORK, vpnQueues.udpDeviceToNetwork.size)
            put(NETWORK_TO_DEVICE, vpnQueues.networkToDevice.size)
        }
    }

    private fun queueTimingData(): JSONObject {
        return JSONObject().apply {
            put(
                TIME_SINCE_LAST_BUFFER_TO_VPN,
                vpnQueuesTimeLogger.millisSinceLastDeviceToNetworkWrite())
            put(
                TIME_SINCE_LAST_BUFFER_TO_TUN,
                vpnQueuesTimeLogger.millisSinceLastNetworkToDeviceWrite())
            put(TIME_SINCE_LAST_PACKET_PROCESSED, vpnQueuesTimeLogger.millisSinceLastBufferRead())
        }
    }

    companion object {
        private const val TCP_DEVICE_TO_NETWORK = "tcpDeviceToNetwork"
        private const val UDP_DEVICE_TO_NETWORK = "udpDeviceToNetwork"
        private const val NETWORK_TO_DEVICE = "networkToDevice"

        private const val TIME_SINCE_LAST_BUFFER_TO_VPN = "millisSinceLastPacketDeliveredToVpn"
        private const val TIME_SINCE_LAST_BUFFER_TO_TUN = "millisSinceLastBufferToTun"
        private const val TIME_SINCE_LAST_PACKET_PROCESSED = "millisSinceLastPacketProcessed"
    }
}
