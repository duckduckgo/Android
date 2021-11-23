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

import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.logPacketDetails
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendFinToClient
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.updateState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveClientToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveServerToState
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.store.PACKET_TYPE_TCP
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val packetPersister: PacketPersister,
    private val tcbCloser: TCBCloser,
    private val vpnCoroutineScope: CoroutineScope
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
                    Timber.v("Got next network-to-device packet [isReadable] after %dms wait", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime))
                    processRead(key)
                } else if (key.isValid && key.isConnectable) {
                    Timber.v("Got next network-to-device packet [isConnectable] after %dms wait", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime))
                    processConnect(key)
                } else if (key.isValid && key.isWritable) {
                    val tcb = key.attachment() as TCB
                    Timber.v("Now is the chance to write to the socket %s", tcb.ipAndPort)
                    tcpSocketWriter.writeToSocket(tcb)
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
                Timber.d("Read %d bytes from %s bound for %s/%s", readBytes, tcb.ipAndPort, tcb.requestingAppName, tcb.requestingAppPackage)

                if (endOfStream(readBytes)) {
                    handleEndOfStream(tcb, packet, key)
                    return
                } else {
                    packetPersister.persistDataReceived(readBytes, PACKET_TYPE_TCP)
                    sendToNetworkToDeviceQueue(packet, receiveBuffer, tcb, readBytes)
                }
            } catch (e: IOException) {
                Timber.w(e, "Network read error")
                sendReset(packet, tcb)
                return
            }
        }
    }

    private fun sendToNetworkToDeviceQueue(packet: Packet, receiveBuffer: ByteBuffer, tcb: TCB, readBytes: Int) {
        Timber.v(
            "Network-to-device packet %s. %d bytes. %s",
            tcb.ipAndPort,
            readBytes,
            logPacketDetails(packet, tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient)
        )
        packet.updateTcpBuffer(
            receiveBuffer,
            (PSH or ACK).toByte(),
            tcb.sequenceNumberToClient,
            tcb.acknowledgementNumberToClient,
            readBytes
        )

        tcb.sequenceNumberToClient += readBytes
        receiveBuffer.position(HEADER_SIZE + readBytes)

        offerToNetworkToDeviceQueue(receiveBuffer, tcb, packet)
    }

    private fun handleEndOfStream(tcb: TCB, packet: Packet, key: SelectionKey) {
        Timber.d(
            "Network-to-device end of stream %s. %s %dms after creation %s",
            tcb.ipAndPort,
            tcb.tcbState,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tcb.creationTime),
            logPacketDetails(packet, tcb.sequenceNumberToClient, tcb.acknowledgementNumberToClient)
        )

        key.cancel()

        TcpStateFlow.socketEndOfStream().events.forEach { event ->
            when (event) {
                is MoveState -> tcb.updateState(event)
                SendFin -> tcb.sendFinToClient(queues, packet, 0, triggeredByServerEndOfStream = true)
                SendReset -> sendReset(packet, tcb)
                is SendDelayedFin -> {
                    vpnCoroutineScope.launch {
                        delay(100)
                        tcb.sendFinToClient(queues, packet, 0, triggeredByServerEndOfStream = true)
                        event.events.forEach { tcb.updateState(it) }
                    }
                }
                else -> Timber.w("Unhandled event for %s for socket end of stream. %s", tcb.ipAndPort, event)
            }
        }
    }

    private fun sendReset(packet: Packet, tcb: TCB) {
        val buffer = ByteBufferPool.acquire()
        packet.updateTcpBuffer(
            buffer,
            (RST or ACK).toByte(),
            tcb.sequenceNumberToClient,
            tcb.acknowledgementNumberToClient,
            0
        )
        tcb.sequenceNumberToClient++
        offerToNetworkToDeviceQueue(buffer, tcb, packet)

        tcbCloser.closeConnection(tcb)
    }

    private fun processConnect(key: SelectionKey) {
        val tcb = key.attachment() as TCB
        val packet = tcb.referencePacket
        runCatching {
            if (tcb.channel.finishConnect()) {
                Timber.v("Finished connecting to %s. Sending SYN+ACK.", tcb.ipAndPort)

                tcb.updateState(MoveServerToState(SYN_RECEIVED))
                tcb.updateState(MoveClientToState(SYN_SENT))
                Timber.v("Update TCB %s status: %s", tcb.ipAndPort, tcb.tcbState)

                val responseBuffer = ByteBufferPool.acquire()
                packet.updateTcpBuffer(
                    responseBuffer,
                    (SYN or ACK).toByte(),
                    tcb.sequenceNumberToClient,
                    tcb.acknowledgementNumberToClient,
                    0
                )

                offerToNetworkToDeviceQueue(responseBuffer, tcb, packet)
                tcb.sequenceNumberToClient++

                tcb.channel.register(selector, OP_NONE)
            } else {
                Timber.v("Not finished connecting yet %s", tcb.ipAndPort)
                tcb.channel.register(selector, OP_CONNECT, tcb)
            }
        }.onFailure {
            Timber.w(it, "Failed to process TCP connect %s", tcb.ipAndPort)
            val responseBuffer = ByteBufferPool.acquire()
            packet.updateTcpBuffer(responseBuffer, RST.toByte(), 0, tcb.acknowledgementNumberToClient, 0)

            offerToNetworkToDeviceQueue(responseBuffer, tcb, packet)

            tcbCloser.closeConnection(tcb)
        }
    }

    private fun offerToNetworkToDeviceQueue(buffer: ByteBuffer, tcb: TCB, packet: Packet) {
        logPacket(tcb, packet)
        queues.networkToDevice.offer(buffer)
    }

    private fun logPacket(tcb: TCB, packet: Packet) {
        Timber.v(
            "New packet. %s. %s. %s. Packet length: %d. Data length: %d",
            tcb.ipAndPort,
            tcb.tcbState,
            logPacketDetails(packet, packet.tcpHeader.sequenceNumber, packet.tcpHeader.acknowledgementNumber),
            packet.ip4Header.totalLength,
            packet.tcpPayloadSize(false)
        )

    }

    private fun endOfStream(readBytes: Int) = readBytes == -1

    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE
        private const val OP_NONE = 0
    }
}
