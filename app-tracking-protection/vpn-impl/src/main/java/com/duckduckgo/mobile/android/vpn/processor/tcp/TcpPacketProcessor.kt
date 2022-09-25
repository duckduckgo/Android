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
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.mobile.android.vpn.di.TcpNetworkSelector
import com.duckduckgo.mobile.android.vpn.health.HealthMetricCounter
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
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader.ACK
import xyz.hexene.localvpn.Packet.TCPHeader.FIN
import xyz.hexene.localvpn.TCB

class TcpPacketProcessor
@AssistedInject
constructor(
    queues: VpnQueues,
    connectionInitializer: ConnectionInitializer,
    trackerDetector: VpnTrackerDetector,
    packetPersister: PacketPersister,
    localAddressDetector: LocalIpAddressDetector,
    originatingAppPackageResolver: OriginatingAppPackageIdentifierStrategy,
    appNameResolver: AppNameResolver,
    @TcpNetworkSelector val selector: Selector,
    tcbCloser: TCBCloser,
    hostnameExtractor: HostnameExtractor,
    payloadBytesExtractor: PayloadBytesExtractor,
    tcpSocketWriter: TcpSocketWriter,
    recentAppTrackerCache: RecentAppTrackerCache,
    @Assisted private val vpnCoroutineScope: CoroutineScope,
    healthMetricCounter: HealthMetricCounter,
) : Runnable {

    @AssistedFactory
    interface Factory {
        fun build(
            coroutineScope: CoroutineScope
        ): TcpPacketProcessor
    }

    private val pollJobDeviceToNetwork = newSingleThreadExecutor().apply {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)
    }
    private val pollJobNetworkToDevice = newSingleThreadExecutor().apply {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)
    }
    private val staleTcpConnectionCleanerJob = ConflatedJob()

    private val tcpNetworkToDevice =
        TcpNetworkToDevice(
            queues = queues,
            selector = selector,
            tcpSocketWriter = tcpSocketWriter,
            packetPersister = packetPersister,
            tcbCloser = tcbCloser,
            vpnCoroutineScope = vpnCoroutineScope,
            healthMetricCounter = healthMetricCounter
        )
    private val tcpDeviceToNetwork =
        TcpDeviceToNetwork(
            queues = queues,
            selector = selector,
            socketWriter = tcpSocketWriter,
            connectionInitializer = connectionInitializer,
            trackerDetector = trackerDetector,
            packetPersister = packetPersister,
            localAddressDetector = localAddressDetector,
            originatingAppPackageResolver = originatingAppPackageResolver,
            appNameResolver = appNameResolver,
            tcbCloser = tcbCloser,
            hostnameExtractor = hostnameExtractor,
            payloadBytesExtractor = payloadBytesExtractor,
            recentAppTrackerCache = recentAppTrackerCache,
            vpnCoroutineScope = vpnCoroutineScope,
            healthMetricCounter = healthMetricCounter
        )

    override fun run() {
        Timber.i("Starting %s", this::class.simpleName)

        pollJobDeviceToNetwork.execute(tcpDeviceToNetwork)
        pollJobNetworkToDevice.execute(tcpNetworkToDevice)

        staleTcpConnectionCleanerJob +=
            vpnCoroutineScope.launch(newSingleThreadExecutor().asCoroutineDispatcher()) {
                periodicConnectionCleanup()
            }
    }

    private suspend fun periodicConnectionCleanup() {
        while (staleTcpConnectionCleanerJob.isActive) {
            tcpDeviceToNetwork.cleanupStaleConnections()
            delay(PERIODIC_STALE_CONNECTION_CLEANUP_PERIOD_MS)
        }
    }

    fun stop() {
        Timber.i("Stopping %s", this::class.simpleName)

        pollJobDeviceToNetwork.shutdownNow()
        pollJobNetworkToDevice.shutdownNow()
        staleTcpConnectionCleanerJob.cancel()
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
        fun logPacketDetails(
            packet: Packet,
            sequenceNumber: Long?,
            acknowledgementNumber: Long?
        ): String {
            with(packet.tcpHeader) {
                return "\tflags:[ ${isSYN.printFlag("SYN")}" +
                    "${isACK.printFlag("ACK")}${isFIN.printFlag("FIN")}${isPSH.printFlag("PSH")}${
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

        fun TCB.sendFinToClient(
            queues: VpnQueues,
            packet: Packet,
            payloadSize: Int,
            triggeredByServerEndOfStream: Boolean
        ) {
            val buffer = ByteBufferPool.acquire()
            synchronized(this) {
                var responseAck = acknowledgementNumberToClient.addAndGet(payloadSize.toLong())
                val responseSeq = acknowledgementNumberToServer.get()

                if (packet.tcpHeader.isFIN) {
                    responseAck = increaseOrWraparound(responseAck, 1)
                }

                this.referencePacket.updateTcpBuffer(
                    buffer, (FIN or ACK).toByte(), responseSeq, responseAck, 0
                )

                if (triggeredByServerEndOfStream) {
                    finSequenceNumberToClient = sequenceNumberToClient.get()
                } else {
                    sequenceNumberToClient.set(increaseOrWraparound(sequenceNumberToClient.get(), 1))
                    finSequenceNumberToClient = sequenceNumberToClient.get()
                }

                Timber.v(
                    "%s - Sending FIN/ACK, response=[seqNum=%d, ackNum=%d] - previous=[seqNum=%d, ackNum =%d, payloadSize=%d]",
                    ipAndPort,
                    responseSeq,
                    responseAck,
                    sequenceNumberToClient.get(),
                    acknowledgementNumberToClient.get(),
                    payloadSize
                )
            }

            queues.networkToDevice.offerFirst(buffer)

            try {
                channel.close()
            } catch (e: Exception) {
                Timber.w(e, "Problem closing socket connection for %s", ipAndPort)
            }
        }

        fun TCB.sendAck(
            queues: VpnQueues,
            packet: Packet
        ) {
            synchronized(this) {
                val payloadSize = packet.tcpPayloadSize(true)

                acknowledgementNumberToClient.set(increaseOrWraparound(packet.tcpHeader.sequenceNumber, payloadSize.toLong()))
                sequenceNumberToClient.set(packet.tcpHeader.acknowledgementNumber)

                if (packet.tcpHeader.isRST || packet.tcpHeader.isSYN || packet.tcpHeader.isFIN) {
                    acknowledgementNumberToClient.set(increaseOrWraparound(acknowledgementNumberToClient.get(), 1))
                    Timber.v(
                        "%s - Sending ACK from network to device. Flags contain RST, SYN or FIN so incremented acknowledge number to %d",
                        ipAndPort,
                        acknowledgementNumberToClient.get()
                    )
                }

                Timber.i(
                    "%s - Sending ACK, payloadSize=%d, seqNumber=%d ackNumber= %d)",
                    ipAndPort,
                    payloadSize,
                    acknowledgementNumberToClient.get(),
                    sequenceNumberToClient.get()
                )

                val buffer = ByteBufferPool.acquire()
                packet.updateTcpBuffer(
                    buffer,
                    (ACK).toByte(),
                    sequenceNumberToClient.get(),
                    acknowledgementNumberToClient.get(),
                    0
                )
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

        fun increaseOrWraparound(
            current: Long,
            increment: Long
        ): Long {
            return (current + increment) % MAX_SEQUENCE_NUMBER
        }

        private val MAX_SEQUENCE_NUMBER = (2.0.pow(32.0) - 1).toLong()
        private const val PERIODIC_STALE_CONNECTION_CLEANUP_PERIOD_MS = 30_000L
    }
}
