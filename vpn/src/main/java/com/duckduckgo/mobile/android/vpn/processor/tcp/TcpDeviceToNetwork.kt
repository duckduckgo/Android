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
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.closeConnection
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.logAckSeqDetails
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendAck
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendFinAck
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendResetPacket
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.updateStatus
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.PendingWriteData
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Transition.MoveToState
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet.TCPHeader.*
import xyz.hexene.localvpn.TCB
import xyz.hexene.localvpn.TCB.TCBStatus.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

class TcpDeviceToNetwork(
    private val queues: VpnQueues,
    private val selector: Selector,
    private val socketWriter: SocketWriter,
    private val connectionInitializer: ConnectionInitializer
) {

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

        val payloadBuffer = packet.backingBuffer ?: return
        packet.backingBuffer = null

        val responseBuffer = ByteBufferPool.acquire()
        val connectionParams = TcpConnectionParams(destinationAddress.hostAddress, destinationPort, sourcePort, packet, responseBuffer)
        val connectionKey = connectionParams.key()

        val totalPacketLength = payloadBuffer.limit()

        val tcb = TCB.getTCB(connectionKey)
        if (tcb == null) {
            Timber.i("Device-to-network packet: $connectionKey. TCB not initialized. ${TcpPacketProcessor.logPacketDetails(packet)}. Packet length: $totalPacketLength")
            TcpStateFlow.newPacket(TCPState(), packet.asPacketType()).events.forEach {
                when (it){
                    OpenConnection -> openConnection(connectionParams)
                    SendReset -> {
                        Timber.i("Trying to initialize a connection but is not a SYN packet; sending RST to $destinationAddress:$destinationPort")
                        connectionParams.packet.updateTcpBuffer(
                            connectionParams.responseBuffer,
                            RST.toByte(),
                            0,
                            connectionParams.packet.tcpHeader.sequenceNumber + 1,
                            0
                        )
                        queues.networkToDevice.offer(connectionParams.responseBuffer)
                    }
                    is MoveToState -> {

                    }
                    else -> Timber.w("No connection open and won't open one to $destinationAddress:$destinationPort. Dropping packet.")
                }
            }
        } else {
            Timber.i("Device-to-network packet: $connectionKey. ${tcb.tcbState}. ${tcb.logAckSeqDetails()} ${TcpPacketProcessor.logPacketDetails(packet)}. Packet length: $totalPacketLength")

            val action = TcpStateFlow.newPacket(tcb.tcbState, packet.asPacketType())
            Timber.v("Action: ${action.transition} - ${action.events} for ${tcb.ipAndPort}")
            if (action.transition is MoveToState) {
                tcb.updateStatus(action.transition.state)
            }
            when (action.events) {
                ProcessDuplicateSyn -> processDuplicateSyn(tcb, connectionParams)
                CloseConnection -> tcb.closeConnection(responseBuffer)
                SendFinAck -> {
                    processPacket(tcb, payloadBuffer, connectionParams)
                    tcb.sendFinAck(queues, packet, connectionParams)
                }
                SendAck -> tcb.sendAck(queues, packet, connectionParams)
                WaitToRead -> waitToRead(tcb)
                ProcessPacket -> processPacket(tcb, payloadBuffer, connectionParams)
                SendReset -> {
                    connectionParams.packet.updateTcpBuffer(
                        connectionParams.responseBuffer,
                        RST.toByte(),
                        0,
                        connectionParams.packet.tcpHeader.sequenceNumber + 1,
                        0
                    )
                    queues.networkToDevice.offer(connectionParams.responseBuffer)
                }
                SendAckAndCloseConnection -> {
                    tcb.sendAck(queues, packet, connectionParams)
                    Timber.e("TODO: Need to kill connection after short delay ${tcb.ipAndPort}")
                }
                NoEvent -> {
                }
                else -> Timber.w("Unknown event: ${action.events}")
            }

//            when {
//                packet.tcpHeader.isSYN -> processDuplicateSyn(tcb, connectionParams)
//                packet.tcpHeader.isRST -> closeConnection(tcb, responseBuffer)
//                packet.tcpHeader.isFIN -> processFin(tcb, connectionParams)
//                packet.tcpHeader.isACK -> processAck(tcb, payloadBuffer, connectionParams)
//                else -> Timber.w("TCP packet has no known flags; dropping")
//            }
        }

        if (responseBuffer.position() == 0) {
            ByteBufferPool.release(responseBuffer)
        }
        ByteBufferPool.release(payloadBuffer)
    }

    private fun openConnection(params: TcpConnectionParams) {
        val (tcb, channel) = connectionInitializer.initializeConnection(params) ?: return
        val action = TcpStateFlow.socketOpening(CLOSED, channel.finishConnect())
        if (action.transition is MoveToState) {
            tcb.updateStatus(action.transition.state)
        }

        when (action.events) {
            SendSynAck -> {
                Timber.v("Channel finished connecting to ${tcb.ipAndPort}")
                params.packet.updateTcpBuffer(params.responseBuffer, (SYN or ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
                tcb.mySequenceNum++
                queues.networkToDevice.offer(params.responseBuffer)
            }
            WaitToRead -> {
                Timber.v("Not finished connecting yet to ${tcb.selectionKey}, will register for OP_CONNECT event")
                selector.wakeup()
                tcb.selectionKey = channel.register(selector, SelectionKey.OP_CONNECT, tcb)
            }
            else -> Timber.w("Unexpected action: ${action.events}")
        }
    }

    private fun processPacket(tcb: TCB, payloadBuffer: ByteBuffer, connectionParams: TcpConnectionParams) {
        val payloadSize = payloadBuffer.limit() - payloadBuffer.position()
        if (payloadSize == 0) return
        synchronized(tcb) {

            if (!tcb.waitingForNetworkData) {
                Timber.w("Not waiting for network data ${tcb.ipAndPort}; register for OP_READ and wait for network data")
                selector.wakeup()
                tcb.selectionKey.interestOps(SelectionKey.OP_READ)
                tcb.waitingForNetworkData = true
            }

            try {
                socketWriter.writeToSocket(PendingWriteData(payloadBuffer, tcb.channel, payloadSize, tcb, connectionParams))
            } catch (e: IOException) {
                Timber.w(e, "Network write error")
                tcb.sendResetPacket(queues, payloadSize, connectionParams.responseBuffer)
                return
            }
        }
        queues.networkToDevice.offer(connectionParams.responseBuffer)
    }

    private fun processAck(tcb: TCB, payloadBuffer: ByteBuffer, connectionParams: TcpConnectionParams) {
        // val payloadSize = payloadBuffer.limit() - payloadBuffer.position()
        // synchronized(tcb) {
        //
        //     val socket = tcb.channel as SocketChannel
        //     when (tcb.clientState) {
        //         SYN_RECEIVED -> {
        //             tcb.clientState = ESTABLISHED
        //             Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.clientState}")
        //
        //             waitToRead(tcb)
        //         }
        //         FIN_WAIT_1 -> {
        //             tcb.clientState = FIN_WAIT_2
        //             Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.clientState}")
        //             return
        //         }
        //         LAST_ACK -> {
        //             tcb.closeConnection(connectionParams.responseBuffer)
        //             return
        //         }
        //         CLOSE_WAIT -> {
        //             tcb.closeConnection(connectionParams.responseBuffer)
        //             return
        //         }
        //
        //         //val payloadString = payloadBuffer.copyPayloadAsString(payloadSize)
        //         //Timber.v("${tcb.ipAndPort} has $payloadSize bytes of data\n${payloadString}")
        //     }
        //
        //     if (payloadSize == 0) return
        //
        //     //val payloadString = payloadBuffer.copyPayloadAsString(payloadSize)
        //     //Timber.v("${tcb.ipAndPort} has $payloadSize bytes of data\n${payloadString}")
        //
        //     if (!tcb.waitingForNetworkData) {
        //         Timber.w("Not waiting for network data ${tcb.ipAndPort}; register for OP_READ and wait for network data")
        //         selector.wakeup()
        //         tcb.selectionKey.interestOps(SelectionKey.OP_READ)
        //         tcb.waitingForNetworkData = true
        //     }
        //
        //     try {
        //         socketWriter.writeToSocket(PendingWriteData(payloadBuffer, socket, payloadSize, tcb, connectionParams))
        //     } catch (e: IOException) {
        //         Timber.w(e, "Network write error")
        //         tcb.sendResetPacket(queues, payloadSize, connectionParams.responseBuffer)
        //         return
        //     }
        // }
        //
        // queues.networkToDevice.offer(connectionParams.responseBuffer)
    }

    private fun waitToRead(tcb: TCB) {
        selector.wakeup()
        tcb.selectionKey = tcb.channel.register(selector, SelectionKey.OP_READ, tcb)
        tcb.waitingForNetworkData = true
    }

    private fun processDuplicateSyn(tcb: TCB, params: TcpConnectionParams) {
        // Timber.v("Processing duplicate SYN")
        //
        // synchronized(tcb) {
        //     if (tcb.clientState == TCB.TCBStatus.SYN_SENT) {
        //         tcb.myAcknowledgementNum = params.packet.tcpHeader.sequenceNumber + 1
        //         return
        //     }
        // }
        //
        // tcb.sendResetPacket(queues, 1, params.responseBuffer)
    }

    private fun processFin(tcb: TCB, connectionParams: TcpConnectionParams) {
        // val packet = tcb.referencePacket
        // tcb.myAcknowledgementNum = connectionParams.packet.tcpHeader.sequenceNumber + 1
        // tcb.theirAcknowledgementNum = connectionParams.packet.tcpHeader.acknowledgementNumber
        //
        // when (tcb.clientState) {
        //     ESTABLISHED -> {
        //         Timber.i("FIN packet received in established state; send ACK and move to CLOSE_WAIT")
        //
        //         tcb.clientState = LAST_ACK
        //         Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.clientState}")
        //         tcb.sendFinAck(queues, packet, connectionParams)
        //     }
        //     LAST_ACK -> {
        //         tcb.clientState = LAST_ACK
        //         Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.clientState}")
        //
        //         tcb.sendFinAck(queues, packet, connectionParams)
        //     }
        //     FIN_WAIT_2 -> {
        //         packet.updateTcpBuffer(connectionParams.responseBuffer, (ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
        //         tcb.mySequenceNum++
        //         queues.networkToDevice.offer(connectionParams.responseBuffer)
        //         //TCB.closeTCB(tcb)
        //     }
        //     else -> {
        //         Timber.w("FIN packet received when in state ${tcb.clientState}. Send RST and close connection")
        //         packet.updateTcpBuffer(connectionParams.responseBuffer, RST.toByte(), 0, tcb.myAcknowledgementNum, 0)
        //         queues.networkToDevice.offer(connectionParams.responseBuffer)
        //         //TCB.closeTCB(tcb)
        //     }
        // }

//        if (tcb.waitingForNetworkData) {
//            tcb.status = TCB.TCBStatus.CLOSE_WAIT
//            Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.status}")
//            packet.updateTCPBuffer(connectionParams.responseBuffer, Packet.TCPHeader.ACK.toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
//        } else {
//            tcb.status = TCB.TCBStatus.LAST_ACK
//            Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.status}")
//            packet.updateTCPBuffer(connectionParams.responseBuffer, (Packet.TCPHeader.FIN or Packet.TCPHeader.ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
//            tcb.mySequenceNum++
//        }
//        queues.networkToDevice.offer(connectionParams.responseBuffer)
    }
}
