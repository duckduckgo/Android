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

package com.duckduckgo.mobile.android.vpn.processor.tcp.tracker

import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType.Tracker
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer


interface VpnTrackerDetector {

    fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer): RequestTrackerType
}

class DomainBasedTrackerDetector(
    private val hostnameExtractor: HostnameExtractor
) : VpnTrackerDetector {

    override fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer): RequestTrackerType {
        val hostname = hostnameExtractor.extract(tcb, packet, payloadBuffer)
        if (hostname == null) {
            Timber.w("Failed to determine if packet is a tracker as hostname not extracted %s", tcb.ipAndPort)
            return RequestTrackerType.Undetermined
        }

        tcb.trackerTypeDetermined = true

        if (hostname.isTracker()) {
            tcb.isTracker = true
            Timber.i("Determined %s to be tracker %s", hostname, tcb.ipAndPort)
            return Tracker
        }

        tcb.isTracker = false
        Timber.v("Determined %s is not a tracker %s", hostname, tcb.ipAndPort)

        return RequestTrackerType.NotTracker
    }

    private fun String.isTracker(): Boolean {
        return this == "example.com"
    }

}