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

package com.duckduckgo.mobile.android.vpn.processor.tcp

import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader
import xyz.hexene.localvpn.TCB
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.random.Random

interface ConnectionInitializer {
    fun initializeConnection(params: TcpConnectionParams): Pair<TCB, SocketChannel>?

    data class TcpConnectionParams(
        val destinationAddress: String,
        val destinationPort: Int,
        val sourcePort: Int,
        val packet: Packet,
        val responseBuffer: ByteBuffer
    ) {

        fun key(): String {
            return "$destinationAddress:$destinationPort:$sourcePort"
        }

    }
}

class TcpConnectionInitializer(private val queues: VpnQueues, private val networkChannelCreator: NetworkChannelCreator) : ConnectionInitializer {

    override fun initializeConnection(params: TcpConnectionParams): Pair<TCB, SocketChannel>? {
        val key = params.key()

        val header = params.packet.tcpHeader
        params.packet.swapSourceAndDestination()

        Timber.d("Initializing connection $key")

        val pair = if (header.isSYN) {
            val channel = networkChannelCreator.createSocketChannel()
            val sequenceNumberToClient = Random.nextLong(Short.MAX_VALUE.toLong() + 1)
            val sequenceToServer = header.sequenceNumber
            val ackNumberToClient = header.sequenceNumber + 1
            val ackNumberToServer = header.acknowledgementNumber

            val tcb = TCB(key, sequenceNumberToClient, sequenceToServer, ackNumberToClient, ackNumberToServer, channel, params.packet)
            TCB.putTCB(params.key(), tcb)
            channel.connect(InetSocketAddress(params.destinationAddress, params.destinationPort))
            Pair(tcb, channel)
        } else {
            Timber.i("Trying to initialize a connection but is not a SYN packet; sending RST")
            params.packet.updateTcpBuffer(
                params.responseBuffer,
                TCPHeader.RST.toByte(),
                0,
                params.packet.tcpHeader.sequenceNumber + 1,
                params.packet.tcpPayloadSize(true)
            )
            queues.networkToDevice.offer(params.responseBuffer)
            null
        }

        return pair
    }
}
