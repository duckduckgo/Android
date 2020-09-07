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

import timber.log.Timber
import xyz.hexene.localvpn.Packet
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets


interface DomainNameExtractor {

    fun extractDomain(packet: Packet, payloadBuffer: ByteBuffer): String?
}

class PlaintextDomainExtractor : DomainNameExtractor {

    private val newlineRegex: Regex = "\\n".toRegex()

    override fun extractDomain(packet: Packet, payloadBuffer: ByteBuffer): String? {
        val headerLength = packet.ip4Header.headerLength + packet.tcpHeader.headerLength
        val packetLength = packet.ip4Header.totalLength
        val originalArray = ByteArray(packetLength)

        if (payloadBuffer.hasArray()) {
            System.arraycopy(payloadBuffer.array(), payloadBuffer.arrayOffset(), originalArray, 0, packetLength)
        } else {
            payloadBuffer.get(originalArray)
        }

        val subsetArray = originalArray.copyOfRange(headerLength, packetLength)
        val packetData = String(subsetArray, StandardCharsets.US_ASCII)
        Timber.v("Got packet data: %s", packetData)

        packetData.split(newlineRegex).forEach {line ->
            val trimmed = line.trim { it <= ' ' }
            if (trimmed.startsWith("Host:")) {
                return trimmed.substring(6)
            }
        }


        return null
    }


}