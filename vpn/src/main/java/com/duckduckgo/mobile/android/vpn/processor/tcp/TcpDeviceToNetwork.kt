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

import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.copyPayloadAsString
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.PendingWriteData
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import xyz.hexene.localvpn.TCB.TCBStatus.FIN_WAIT_1
import xyz.hexene.localvpn.TCB.TCBStatus.FIN_WAIT_2
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel


class TcpDeviceToNetwork(private val queues: VpnQueues,
                         private val selector: Selector,
                         private val socketWriter: TcpSocketWriter,
                         private val connectionInitializer: TcpConnectionInitializer) {

    /**
     * Reads from the device-to-network queue. For any packets in this queue, a new DatagramChannel is created and the packet is written.
     * Instructs the selector we'll be interested in OP_READ for receiving the response to the packet we write.
     */
    fun deviceToNetworkProcessing() {
        val packet = queues.tcpDeviceToNetwork.poll()
        if (packet == null) {
            Thread.sleep(10)
            return
        }

        val destinationAddress = packet.ip4Header.destinationAddress
        val destinationPort = packet.tcpHeader.destinationPort
        val sourcePort = packet.tcpHeader.sourcePort

        val payloadBuffer = packet.backingBuffer
        packet.backingBuffer = null

        val responseBuffer = ByteBufferPool.acquire()
        val connectionParams = TcpConnectionInitializer.TcpConnectionParams(destinationAddress.hostAddress, destinationPort, sourcePort, packet, responseBuffer)
        val connectionKey = connectionParams.key()

        val totalPacketLength = payloadBuffer.limit()

        val tcb = TCB.getTCB(connectionKey)
        if (tcb == null) {
            Timber.i("Device-to-network packet: $connectionKey. TCB not initialized. ${TcpPacketProcessor.logPacketDetails(packet)}. Packet length: $totalPacketLength")
            connectionInitializer.initializeConnection(connectionParams)
        } else {
            Timber.i("Device-to-network packet: $connectionKey. ${TcpPacketProcessor.logPacketDetails(packet)}. Packet length: $totalPacketLength")

            when {
                packet.tcpHeader.isSYN -> processDuplicateSyn(tcb, connectionParams)
                packet.tcpHeader.isRST -> closeConnection(tcb, responseBuffer)
                packet.tcpHeader.isFIN -> processFin(tcb, connectionParams)
                packet.tcpHeader.isACK -> processAck(tcb, payloadBuffer, connectionParams)
                else -> Timber.w("TCP packet has no known flags; dropping")
            }
        }

        if (responseBuffer.position() == 0) {
            ByteBufferPool.release(responseBuffer)
        }
        ByteBufferPool.release(payloadBuffer)
    }

    private fun processAck(tcb: TCB, payloadBuffer: ByteBuffer, connectionParams: TcpConnectionInitializer.TcpConnectionParams) {
        val payloadSize = payloadBuffer.limit() - payloadBuffer.position()
        synchronized(tcb) {

            val socket = tcb.channel as SocketChannel
            if (tcb.status == TCB.TCBStatus.SYN_RECEIVED) {
                tcb.status = TCB.TCBStatus.ESTABLISHED
                Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.status}")

                selector.wakeup()
                tcb.selectionKey = socket.register(selector, SelectionKey.OP_READ, tcb)
                tcb.waitingForNetworkData = true
            }
            else if (tcb.status == FIN_WAIT_1){
                tcb.status = FIN_WAIT_2
                Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.status}")
            }
            else if (tcb.status == TCB.TCBStatus.LAST_ACK) {
                closeConnection(tcb, connectionParams.responseBuffer)
                return
            }

            if (payloadSize == 0) return

            val payloadString = payloadBuffer.copyPayloadAsString(payloadSize)

            Timber.v("${tcb.ipAndPort} has $payloadSize bytes of data\n${payloadString}")

            if (!tcb.waitingForNetworkData) {
                Timber.w("Not waiting for network data ${tcb.ipAndPort}; register for OP_READ and wait for network data")
                selector.wakeup()
                tcb.selectionKey.interestOps(SelectionKey.OP_READ)
                tcb.waitingForNetworkData = true
            }

            try {
                socketWriter.writeToSocket(PendingWriteData(payloadBuffer, socket, payloadSize, tcb, connectionParams))
            } catch (e: IOException) {
                Timber.w(e, "Network write error")
                sendResetPacket(tcb, payloadSize, connectionParams.responseBuffer)
                return
            }
        }

        queues.networkToDevice.offer(connectionParams.responseBuffer)
    }

    private fun processDuplicateSyn(tcb: TCB, params: TcpConnectionInitializer.TcpConnectionParams) {
        Timber.v("Processing duplicate SYN")

        synchronized(tcb) {
            if (tcb.status == TCB.TCBStatus.SYN_SENT) {
                tcb.myAcknowledgementNum = params.packet.tcpHeader.sequenceNumber + 1
                return
            }
        }

        sendResetPacket(tcb, 1, params.responseBuffer)
    }

    private fun processFin(tcb: TCB, connectionParams: TcpConnectionInitializer.TcpConnectionParams) {
        val packet = tcb.referencePacket
        tcb.myAcknowledgementNum = connectionParams.packet.tcpHeader.sequenceNumber + 1
        tcb.theirAcknowledgementNum = connectionParams.packet.tcpHeader.acknowledgementNumber

        if (tcb.waitingForNetworkData) {
            tcb.status = TCB.TCBStatus.CLOSE_WAIT
            Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.status}")
            packet.updateTCPBuffer(connectionParams.responseBuffer, Packet.TCPHeader.ACK.toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
        } else {
            tcb.status = TCB.TCBStatus.LAST_ACK
            Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.status}")
            packet.updateTCPBuffer(connectionParams.responseBuffer, (Packet.TCPHeader.FIN or Packet.TCPHeader.ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
            tcb.mySequenceNum++
        }
        queues.networkToDevice.offer(connectionParams.responseBuffer)
    }

    private fun sendResetPacket(tcb: TCB, previousPayLoadSize: Int, responseBuffer: ByteBuffer) {
        Timber.w("Sending device-to-network reset packet ${tcb.ipAndPort}")
        tcb.referencePacket.updateTCPBuffer(responseBuffer, Packet.TCPHeader.RST.toByte(), 0, tcb.myAcknowledgementNum + previousPayLoadSize, 0)
        queues.networkToDevice.offer(responseBuffer)
        TCB.closeTCB(tcb)
    }

    private fun closeConnection(tcb: TCB, buffer: ByteBuffer) {
        Timber.v("Closing TCB connection ${tcb.ipAndPort}")
        ByteBufferPool.release(buffer)
        TCB.closeTCB(tcb)
    }


}