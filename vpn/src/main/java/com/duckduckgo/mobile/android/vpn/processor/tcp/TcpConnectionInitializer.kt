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

import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader
import xyz.hexene.localvpn.TCB
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import kotlin.random.Random

class TcpConnectionInitializer constructor(private val queues: VpnQueues, private val selector: Selector, private val networkChannelCreator: NetworkChannelCreator) {

    fun initializeConnection(params: TcpConnectionParams) {
        val key = params.key()
        Timber.v("Initializing TCP connection for $key")

        val header = params.packet.tcpHeader
        params.packet.swapSourceAndDestination()
        val isSyn = header.isSYN
        val isAck = header.isACK
        val isFin = header.isFIN
        val isPsh = header.isPSH
        val isRst = header.isRST
        val isUrg = header.isURG

        Timber.i("Initializing connection. packet type:\tsyn=$isSyn,\tack=$isAck,\tfin=$isFin,\tpsh=$isPsh,\trst=$isRst,\turg=$isUrg. key=$key")

        if (header.isSYN) {
            Timber.v("is SYN request")
            val channel = networkChannelCreator.createSocket()
            val sequenceNumber = Random.nextLong(Short.MAX_VALUE.toLong() + 1)
            val sequenceFromPacket = header.sequenceNumber
            val ackNumber = header.sequenceNumber + 1
            val ackFromPacket = header.acknowledgementNumber

            val tcb = TCB(key,
                sequenceNumber,
                sequenceFromPacket,
                ackNumber,
                ackFromPacket,
                channel,
                params.packet)
            TCB.putTCB(params.key(), tcb)
            connect(tcb, channel, params)
        } else {
            Timber.i("Trying to initialize a connection but is not a SYN packet; sending RST")
            params.packet.updateTCPBuffer(params.responseBuffer, TCPHeader.RST.toByte(), 0, params.packet.tcpHeader.sequenceNumber + 1, 0)
            queues.networkToDevice.offer(params.responseBuffer)
        }
    }

    private fun connect(tcb: TCB, channel: SocketChannel, params: TcpConnectionParams) {
        channel.connect(InetSocketAddress(params.destinationAddress, params.destinationPort))
        if (channel.finishConnect()) {
            Timber.i("Channel finished connecting to ${params.destinationAddress}")
            tcb.status = TCB.TCBStatus.SYN_RECEIVED
            params.packet.updateTCPBuffer(params.responseBuffer, (TCPHeader.SYN or TCPHeader.ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
            tcb.mySequenceNum++
            queues.networkToDevice.offer(params.responseBuffer)
        } else {
            Timber.i("Not finished connecting yet to ${params.destinationAddress}, will register for OP_CONNECT event")
            tcb.status = TCB.TCBStatus.SYN_SENT
            selector.wakeup()
            tcb.selectionKey = channel.register(selector, SelectionKey.OP_CONNECT, tcb)
        }
    }

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
