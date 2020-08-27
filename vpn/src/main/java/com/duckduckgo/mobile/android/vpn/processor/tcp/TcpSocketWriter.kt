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

import timber.log.Timber
import xyz.hexene.localvpn.Packet
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

interface SocketWriter {
    fun writeToSocket(writeData: TcpPacketProcessor.PendingWriteData): Boolean
}

class TcpSocketWriter(private val selector:Selector) : SocketWriter{

    @Synchronized
    override fun writeToSocket(writeData: TcpPacketProcessor.PendingWriteData): Boolean {
        Timber.v("Writing data to socket ${writeData.tcb.ipAndPort}: ${writeData.payloadSize} bytes")
        val payloadBuffer = writeData.payloadBuffer
        val payloadSize = writeData.payloadSize
        val socket = writeData.socket
        val connectionParams = writeData.connectionParams
        val tcb = writeData.tcb

        val bytesWritten = socket.write(payloadBuffer)
        if (payloadBuffer.remaining() > 0) {
            Timber.e("Hey hey, hey. now what? %d bytes written. %d bytes remain out of %d", bytesWritten, payloadBuffer.remaining(), payloadSize)

//            selector.wakeup()
//            socket.register(selector,
//                SelectionKey.OP_WRITE,
//                TcpPacketProcessor.PendingWriteData(ByteBuffer.wrap(remainingData), socket, payloadSize, tcb, connectionParams)
//            )
            return false
        } else {
            selector.wakeup()
            socket.register(selector, SelectionKey.OP_READ, tcb)
            tcb.acknowledgementNumberToClient = connectionParams.packet.tcpHeader.sequenceNumber + payloadSize
            tcb.acknowledgementNumberToServer = connectionParams.packet.tcpHeader.acknowledgementNumber
            val seqToClient = tcb.sequenceNumberToClient
            val ackToServer = tcb.acknowledgementNumberToServer
            val seqAckDiff = seqToClient-ackToServer
            Timber.i("seqToClient=$seqToClient, ackToServer=$ackToServer, diff=$seqAckDiff")
            tcb.referencePacket.updateTcpBuffer(connectionParams.responseBuffer, Packet.TCPHeader.ACK.toByte(), tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient, 0)
            return true
        }
    }

}