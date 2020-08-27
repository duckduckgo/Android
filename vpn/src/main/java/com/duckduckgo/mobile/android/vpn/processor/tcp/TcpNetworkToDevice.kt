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

import android.os.Handler
import androidx.core.os.postDelayed
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.logPacketDetails
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.updateState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveClientToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveServerToState
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader.*
import xyz.hexene.localvpn.TCB
import xyz.hexene.localvpn.TCB.TCBStatus.SYN_RECEIVED
import xyz.hexene.localvpn.TCB.TCBStatus.SYN_SENT
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SelectionKey.OP_CONNECT
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit
import kotlin.math.log


class TcpNetworkToDevice(
    private val queues: VpnQueues,
    private val selector: Selector,
    private val tcpSocketWriter: TcpSocketWriter,
    private val handler: Handler
) {

    /**
     * Reads data from the network when the selector tells us it has a readable key.
     * When data is read, we add it to the network-to-device queue, which will result in the packet being written back to the TUN.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    fun networkToDeviceProcessing() {
        val startTime = System.nanoTime()
        val channelsReady = selector.select()

        if (channelsReady == 0) {
            Thread.sleep(10)
            return
        }

        val iterator = selector.selectedKeys().iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            iterator.remove()

            kotlin.runCatching {
                if (key.isValid && key.isReadable) {
                    Timber.v("Got next network-to-device packet [isReadable] after ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)}ms wait")
                    processRead(key)
                } else if (key.isValid && key.isConnectable) {
                    Timber.v("Got next network-to-device packet [isConnectable] after ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)}ms wait")
                    processConnect(key)
                } else if (key.isValid && key.isWritable) {

                    val partialWriteData = key.attachment() as TcpPacketProcessor.PendingWriteData
                    Timber.w("Now is the chance to write some more!")

                    val fullyWritten = tcpSocketWriter.writeToSocket(partialWriteData)
                    Timber.i("Fully written this time? %s", fullyWritten)
                    if (fullyWritten) {
                        key.interestOps(SelectionKey.OP_READ)
                    }
                }
            }.onFailure {
                Timber.w(it, "Failure processing selected key for selector")
                key.cancel()
            }
        }
    }

    private fun processRead(key: SelectionKey) {
        val receiveBuffer = ByteBufferPool.acquire()
        receiveBuffer.position(HEADER_SIZE)

        val tcb = key.attachment() as TCB

        synchronized(tcb) {
            val packet = tcb.referencePacket

            val channel = key.channel() as SocketChannel
            try {
                val readBytes = channel.read(receiveBuffer)

                if (endOfStream(readBytes)) {
                    Timber.w("Network-to-device end of stream ${tcb.ipAndPort}. ${tcb.tcbState} ${logPacketDetails(packet, tcb.sequenceNumberToClientInitial,tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient)}")

                    // close connection with remote end point as there's no more communication required
                    key.cancel()
                    channel.close()
//
                    TcpStateFlow.socketEndOfStream(tcb.tcbState).events.forEach {
                        when (it) {
                            is MoveState -> tcb.updateState(it)
                            SendReset -> sendReset(packet, receiveBuffer, tcb)
                            else -> Timber.w("Unhandled event for ${tcb.ipAndPort}. $it")
                        }
                    }
                    return
                } else {
                    //Timber.i("Network-to-device packet ${tcb.ipAndPort}. $readBytes bytes. ${logPacketDetails(packet, tcb.sequenceNumberToClientInitial, tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient)}")
                    packet.updateTcpBuffer(receiveBuffer, (PSH or ACK).toByte(), tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient, readBytes)

                    tcb.sequenceNumberToClient += readBytes
                    receiveBuffer.position(HEADER_SIZE + readBytes)

                    offerToNetworkToDeviceQueue(receiveBuffer, tcb, packet)
                }
            } catch (e: IOException) {
                Timber.w(e, "Network read error")
                sendReset(packet, receiveBuffer, tcb)
                return
            }
        }

    }

    private fun sendFin(packet: Packet, receiveBuffer: ByteBuffer, tcb: TCB) {
        packet.updateTcpBuffer(receiveBuffer, (FIN).toByte(), tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient, 0)
        tcb.sequenceNumberToClient++

        offerToNetworkToDeviceQueue(receiveBuffer, tcb, packet)
    }

    private fun sendFinAck(packet: Packet, receiveBuffer: ByteBuffer, tcb: TCB) {
        packet.updateTcpBuffer(receiveBuffer, (FIN or ACK).toByte(), tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient, 0)
        tcb.sequenceNumberToClient++

        offerToNetworkToDeviceQueue(receiveBuffer, tcb, packet)
    }

    private fun sendReset(packet: Packet, receiveBuffer: ByteBuffer, tcb: TCB) {
        packet.updateTcpBuffer(receiveBuffer, (RST).toByte(), tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient, 0)
        tcb.sequenceNumberToClient++
        offerToNetworkToDeviceQueue(receiveBuffer, tcb, packet)
        TCB.closeTCB(tcb)
    }

    private fun processConnect(key: SelectionKey) {
        val tcb = key.attachment() as TCB
        val packet = tcb.referencePacket
        runCatching {
            if (tcb.channel.finishConnect()) {
                Timber.d("Finished connecting to ${tcb.ipAndPort}. Sending SYN+ACK.")

                tcb.updateState(MoveServerToState(SYN_RECEIVED))
                tcb.updateState(MoveClientToState(SYN_SENT))
                Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.tcbState}")

                val responseBuffer = ByteBufferPool.acquire()
                packet.updateTcpBuffer(responseBuffer, (SYN or ACK).toByte(), tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient, 0)

                offerToNetworkToDeviceQueue(responseBuffer, tcb, packet)
                tcb.sequenceNumberToClient++

                tcb.channel.register(selector, OP_NONE)
            } else {
                Timber.v("Not finished connecting yet ${tcb.ipAndPort}")
                tcb.channel.register(selector, OP_CONNECT, tcb)
            }
        }.onFailure {
            Timber.w(it, "Failed to process TCP connect ${tcb.ipAndPort}")
            val responseBuffer = ByteBufferPool.acquire()
            packet.updateTcpBuffer(responseBuffer, RST.toByte(), 0, tcb.acknowledgementNumberToClient, 0)

            offerToNetworkToDeviceQueue(responseBuffer, tcb, packet)
            TCB.closeTCB(tcb)
        }
    }

    private fun offerToNetworkToDeviceQueue(buffer: ByteBuffer, tcb: TCB, packet: Packet) {
        logPacket(tcb, packet)
        queues.networkToDevice.offer(buffer)
    }

    private fun logPacket(tcb: TCB, packet: Packet) {
        Timber.i(
            "New packet. ${tcb.ipAndPort}. ${tcb.tcbState}. ${logPacketDetails(
                packet,
                tcb.sequenceNumberToClientInitial,
                packet.tcpHeader.sequenceNumber,
                packet.tcpHeader.acknowledgementNumber
            )}. Packet length: ${packet.ip4Header.totalLength}. Data length: ${packet.tcpPayloadSize(false)}"
        )
    }

    private fun endOfStream(readBytes: Int) = readBytes == -1

    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE
        private const val OP_NONE = 0
    }

}