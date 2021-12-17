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

package com.duckduckgo.mobile.android.vpn.processor.packet

import com.duckduckgo.mobile.android.vpn.processor.requestingapp.ConnectionInfo
import xyz.hexene.localvpn.Packet

fun Packet.connectionInfo(): ConnectionInfo {
    return ConnectionInfo(
        destinationAddress = ip4Header.destinationAddress,
        destinationPort = extractDestinationPort(),
        sourceAddress = ip4Header.sourceAddress,
        sourcePort = extractSourcePort(),
        protocolNumber = ip4Header.protocol.number)
}

private fun Packet.extractSourcePort() =
    if (isTCP) tcpHeader.sourcePort else if (isUDP) udpHeader.sourcePort else 0

private fun Packet.extractDestinationPort() =
    if (isTCP) tcpHeader.destinationPort else if (isUDP) udpHeader.destinationPort else 0
