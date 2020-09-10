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
import java.nio.charset.StandardCharsets


interface VpnTrackerDetector {

    fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer): TrackerType
}

class DomainBasedTrackerDetector(
    private val hostNameHeaderExtractor: HostNameHeaderExtractor,
    private val encryptedRequestHostExtractor: EncryptedRequestHostExtractor,
    private val payloadBytesExtractor: PayloadBytesExtractor
) : VpnTrackerDetector {

    override fun determinePacketType(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer): TrackerType {
        if (tcb.isTracker) return Tracker
        val host = tcb.hostName ?: determineHost(packet, payloadBuffer).also { tcb.hostName = it }

        return NonTracker
    }

    private fun determineHost(packet: Packet, payloadBuffer: ByteBuffer): String? {
        val payloadBytes = payloadBytesExtractor.extract(packet, payloadBuffer)

        var host = hostNameHeaderExtractor.extract(String(payloadBytes, StandardCharsets.US_ASCII))
        if (host != null) {
            Timber.v("Found domain from plaintext headers: %s", host)
            return host
        }

        host = encryptedRequestHostExtractor.extract(payloadBytes)
        if (host != null) {
            Timber.v("Found domain from encrypted headers: %s", host)
            return host
        }

        Timber.w("Failed to extract host")
        return null
    }
}


sealed class TrackerType {
    object Tracker : TrackerType()
    object NonTracker : TrackerType()
}