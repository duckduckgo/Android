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

package com.duckduckgo.mobile.android.vpn.processor.tracker

import com.duckduckgo.mobile.android.vpn.processor.tracker.TrackerType.NonTracker
import com.duckduckgo.mobile.android.vpn.processor.tracker.TrackerType.Tracker
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer


interface VpnTrackerDetector {

    fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer): TrackerType
}

class DomainBasedTrackerDetector(private val domainNameExtractor: DomainNameExtractor) : VpnTrackerDetector {

    override fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer): TrackerType {
        if(tcb.isTracker) return Tracker
        val domain = domainNameExtractor.extractDomain(packet, payloadBuffer)
        if(domain != null) {
            Timber.i("Found domain from plaintext headers: %s", domain)
        }

        return NonTracker
    }

}

sealed class TrackerType {
    object Tracker : TrackerType()
    object NonTracker: TrackerType()
}