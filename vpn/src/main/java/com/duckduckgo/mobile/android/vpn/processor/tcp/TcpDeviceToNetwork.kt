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

import com.duckduckgo.mobile.android.vpn.health.HealthMetricCounter
import com.duckduckgo.mobile.android.vpn.processor.packet.connectionInfo
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver.OriginatingApp
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifierStrategy
import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.increaseOrWraparound
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendAck
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendFinToClient
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.updateState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.PendingWriteData
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.CloseConnection
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.DelayedCloseConnection
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.OpenConnection
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.ProcessPacket
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.SendAck
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.SendFin
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.SendFinWithData
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.SendReset
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.SendSynAck
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.WaitToConnect
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.PayloadBytesExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.LocalIpAddressDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.store.PACKET_TYPE_TCP
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader.ACK
import xyz.hexene.localvpn.Packet.TCPHeader.RST
import xyz.hexene.localvpn.Packet.TCPHeader.SYN
import xyz.hexene.localvpn.TCB
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.SelectionKey.OP_WRITE
import java.nio.channels.Selector

class TcpDeviceToNetwork(
    private val queues: VpnQueues,
    private val selector: Selector,
    private val socketWriter: TcpSocketWriter,
    private val connectionInitializer: ConnectionInitializer,
    private val trackerDetector: VpnTrackerDetector,
    private val packetPersister: PacketPersister,
    private val localAddressDetector: LocalIpAddressDetector,
    private val originatingAppPackageResolver: OriginatingAppPackageIdentifierStrategy,
    private val appNameResolver: AppNameResolver,
    private val tcbCloser: TCBCloser,
    private val hostnameExtractor: HostnameExtractor,
    private val payloadBytesExtractor: PayloadBytesExtractor,
    private val recentAppTrackerCache: RecentAppTrackerCache,
    private val vpnCoroutineScope: CoroutineScope,
    private val healthMetricCounter: HealthMetricCounter
) : Runnable {

    override fun run() {
        while (!Thread.interrupted()) {
            try {
                deviceToNetworkProcessing()
            } catch (e: IOException) {
                Timber.w(e, "Failed to process TCP device-to-network packet")
            } catch (e: CancelledKeyException) {
                Timber.w(e, "Failed to process TCP device-to-network packet")
            } catch (e: InterruptedException) {
                Timber.w(e, "Thread is interrupted")
                return
            }
        }
    }

    /**
     * Reads from the device-to-network queue. For any packets in this queue, a new DatagramChannel is created and the packet is written.
     * Instructs the selector we'll be interested in OP_READ for receiving the response to the packet we write.
     */
    private fun deviceToNetworkProcessing() {
        val packet = queues.tcpDeviceToNetwork.take() ?: return

        healthMetricCounter.onReadFromDeviceToNetworkQueue()

        val destinationAddress = packet.ipHeader.destinationAddress
        val destinationPort = packet.tcpHeader.destinationPort
        val sourcePort = packet.tcpHeader.sourcePort

        if (packet.backingBuffer == null) return

        val connectionParams = TcpConnectionParams(destinationAddress.hostAddress, destinationPort, sourcePort, packet, ByteBufferPool.acquire())

        packetPersister.persistDataSent(packet.backingBuffer.limit(), PACKET_TYPE_TCP)

        val tcb = TCB.getTCB(connectionParams.key())

        if (tcb == null) {
            processPacketTcbNotInitialized(connectionParams)
        } else {
            processPacketTcbExists(connectionParams)
        }
    }

    private fun processPacketTcbNotInitialized(
        connectionParams: TcpConnectionParams
    ) {
        val packet = connectionParams.packet
        val totalPacketLength = connectionParams.packet.backingBuffer.limit()

        Timber.v(
            "New packet. %s. TCB not initialized. %s. Packet length: %d.  Data length: %d",
            connectionParams.key(),
            TcpPacketProcessor.logPacketDetails(
                packet,
                packet.tcpHeader.sequenceNumber,
                packet.tcpHeader.acknowledgementNumber
            ),
            totalPacketLength,
            packet.tcpPayloadSize(true)
        )
        TcpStateFlow.newPacket(connectionParams.key(), TcbState(), packet.asPacketType(), -1).events.forEach {
            when (it) {
                OpenConnection -> openConnection(connectionParams)
                SendAck -> {
                    synchronized(connectionParams.responseBuffer) {
                        connectionParams.packet.updateTcpBuffer(
                            connectionParams.responseBuffer, ACK.toByte(), 0, connectionParams.packet.tcpHeader.sequenceNumber + 1, 0
                        )
                        queues.networkToDevice.offer(connectionParams.responseBuffer)
                    }
                }
                SendReset -> {
                    synchronized(connectionParams.responseBuffer) {
                        connectionParams.packet.updateTcpBuffer(
                            connectionParams.responseBuffer, RST.toByte(), 0, connectionParams.packet.tcpHeader.sequenceNumber + 1, 0
                        )
                        queues.networkToDevice.offer(connectionParams.responseBuffer)
                    }
                }
                else -> Timber.e("No connection open and won't open one to %s. Dropping packet. (action=%s)", connectionParams.key(), it)
            }
        }
    }

    private fun processPacketTcbExists(
        connectionParams: TcpConnectionParams,
    ) {
        val packet = connectionParams.packet
        val payloadBuffer = connectionParams.packet.backingBuffer
        val totalPacketLength = payloadBuffer.limit()
        // TCB should always exist here
        val tcb = connectionParams.tcbOrClose() ?: return

        Timber.v(
            "New packet. %s. %s. %s. Packet length: %d.  Data length: %d",
            connectionParams.key(), tcb.tcbState,
            TcpPacketProcessor.logPacketDetails(
                packet,
                packet.tcpHeader.sequenceNumber,
                packet.tcpHeader.acknowledgementNumber
            ),
            totalPacketLength, packet.tcpPayloadSize(true)
        )

        if (packet.tcpHeader.isACK) {
            tcb.acknowledgementNumberToServer.set(packet.tcpHeader.acknowledgementNumber)
        }

        val action = TcpStateFlow.newPacket(
            connectionParams.key(), tcb.tcbState, packet.asPacketType(tcb.finSequenceNumberToClient), tcb.sequenceNumberToClientInitial.get()
        )
        Timber.v("Action: %s for %s", action.events, tcb.ipAndPort)
        Timber.v("payloadBuffer size: %s", payloadBuffer)

        action.events.forEach {
            when (it) {
                is MoveState -> tcb.updateState(it)
                ProcessPacket -> processPacket(connectionParams)
                SendFin -> tcb.sendFinToClient(queues, packet, packet.tcpPayloadSize(true), triggeredByServerEndOfStream = false)
                SendFinWithData -> tcb.sendFinToClient(queues, packet, 0, triggeredByServerEndOfStream = false)
                CloseConnection -> closeConnection(connectionParams)
                SendReset -> tcbCloser.sendResetPacket(connectionParams, queues, packet.tcpPayloadSize(true), packet.tcpHeader.isFIN)
                DelayedCloseConnection -> {
                    vpnCoroutineScope.launch {
                        delay(3_000)
                        closeConnection(connectionParams)
                    }
                }
                SendAck -> tcb.sendAck(queues, packet)
                else -> Timber.e("Unknown event for how to process device-to-network packet: %s", action.events)
            }

        }
    }

    private fun closeConnection(
        connectionParams: TcpConnectionParams,
    ) {
        TCB.getTCB(connectionParams.key())?.let {
            tcbCloser.closeConnection(it)
        }
        connectionParams.close()
    }

    private fun openConnection(params: TcpConnectionParams) {
        Timber.v("Opening connection to %s:%s", params.destinationAddress, params.destinationPort)
        val (tcb, channel) = connectionInitializer.initializeConnection(params) ?: return
        TcpStateFlow.socketOpening(TcbState()).events.forEach {
            when (it) {
                is MoveState -> tcb.updateState(it)
                SendSynAck -> {
                    Timber.v("Channel finished connecting to %s", tcb.ipAndPort)
                    synchronized(tcb) {
                        params.packet.updateTcpBuffer(
                            params.responseBuffer,
                            (SYN or ACK).toByte(),
                            tcb.sequenceNumberToClient.get(),
                            tcb.acknowledgementNumberToClient.get(),
                            0
                        )
                        tcb.sequenceNumberToClient.incrementAndGet()
                        queues.networkToDevice.offer(params.responseBuffer)
                    }
                }
                WaitToConnect -> {
                    Timber.v("Not finished connecting yet to %s, will register for OP_CONNECT event", tcb.ipAndPort)
                    synchronized(queues.selectorQueue) {
                        queues.selectorQueue.offerLast(TcpSelectorOp(SelectionKey.OP_CONNECT, tcb))
                    }
                    selector.wakeup()
                }
                else -> Timber.w("Unexpected action: %s", it)
            }
        }
    }

    private fun isARetryForRecentlyBlockedTracker(
        requestingApp: OriginatingApp,
        hostName: String?,
        payloadSize: Int
    ): Boolean {
        if (hostName == null) return false
        val recentTrackerEvent = recentAppTrackerCache.getRecentTrackingAttempt(requestingApp.packageId, hostName, payloadSize) ?: return false
        val timeSinceTrackerRequested = System.currentTimeMillis() - recentTrackerEvent.timestamp
        Timber.v("Tracker %s was last sent by %s %dms ago", hostName, requestingApp.packageId, timeSinceTrackerRequested)
        return true
    }

    private fun processPacket(
        connectionParams: TcpConnectionParams
    ) {
        val packet = connectionParams.packet
        val payloadBuffer = packet.backingBuffer
        // we should never get here with null TCB
        val tcb = connectionParams.tcbOrClose() ?: return

        synchronized(tcb) {
            val payloadSize = payloadBuffer.limit() - payloadBuffer.position()
            if (payloadSize == 0) {
                Timber.v(" %s Payload Size is 0. There's nothing to Process", tcb.ipAndPort)
                ByteBufferPool.release(payloadBuffer)
                return
            }

            val isLocalAddress = determineIfLocalIpAddress(packet)
            val requestingApp = determineRequestingApp(tcb, packet)
            val hostName = determineHostName(tcb, packet, payloadBuffer)
            val requestType = determineIfTracker(tcb, packet, requestingApp, payloadBuffer, isLocalAddress)
            val isATrackerRetryRequest = isARetryForRecentlyBlockedTracker(requestingApp, hostName, payloadSize)
            Timber.v(
                "App %s attempting to send %d bytes to (%s). %s host=%s, localAddress=%s, retry=%s",
                requestingApp,
                payloadSize,
                tcb.ipAndPort,
                requestType,
                hostName,
                isLocalAddress,
                isATrackerRetryRequest
            )

            if (requestType is RequestTrackerType.Tracker) {
                processTrackingRequestPacket(
                    isATrackerRetryRequest,
                    connectionParams,
                    requestingApp,
                    packet,
                    payloadSize
                )
                ByteBufferPool.release(payloadBuffer)
                return
            }

            try {
                val seqNumber = packet.tcpHeader.acknowledgementNumber
                var ackNumber = increaseOrWraparound(packet.tcpHeader.sequenceNumber, payloadSize.toLong())
                if (packet.tcpHeader.isFIN || packet.tcpHeader.isRST || packet.tcpHeader.isSYN) {
                    ackNumber = increaseOrWraparound(ackNumber, 1)
                }
                tcb.acknowledgementNumberToClient.set(ackNumber)

                val writeData = PendingWriteData(payloadBuffer, tcb.channel, payloadSize, tcb, connectionParams, ackNumber, seqNumber)
                socketWriter.addToWriteQueue(writeData, false)

                synchronized(queues.selectorQueue) {
                    queues.selectorQueue.offerLast(TcpSelectorOp(OP_WRITE, tcb))
                }
                selector.wakeup()
            } catch (e: IOException) {
                val bytesUnwritten = payloadBuffer.remaining()
                val bytesWritten = payloadSize - bytesUnwritten
                Timber.w(e, "Network write error for %s. Wrote %d; %d unwritten", tcb.ipAndPort, bytesWritten, bytesUnwritten)
                tcbCloser.sendResetPacket(connectionParams, queues, bytesWritten, packet.tcpHeader.isFIN)
                return
            }
        }
    }

    private fun processTrackingRequestPacket(
        isATrackerRetryRequest: Boolean,
        connectionParams: TcpConnectionParams,
        requestingApp: OriginatingApp,
        packet: Packet,
        payloadSize: Int
    ) {
        // we should never get here with null TCB
        val tcb = connectionParams.tcbOrClose() ?: return
        if (isATrackerRetryRequest) {
            tcb.enterGhostingMode()
            processPacketInGhostingMode(tcb)
        } else {
            Timber.i("Blocking tracker request. [%s] ---> [%s] %s", tcb.requestingAppPackage, tcb.trackerHostName, tcb.ipAndPort)

            recentAppTrackerCache.addTrackerForApp(requestingApp.packageId, tcb.hostName, tcb.ipAndPort, payloadSize)
            tcbCloser.sendResetPacket(connectionParams, queues, payloadSize, packet.tcpHeader.isFIN)
        }
    }

    private fun processPacketInGhostingMode(tcb: TCB) {
        val ghostingDuration = (System.currentTimeMillis() - tcb.getGhostingStartTime()).coerceAtLeast(0)

        Timber.v(
            "Blocking tracker request (dropping packet). [%s] ---> [%s] %s. Ghosting for %sms",
            tcb.requestingAppPackage,
            tcb.trackerHostName,
            tcb.ipAndPort,
            ghostingDuration
        )
    }

    private fun determineHostName(
        tcb: TCB,
        packet: Packet,
        payloadBuffer: ByteBuffer
    ): String? {
        if (tcb.hostName != null) return tcb.hostName
        val payloadBytes = payloadBytesExtractor.extract(packet, payloadBuffer)
        return hostnameExtractor.extract(tcb, payloadBytes)
    }

    private fun determineIfTracker(
        tcb: TCB,
        packet: Packet,
        requestingApp: OriginatingApp,
        payloadBuffer: ByteBuffer,
        isLocalAddress: Boolean
    ): RequestTrackerType {
        Timber.v("Determining if a tracker. Already determined? %s", tcb.trackerTypeDetermined)
        if (tcb.trackerTypeDetermined) {
            return if (tcb.isTracker) (RequestTrackerType.Tracker(tcb.trackerHostName)) else RequestTrackerType.NotTracker(
                tcb.hostName ?: packet.ipHeader.destinationAddress.hostName
            )
        }

        return trackerDetector.determinePacketType(tcb, packet, payloadBuffer, isLocalAddress, requestingApp)
    }

    private fun determineIfLocalIpAddress(packet: Packet): Boolean {
        return localAddressDetector.isLocalAddress(packet.ipHeader.destinationAddress)
    }

    private fun determineRequestingApp(
        tcb: TCB,
        packet: Packet
    ): OriginatingApp {
        if (tcb.requestingAppDetermined) {
            return OriginatingApp(tcb.requestingAppPackage ?: "unknown package", tcb.requestingAppName ?: "unknown app")
        }

        val packageId = originatingAppPackageResolver.resolvePackageId(packet.connectionInfo())

        tcb.requestingAppDetermined = true
        tcb.requestingAppPackage = packageId
        tcb.requestingAppName = appNameResolver.getAppNameForPackageId(packageId).appName

        return OriginatingApp(packageId, tcb.requestingAppName)
    }

    private fun TCB.getGhostingStartTime(): Long {
        val currentTimestamp = System.currentTimeMillis()
        val originalGhostingTimestamp = stopRespondingTime

        if (originalGhostingTimestamp != null) {
            return originalGhostingTimestamp
        }
        return currentTimestamp.also { stopRespondingTime = it }
    }

    fun cleanupStaleConnections() {
        recentAppTrackerCache.cleanupStaleConnections()
    }

    private fun TCB.enterGhostingMode() {
        if (stopRespondingTime == null) stopRespondingTime = System.currentTimeMillis()
    }
}
