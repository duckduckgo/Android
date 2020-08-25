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
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.updateState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.DelayedSendFin
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState
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
                    Timber.w("Network-to-device end of stream ${tcb.ipAndPort}. ${tcb.tcbState} ${TcpPacketProcessor.logPacketDetails(packet)}")

//                    // close connection with remote end point as there's no more communication required
                    key.cancel()
                    channel.close()
//
                    TcpStateFlow.socketEndOfStream(tcb.tcbState).events.forEach {
                        when (it) {
                            is MoveState -> tcb.updateState(it)
                            DelayedSendFin -> {
                                Timber.w("Will send a FIN soon")
                                handler.postDelayed(100) {
                                    Timber.w("Sending FIN")
                                    sendFinAck(packet, receiveBuffer, tcb)
                                }
                            }
                            else -> Timber.w("Unhandled event for ${tcb.ipAndPort}. $it")
                        }
                    }

                    return
//                    if (tcb.status == CLOSE_WAIT) {
//                        tcb.status = LAST_ACK
//                        Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.status}")
//                        ByteBufferPool.release(receiveBuffer)
//                    } else if (tcb.status == LAST_ACK) {
//
//                    }

                } else {
                    val limit = receiveBuffer.limit()
                    val position = receiveBuffer.position()

                    val totalLength = packet.ip4Header.totalLength

                    Timber.i("${tcb.ipAndPort} limit=$limit, position=$position, totalLength=$totalLength, readBytes=$readBytes, tcpHeaderLength=${packet.tcpHeader.headerLength}, ipHeaderLength=${packet.ip4Header.headerLength}")
                    Timber.i("Network-to-device packet ${tcb.ipAndPort}. $readBytes bytes. ${TcpPacketProcessor.logPacketDetails(packet)}")
                    packet.updateTcpBuffer(receiveBuffer, (PSH or ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, readBytes)

                    tcb.mySequenceNum += readBytes
                    receiveBuffer.position(HEADER_SIZE + readBytes)
                    queues.networkToDevice.offer(receiveBuffer)
                }
            } catch (e: IOException) {
                Timber.w(e, "Network read error")
                packet.updateTcpBuffer(receiveBuffer, RST.toByte(), 0, tcb.myAcknowledgementNum, 0)
                queues.networkToDevice.offer(receiveBuffer)
                TCB.closeTCB(tcb)
                return
            }
        }

    }

    private fun sendFin(packet: Packet, receiveBuffer: ByteBuffer?, tcb: TCB) {
        packet.updateTcpBuffer(receiveBuffer, (FIN).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
        tcb.mySequenceNum++
        queues.networkToDevice.offerFirst(receiveBuffer)
    }

    private fun sendFinAck(packet: Packet, receiveBuffer: ByteBuffer?, tcb: TCB) {
        packet.updateTcpBuffer(receiveBuffer, (FIN or ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
        tcb.mySequenceNum++
        queues.networkToDevice.offerFirst(receiveBuffer)
    }

    private fun sendReset(packet: Packet, receiveBuffer: ByteBuffer?, tcb: TCB) {
        packet.updateTcpBuffer(receiveBuffer, (RST).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
        tcb.mySequenceNum++
        queues.networkToDevice.offerFirst(receiveBuffer)
    }

    private fun processConnect(key: SelectionKey) {
        val tcb = key.attachment() as TCB
        val packet = tcb.referencePacket
        runCatching {
            if (tcb.channel.finishConnect()) {
                Timber.d("Finished connecting to ${tcb.ipAndPort}. Sending SYN+ACK.")

                tcb.updateState(MoveServerToState(SYN_RECEIVED))
                tcb.updateState(MoveState.MoveClientToState(SYN_SENT))
                Timber.v("Update TCB ${tcb.ipAndPort} status: ${tcb.tcbState}")

                val responseBuffer = ByteBufferPool.acquire()
                packet.updateTcpBuffer(responseBuffer, (SYN or ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
                queues.networkToDevice.offer(responseBuffer)
                tcb.mySequenceNum++

                tcb.channel.register(selector, OP_NONE)
            } else {
                Timber.v("Not finished connecting yet ${tcb.ipAndPort}")
                tcb.channel.register(selector, OP_CONNECT, tcb)
            }
        }.onFailure {
            Timber.w(it, "Failed to process TCP connect ${tcb.ipAndPort}")
            val responseBuffer = ByteBufferPool.acquire()
            packet.updateTcpBuffer(responseBuffer, RST.toByte(), 0, tcb.myAcknowledgementNum, 0)
            queues.networkToDevice.offer(responseBuffer)
            TCB.closeTCB(tcb)
        }
    }

    private fun endOfStream(readBytes: Int) = readBytes == -1

    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE
        private const val OP_NONE = 0
    }

}