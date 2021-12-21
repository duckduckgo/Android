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
import com.duckduckgo.mobile.android.vpn.health.TracedState
import com.duckduckgo.mobile.android.vpn.health.TracerEvent
import com.duckduckgo.mobile.android.vpn.health.TracerPacketRegister
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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SelectionKey.OP_READ
import java.nio.channels.SelectionKey.OP_WRITE
import java.nio.channels.Selector
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
    private val tracerRegister: TracerPacketRegister,
    private val healthMetricCounter: HealthMetricCounter
) {

    /**
     * Reads from the device-to-network queue. For any packets in this queue, a new DatagramChannel
     * is created and the packet is written. Instructs the selector we'll be interested in OP_READ
     * for receiving the response to the packet we write.
     */
    fun deviceToNetworkProcessing() {
        val packet = queues.tcpDeviceToNetwork.take() ?: return

        if (packet.isTracer) {
            processTracerPacker(packet)
            return
        }

        healthMetricCounter.onReadFromDeviceToNetworkQueue()

        val destinationAddress = packet.ip4Header.destinationAddress
        val destinationPort = packet.tcpHeader.destinationPort
        val sourcePort = packet.tcpHeader.sourcePort

        val payloadBuffer = packet.backingBuffer ?: return
        packet.backingBuffer = null

        val responseBuffer = ByteBufferPool.acquire()
        val connectionParams =
            TcpConnectionParams(
                destinationAddress.hostAddress, destinationPort, sourcePort, packet, responseBuffer)
        val connectionKey = connectionParams.key()

        val totalPacketLength = payloadBuffer.limit()

        packetPersister.persistDataSent(totalPacketLength, PACKET_TYPE_TCP)

        val tcb = TCB.getTCB(connectionKey)

        if (tcb == null) {
            processPacketTcbNotInitialized(
                connectionKey, packet, totalPacketLength, connectionParams)
        } else {
            processPacketTcbExists(
                connectionKey,
                tcb,
                packet,
                totalPacketLength,
                connectionParams,
                responseBuffer,
                payloadBuffer)
        }
    }

    private fun processTracerPacker(packet: Packet) {
        val tracerId = packet.tracerId
        tracerRegister.logTracerPacketEvent(
            TracerEvent(tracerId, TracedState.REMOVED_FROM_DEVICE_TO_NETWORK_QUEUE))

        val idLength = tracerId.length
        val idBytes = tracerId.toByteArray()

        val byteBuffer = ByteBufferPool.acquire()
        byteBuffer.put(-1)
        byteBuffer.putInt(idLength)
        byteBuffer.put(idBytes)

        tracerRegister.logTracerPacketEvent(
            TracerEvent(tracerId, TracedState.ADDED_TO_NETWORK_TO_DEVICE_QUEUE))
        queues.networkToDevice.offer(byteBuffer)
    }

    private fun processPacketTcbNotInitialized(
        connectionKey: String,
        packet: Packet,
        totalPacketLength: Int,
        connectionParams: TcpConnectionParams
    ) {
        Timber.v(
            "New packet. %s. TCB not initialized. %s. Packet length: %d.  Data length: %d",
            connectionKey,
            TcpPacketProcessor.logPacketDetails(
                packet, packet.tcpHeader.sequenceNumber, packet.tcpHeader.acknowledgementNumber),
            totalPacketLength,
            packet.tcpPayloadSize(true))
        TcpStateFlow.newPacket(connectionKey, TcbState(), packet.asPacketType(), -1)
            .events
            .forEach {
                when (it) {
                    OpenConnection -> openConnection(connectionParams)
                    SendAck -> {
                        synchronized(connectionParams.responseBuffer) {
                            connectionParams.packet.updateTcpBuffer(
                                connectionParams.responseBuffer,
                                ACK.toByte(),
                                0,
                                connectionParams.packet.tcpHeader.sequenceNumber + 1,
                                0)
                            queues.networkToDevice.offer(connectionParams.responseBuffer)
                        }
                    }
                    SendReset -> {
                        synchronized(connectionParams.responseBuffer) {
                            connectionParams.packet.updateTcpBuffer(
                                connectionParams.responseBuffer,
                                RST.toByte(),
                                0,
                                connectionParams.packet.tcpHeader.sequenceNumber + 1,
                                0)
                            queues.networkToDevice.offer(connectionParams.responseBuffer)
                        }
                    }
                    else ->
                        Timber.e(
                            "No connection open and won't open one to %s. Dropping packet. (action=%s)",
                            connectionKey,
                            it)
                }
            }
    }

    private fun processPacketTcbExists(
        connectionKey: String,
        tcb: TCB,
        packet: Packet,
        totalPacketLength: Int,
        connectionParams: TcpConnectionParams,
        responseBuffer: ByteBuffer,
        payloadBuffer: ByteBuffer
    ) {
        Timber.v(
            "New packet. %s. %s. %s. Packet length: %d.  Data length: %d",
            connectionKey,
            tcb.tcbState,
            TcpPacketProcessor.logPacketDetails(
                packet, packet.tcpHeader.sequenceNumber, packet.tcpHeader.acknowledgementNumber),
            totalPacketLength,
            packet.tcpPayloadSize(true))

        if (packet.tcpHeader.isACK) {
            tcb.acknowledgementNumberToServer = packet.tcpHeader.acknowledgementNumber
        }

        val action =
            TcpStateFlow.newPacket(
                connectionKey,
                tcb.tcbState,
                packet.asPacketType(tcb.finSequenceNumberToClient),
                tcb.sequenceNumberToClientInitial)
        Timber.v("Action: %s for %s", action.events, tcb.ipAndPort)

        action.events.forEach {
            when (it) {
                is MoveState -> tcb.updateState(it)
                ProcessPacket -> processPacket(tcb, packet, payloadBuffer, connectionParams)
                SendFin ->
                    tcb.sendFinToClient(
                        queues,
                        packet,
                        packet.tcpPayloadSize(true),
                        triggeredByServerEndOfStream = false)
                SendFinWithData ->
                    tcb.sendFinToClient(queues, packet, 0, triggeredByServerEndOfStream = false)
                CloseConnection -> closeConnection(tcb, responseBuffer)
                SendReset ->
                    tcbCloser.sendResetPacket(tcb, queues, packet, packet.tcpPayloadSize(true))
                DelayedCloseConnection -> {
                    vpnCoroutineScope.launch {
                        delay(3_000)
                        closeConnection(tcb, responseBuffer)
                    }
                }
                SendAck -> tcb.sendAck(queues, packet)
                else ->
                    Timber.e(
                        "Unknown event for how to process device-to-network packet: %s",
                        action.events)
            }
        }
    }

    private fun closeConnection(tcb: TCB, responseBuffer: ByteBuffer) {
        tcbCloser.closeConnection(tcb)
        ByteBufferPool.release(responseBuffer)
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
                            tcb.sequenceNumberToClient,
                            tcb.acknowledgementNumberToClient,
                            0)
                        tcb.sequenceNumberToClient++
                        queues.networkToDevice.offer(params.responseBuffer)
                    }
                }
                WaitToConnect -> {
                    Timber.v(
                        "Not finished connecting yet to %s, will register for OP_CONNECT event",
                        tcb.selectionKey)
                    selector.wakeup()
                    tcb.selectionKey = channel.register(selector, SelectionKey.OP_CONNECT, tcb)
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
        val recentTrackerEvent =
            recentAppTrackerCache.getRecentTrackingAttempt(
                requestingApp.packageId, hostName, payloadSize)
                ?: return false
        val timeSinceTrackerRequested = System.currentTimeMillis() - recentTrackerEvent.timestamp
        Timber.v(
            "Tracker %s was last sent by %s %dms ago",
            hostName,
            requestingApp.packageId,
            timeSinceTrackerRequested)
        return true
    }

    private fun processPacket(
        tcb: TCB,
        packet: Packet,
        payloadBuffer: ByteBuffer,
        connectionParams: TcpConnectionParams
    ) {
        synchronized(tcb) {
            val payloadSize = payloadBuffer.limit() - payloadBuffer.position()
            if (payloadSize == 0) {
                Timber.v(" %s Payload Size is 0. There's nothing to Process", tcb.ipAndPort)
                return
            }

            val isLocalAddress = determineIfLocalIpAddress(packet)
            val requestingApp = determineRequestingApp(tcb, packet)
            val hostName = determineHostName(tcb, packet, payloadBuffer)
            val requestType =
                determineIfTracker(tcb, packet, requestingApp, payloadBuffer, isLocalAddress)
            val isATrackerRetryRequest =
                isARetryForRecentlyBlockedTracker(requestingApp, hostName, payloadSize)
            Timber.v(
                "App %s attempting to send %d bytes to (%s). %s host=%s, localAddress=%s, retry=%s",
                requestingApp,
                payloadSize,
                tcb.ipAndPort,
                requestType,
                hostName,
                isLocalAddress,
                isATrackerRetryRequest)

            if (requestType is RequestTrackerType.Tracker) {
                processTrackingRequestPacket(
                    isATrackerRetryRequest, tcb, requestingApp, packet, payloadSize)
                return
            }

            if (!tcb.waitingForNetworkData) {
                Timber.v("Register for OP_READ and wait for network data. %s.", tcb.ipAndPort)
                selector.wakeup()
                tcb.selectionKey.interestOps(OP_READ)
                tcb.waitingForNetworkData = true
            }

            try {
                val seqNumber = packet.tcpHeader.acknowledgementNumber
                var ackNumber =
                    increaseOrWraparound(packet.tcpHeader.sequenceNumber, payloadSize.toLong())
                if (packet.tcpHeader.isFIN || packet.tcpHeader.isRST || packet.tcpHeader.isSYN) {
                    ackNumber = increaseOrWraparound(ackNumber, 1)
                }
                tcb.acknowledgementNumberToClient = ackNumber

                selector.wakeup()
                tcb.channel.register(selector, OP_WRITE, tcb)

                val writeData =
                    PendingWriteData(
                        payloadBuffer,
                        tcb.channel,
                        payloadSize,
                        tcb,
                        connectionParams,
                        ackNumber,
                        seqNumber)
                socketWriter.addToWriteQueue(writeData, false)
            } catch (e: IOException) {
                val bytesUnwritten = payloadBuffer.remaining()
                val bytesWritten = payloadSize - bytesUnwritten
                Timber.w(
                    e,
                    "Network write error for %s. Wrote %d; %d unwritten",
                    tcb.ipAndPort,
                    bytesWritten,
                    bytesUnwritten)
                tcbCloser.sendResetPacket(tcb, queues, packet, bytesWritten)
                return
            }
        }
    }

    private fun processTrackingRequestPacket(
        isATrackerRetryRequest: Boolean,
        tcb: TCB,
        requestingApp: OriginatingApp,
        packet: Packet,
        payloadSize: Int
    ) {
        if (isATrackerRetryRequest) {
            tcb.enterGhostingMode()
            processPacketInGhostingMode(tcb)
        } else {
            Timber.i(
                "Blocking tracker request. [%s] ---> [%s] %s",
                tcb.requestingAppPackage,
                tcb.trackerHostName,
                tcb.ipAndPort)

            recentAppTrackerCache.addTrackerForApp(
                requestingApp.packageId, tcb.hostName, tcb.ipAndPort, payloadSize)
            tcbCloser.sendResetPacket(tcb, queues, packet, payloadSize)
        }
    }

    private fun processPacketInGhostingMode(tcb: TCB) {
        val ghostingDuration =
            (System.currentTimeMillis() - tcb.getGhostingStartTime()).coerceAtLeast(0)

        Timber.v(
            "Blocking tracker request (dropping packet). [%s] ---> [%s] %s. Ghosting for %sms",
            tcb.requestingAppPackage,
            tcb.trackerHostName,
            tcb.ipAndPort,
            ghostingDuration)
    }

    private fun determineHostName(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer): String? {
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
            return if (tcb.isTracker) (RequestTrackerType.Tracker(tcb.trackerHostName))
            else
                RequestTrackerType.NotTracker(
                    tcb.hostName ?: packet.ip4Header.destinationAddress.hostName)
        }

        return trackerDetector.determinePacketType(
            tcb, packet, payloadBuffer, isLocalAddress, requestingApp)
    }

    private fun determineIfLocalIpAddress(packet: Packet): Boolean {
        return localAddressDetector.isLocalAddress(packet.ip4Header.destinationAddress)
    }

    private fun determineRequestingApp(tcb: TCB, packet: Packet): OriginatingApp {
        if (tcb.requestingAppDetermined) {
            return OriginatingApp(
                tcb.requestingAppPackage ?: "unknown package",
                tcb.requestingAppName ?: "unknown app")
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
