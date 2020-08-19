/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor

import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import timber.log.Timber


class QueueMonitor(private val queues: VpnQueues) : Runnable {

    override fun run() {
        while (!Thread.currentThread().isInterrupted) {
            logQueueStatus()
            Thread.sleep(3_000)
        }
    }

    private fun logQueueStatus() {
        Timber.i("VPN Queues:\nNetwork-to-device: ${queues.networkToDevice.size}\nTCP device-to-network: ${queues.tcpDeviceToNetwork.size}\nUDP device-to-network: ${queues.udpDeviceToNetwork.size}")
    }
}