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

import android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
import android.os.Process.setThreadPriority
import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveClientToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveServerToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.requestingapp.OriginatingAppResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.LocalIpAddressDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader.*
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors
import kotlin.math.pow

class TcpPacketProcessor(
    queues: VpnQueues,
    networkChannelCreator: NetworkChannelCreator,
    trackerDetector: VpnTrackerDetector,
    packetPersister: PacketPersister,
    localAddressDetector: LocalIpAddressDetector,
    originatingAppResolver: OriginatingAppResolver,
    private val vpnCoroutineScope: CoroutineScope
) : Runnable {

    val selector: Selector = Selector.open()

    private var pollJobDeviceToNetwork: Job? = null
    private var pollJobNetworkToDevice: Job? = null

    private val tcpSocketWriter = TcpSocketWriter(selector)
    private val tcpNetworkToDevice = TcpNetworkToDevice(
        queues = queues,
        selector = selector,
        tcpSocketWriter = tcpSocketWriter,
        packetPersister = packetPersister,
        vpnCoroutineScope = vpnCoroutineScope
    )
    private val tcpDeviceToNetwork =
        TcpDeviceToNetwork(
            queues = queues,
            selector = selector,
            socketWriter = tcpSocketWriter,
            connectionInitializer = TcpConnectionInitializer(queues, networkChannelCreator),
            trackerDetector = trackerDetector,
            packetPersister = packetPersister,
            localAddressDetector = localAddressDetector,
            originatingAppResolver = originatingAppResolver,
            vpnCoroutineScope = vpnCoroutineScope
        )

    override fun run() {
        Timber.i("Starting ${this::class.simpleName}")

        if (pollJobDeviceToNetwork == null) {
            pollJobDeviceToNetwork = vpnCoroutineScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollForDeviceToNetworkWork() }
        }

        if (pollJobNetworkToDevice == null) {
            pollJobNetworkToDevice = vpnCoroutineScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollForNetworkToDeviceWork() }
        }

    }

    fun stop() {
        Timber.i("Stopping ${this::class.simpleName}")

        pollJobDeviceToNetwork?.cancel()
        pollJobDeviceToNetwork = null

        pollJobNetworkToDevice?.cancel()
        pollJobNetworkToDevice = null
    }

    private suspend fun pollForDeviceToNetworkWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJobDeviceToNetwork?.isActive == true) {
            kotlin.runCatching {
                tcpDeviceToNetwork.deviceToNetworkProcessing()
            }.onFailure {
                Timber.w(it, "Failed to process TCP device-to-network packet")
            }
        }
    }

    private suspend fun pollForNetworkToDeviceWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJobNetworkToDevice?.isActive == true) {
            kotlin.runCatching {
                tcpNetworkToDevice.networkToDeviceProcessing()
            }.onFailure {
                Timber.w(it, "Failed to process TCP network-to-device packet")
            }
        }
    }

    data class PendingWriteData(
        val payloadBuffer: ByteBuffer,
        val socket: SocketChannel,
        val payloadSize: Int,
        val tcb: TCB,
        val connectionParams: TcpConnectionParams,
        val ackNumber: Long,
        val seqNumber: Long
    )

    companion object {
        fun logPacketDetails(packet: Packet, sequenceNumber: Long, acknowledgementNumber: Long): String {
            with(packet.tcpHeader) {
                return "\tflags:[ ${isSYN.printFlag("SYN")}${isACK.printFlag("ACK")}${isFIN.printFlag("FIN")}${isPSH.printFlag("PSH")}${isRST.printFlag("RST")}${
                isURG.printFlag(
                    "URG"
                )
                }]. [sequenceNumber=$sequenceNumber, ackNumber=$acknowledgementNumber]"
            }
        }

        private fun Boolean.printFlag(name: String): String {
            return if (this) "$name " else ""
        }

        fun ByteBuffer.copyPayloadAsString(payloadSize: Int): String {
            val bytesForLogging = ByteArray(payloadSize)
            this.position().also {
                this.get(bytesForLogging, 0, payloadSize)
                this.position(it)
            }
            return String(bytesForLogging)
        }

        fun TCB.updateState(newStatus: TcbState) {
            this.tcbState = newStatus
        }

        fun TCB.sendFinToClient(queues: VpnQueues, packet: Packet, payloadSize: Int, triggeredByServerEndOfStream: Boolean) {
            val buffer = ByteBufferPool.acquire()
            synchronized(this) {

                var responseAck = acknowledgementNumberToClient + payloadSize
                val responseSeq = acknowledgementNumberToServer

                if (packet.tcpHeader.isFIN) {
                    responseAck = increaseOrWraparound(responseAck, 1)
                }

                this.referencePacket.updateTcpBuffer(buffer, (FIN or ACK).toByte(), responseSeq, responseAck, 0)

                if (triggeredByServerEndOfStream) {
                    finSequenceNumberToClient = sequenceNumberToClient
                } else {
                    sequenceNumberToClient = increaseOrWraparound(sequenceNumberToClient, 1)
                    finSequenceNumberToClient = sequenceNumberToClient
                }

                Timber.i(
                    "%s - Sending FIN/ACK, response=[seqNum=%d, ackNum=%d] - previous=[seqNum=%d, ackNum =%d, payloadSize=%d]",
                    ipAndPort,
                    responseSeq, responseAck,
                    sequenceNumberToClient, acknowledgementNumberToClient, payloadSize
                )

            }

            queues.networkToDevice.offerFirst(buffer)

            try {
                selectionKey.cancel()
                channel.close()
            } catch (e: Exception) {
                Timber.w(e, "Problem closing socket connection for %s", ipAndPort)
            }
        }

        fun TCB.sendAck(queues: VpnQueues, packet: Packet) {
            synchronized(this) {
                val payloadSize = packet.tcpPayloadSize(true)

                acknowledgementNumberToClient = increaseOrWraparound(packet.tcpHeader.sequenceNumber, payloadSize.toLong())
                sequenceNumberToClient = packet.tcpHeader.acknowledgementNumber

                if (packet.tcpHeader.isRST || packet.tcpHeader.isSYN || packet.tcpHeader.isFIN) {
                    acknowledgementNumberToClient = increaseOrWraparound(acknowledgementNumberToClient, 1)
                    Timber.v("$ipAndPort - Sending ACK from network to device. Flags contain RST, SYN or FIN so incremented acknowledge number to $acknowledgementNumberToClient")
                }

                Timber.i(
                    "%s - Sending ACK, payloadSize=%d, seqNumber=%d ackNumber= %d)",
                    ipAndPort,
                    payloadSize,
                    acknowledgementNumberToClient,
                    sequenceNumberToClient
                )

                val buffer = ByteBufferPool.acquire()
                packet.updateTcpBuffer(buffer, (ACK).toByte(), sequenceNumberToClient, acknowledgementNumberToClient, 0)
                // sequenceNumberToClient = increaseOrWraparound(sequenceNumberToClient, 1)
                queues.networkToDevice.offer(buffer)
            }
        }

        @Synchronized
        fun TCB.sendResetPacket(queues: VpnQueues, packet: Packet, payloadSize: Int) {
            val buffer = ByteBufferPool.acquire()

            var responseAck = acknowledgementNumberToClient + payloadSize
            val responseSeq = acknowledgementNumberToServer

            if (packet.tcpHeader.isFIN) {
                responseAck = increaseOrWraparound(responseAck, 1)
            }

            synchronized(this) {
                Timber.i(
                    "%s - Sending RST, response=[seqNum=%d, ackNum=%d] - previous=[seqNum=%d, ackNum =%d, payloadSize=%d]",
                    ipAndPort,
                    responseSeq, responseAck,
                    sequenceNumberToClient, acknowledgementNumberToClient, payloadSize
                )

                this.referencePacket.updateTcpBuffer(buffer, (RST or ACK).toByte(), responseSeq, responseAck, 0)
            }
            queues.networkToDevice.offerFirst(buffer)
            TCB.closeTCB(this)
        }

        @Synchronized
        fun TCB.closeConnection(buffer: ByteBuffer) {
            Timber.v("Closing TCB connection $ipAndPort")
            ByteBufferPool.release(buffer)
            TCB.closeTCB(this)
        }

        fun TCB.logAckSeqDetails(): String {
            return "[mySeq:rel=${sequenceNumberToClient - sequenceNumberToClientInitial}, abs=$sequenceNumberToClient, myAck: $acknowledgementNumberToClient]"
        }

        fun TCB.updateState(state: MoveState) {
            when (state) {
                is MoveClientToState -> updateState(state)
                is MoveServerToState -> updateState(state)
            }
        }

        private fun TCB.updateState(newState: MoveServerToState) {
            Timber.v("Updating server state: ${newState.state}")
            updateState(tcbState.copy(serverState = newState.state))
        }

        private fun TCB.updateState(newState: MoveClientToState) {
            Timber.v("Updating client state: ${newState.state}")
            updateState(tcbState.copy(clientState = newState.state))
        }

        fun increaseOrWraparound(current: Long, increment: Long): Long {
            return (current + increment) % MAX_SEQUENCE_NUMBER
        }

        private val MAX_SEQUENCE_NUMBER = (2.0.pow(32.0) - 1).toLong()
    }

}
