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

package com.duckduckgo.mobile.android.vpn

import com.duckduckgo.mobile.android.vpn.data.Packet
import thirdpartyneedsrewritten.ip.IpDatagram
import timber.log.Timber
import java.nio.ByteBuffer


class PacketHandler constructor(private val deviceToNetworkPacketProcessor: DeviceToNetworkPacketProcessor) {

    init {
        deviceToNetworkPacketProcessor.packetHandler = this
    }

    fun handleDeviceToNetworkPacket(packet: ByteBuffer) {
        copyRawPacket(packet)
        logPacket(packet)

        when (val protocol = IpDatagram.readProtocol(packet).toInt()) {
            ProtocolVersion.TCP.number -> handleTcpPacket()
            ProtocolVersion.UDP.number -> handleUdpPacket(packet)
            ProtocolVersion.ICMP.number -> handleIcmpPacket()
            else -> Timber.w("Not supporting unknown protocol (protocol %d)", protocol)
        }
    }

    private fun handleUdpPacket(packet: ByteBuffer) {
        deviceToNetworkPacketProcessor.addPacket(Packet(packet))
    }

    fun handleNetworkToDevicePacket(packet: ByteBuffer) {
        //networkToDevicePacketProcessor.addPacket(Packet(packet))
    }

    private fun handleIcmpPacket() {
        Timber.v("Not supporting ICMP yet (protocol 1)")
    }

    private fun handleTcpPacket() {
        Timber.v("Not supporting TCP yet (protocol 6)")
    }

    private fun copyRawPacket(packet: ByteBuffer) {
        val byteArray = ByteArray(packet.limit())
        packet.get(byteArray, 0, packet.limit())
        packet.rewind()
    }

    private fun logPacket(packet: ByteBuffer) {
        Timber.v("Saw packet with %d bytes", packet.limit())
    }
}