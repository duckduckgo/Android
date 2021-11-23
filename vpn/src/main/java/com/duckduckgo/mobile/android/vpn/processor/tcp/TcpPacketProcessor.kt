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
import com.duckduckgo.mobile.android.vpn.di.TcpNetworkSelector
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifierStrategy
import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveClientToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveServerToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.PayloadBytesExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.LocalIpAddressDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader.ACK
import xyz.hexene.localvpn.Packet.TCPHeader.FIN
import xyz.hexene.localvpn.TCB
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.math.pow

class TcpPacketProcessor @AssistedInject constructor(
    queues: VpnQueues,
    @Assisted networkChannelCreator: NetworkChannelCreator,
    trackerDetector: VpnTrackerDetector,
    packetPersister: PacketPersister,
    localAddressDetector: LocalIpAddressDetector,
    originatingAppPackageResolver: OriginatingAppPackageIdentifierStrategy,
    appNameResolver: AppNameResolver,
    @TcpNetworkSelector selector: Selector,
    tcbCloser: TCBCloser,
    hostnameExtractor: HostnameExtractor,
    payloadBytesExtractor: PayloadBytesExtractor,
    tcpSocketWriter: TcpSocketWriter,
    recentAppTrackerCache: RecentAppTrackerCache,
    @Assisted private val vpnCoroutineScope: CoroutineScope
) : Runnable {

    @AssistedFactory
    interface Factory {
        fun build(networkChannelCreator: NetworkChannelCreator, coroutineScope: CoroutineScope): TcpPacketProcessor
    }

    private var pollJobDeviceToNetwork: Job? = null
    private var pollJobNetworkToDevice: Job? = null
    private var staleTcpConnectionCleanerJob: Job? = null

    private val tcpNetworkToDevice = TcpNetworkToDevice(
        queues = queues,
        selector = selector,
        tcpSocketWriter = tcpSocketWriter,
        packetPersister = packetPersister,
        tcbCloser = tcbCloser,
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
            originatingAppPackageResolver = originatingAppPackageResolver,
            appNameResolver = appNameResolver,
            tcbCloser = tcbCloser,
            hostnameExtractor = hostnameExtractor,
            payloadBytesExtractor = payloadBytesExtractor,
            recentAppTrackerCache = recentAppTrackerCache,
            vpnCoroutineScope = vpnCoroutineScope
        )

    override fun run() {
        Timber.i("Starting %s", this::class.simpleName)

        if (pollJobDeviceToNetwork == null) {
            pollJobDeviceToNetwork = vpnCoroutineScope.launch(newSingleThreadExecutor().asCoroutineDispatcher()) { pollForDeviceToNetworkWork() }
        }

        if (pollJobNetworkToDevice == null) {
            pollJobNetworkToDevice = vpnCoroutineScope.launch(newSingleThreadExecutor().asCoroutineDispatcher()) { pollForNetworkToDeviceWork() }
        }

        if (staleTcpConnectionCleanerJob == null) {
            staleTcpConnectionCleanerJob = vpnCoroutineScope.launch(newSingleThreadExecutor().asCoroutineDispatcher()) { periodicConnectionCleanup() }
        }

    }

    private suspend fun periodicConnectionCleanup() {
        while (staleTcpConnectionCleanerJob?.isActive == true) {
            tcpDeviceToNetwork.cleanupStaleConnections()
            delay(PERIODIC_STALE_CONNECTION_CLEANUP_PERIOD_MS)
        }
    }

    fun stop() {
        Timber.i("Stopping %s", this::class.simpleName)

        pollJobDeviceToNetwork?.cancel()
        pollJobDeviceToNetwork = null

        pollJobNetworkToDevice?.cancel()
        pollJobNetworkToDevice = null

        staleTcpConnectionCleanerJob?.cancel()
        staleTcpConnectionCleanerJob = null
    }

    private fun pollForDeviceToNetworkWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJobDeviceToNetwork?.isActive == true) {
            try {
                tcpDeviceToNetwork.deviceToNetworkProcessing()
            } catch (e: IOException) {
                Timber.w(e, "Failed to process TCP device-to-network packet")
            }
        }
    }

    private fun pollForNetworkToDeviceWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJobNetworkToDevice?.isActive == true) {
            try {
                tcpNetworkToDevice.networkToDeviceProcessing()
            } catch (e: IOException) {
                Timber.w(e, "Failed to process TCP network-to-device packet")
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
                return "\tflags:[ ${isSYN.printFlag("SYN")}${isACK.printFlag("ACK")}${isFIN.printFlag("FIN")}${isPSH.printFlag("PSH")}${
                isRST.printFlag(
                    "RST"
                )
                }${
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

                Timber.v(
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
                    Timber.v(
                        "%s - Sending ACK from network to device. Flags contain RST, SYN or FIN so incremented acknowledge number to %d",
                        ipAndPort,
                        acknowledgementNumberToClient
                    )
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

        fun TCB.updateState(state: MoveState) {
            when (state) {
                is MoveClientToState -> updateState(state)
                is MoveServerToState -> updateState(state)
            }
        }

        private fun TCB.updateState(newState: MoveServerToState) {
            Timber.v("Updating server state: %s", newState.state)
            updateState(tcbState.copy(serverState = newState.state))
        }

        private fun TCB.updateState(newState: MoveClientToState) {
            Timber.v("Updating client state: %s", newState.state)
            updateState(tcbState.copy(clientState = newState.state))
        }

        fun increaseOrWraparound(current: Long, increment: Long): Long {
            return (current + increment) % MAX_SEQUENCE_NUMBER
        }

        private val MAX_SEQUENCE_NUMBER = (2.0.pow(32.0) - 1).toLong()
        private const val PERIODIC_STALE_CONNECTION_CLEANUP_PERIOD_MS = 30_000L
    }

}
