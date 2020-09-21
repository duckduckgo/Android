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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

import xyz.hexene.localvpn.Packet
import java.nio.ByteBuffer


class PayloadBytesExtractor {

    fun extract(packet: Packet, payloadBuffer: ByteBuffer): ByteArray {
        val headerLength = packet.ip4Header.headerLength + packet.tcpHeader.headerLength
        val payloadSize = packet.ip4Header.totalLength - headerLength
        val newArray = ByteArray(payloadSize)

        if (payloadBuffer.hasArray()) {
            System.arraycopy(payloadBuffer.array(), payloadBuffer.arrayOffset() + headerLength, newArray, 0, payloadSize)
        } else {
            payloadBuffer.get(newArray, headerLength, payloadSize)
        }
        return newArray
    }

}