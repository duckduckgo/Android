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

import com.duckduckgo.mobile.android.vpn.processor.packet.connectionInfo
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver.OriginatingApp
import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.closeConnection
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.increaseOrWraparound
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendAck
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendFinToClient
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.sendResetPacket
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.Companion.updateState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.PendingWriteData
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.*
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifierStrategy
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
import xyz.hexene.localvpn.Packet.TCPHeader.*
import xyz.hexene.localvpn.TCB
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

class TcpDeviceToNetwork(
    private val queues: VpnQueues,
    private val selector: Selector,
    private val socketWriter: SocketWriter,
    private val connectionInitializer: ConnectionInitializer,
    private val trackerDetector: VpnTrackerDetector,
    private val packetPersister: PacketPersister,
    private val localAddressDetector: LocalIpAddressDetector,
    private val originatingAppPackageResolver: OriginatingAppPackageIdentifierStrategy,
    private val appNameResolver: AppNameResolver,
    private val vpnCoroutineScope: CoroutineScope
) {

    var lastTimePacketConsumed = 0L
    var totalPacketsConsumed = 0
    var totalTimeToConsumePacket = 0L

    var lastTimeProcessPacketEntered = 0L
    var totalPacketsProcessed = 0
    var totalTimeToProcessPackets = 0L

    /**
     * Reads from the device-to-network queue. For any packets in this queue, a new DatagramChannel is created and the packet is written.
     * Instructs the selector we'll be interested in OP_READ for receiving the response to the packet we write.
     */
    fun deviceToNetworkProcessing() {
        val packet = queues.tcpDeviceToNetwork.take() ?: return
        val startTime = System.nanoTime()
        // measurePacketConsumptionTimes(startTime)

        val destinationAddress = packet.ip4Header.destinationAddress
        val destinationPort = packet.tcpHeader.destinationPort
        val sourcePort = packet.tcpHeader.sourcePort

        val payloadBuffer = packet.backingBuffer ?: return
        packet.backingBuffer = null

        val responseBuffer = ByteBufferPool.acquire()
        val connectionParams = TcpConnectionParams(destinationAddress.hostAddress, destinationPort, sourcePort, packet, responseBuffer)
        val connectionKey = connectionParams.key()

        val totalPacketLength = payloadBuffer.limit()

        packetPersister.persistDataSent(totalPacketLength, PACKET_TYPE_TCP)

        val tcb = TCB.getTCB(connectionKey)
        // Timber.i("Device to network packet; got new response buffer and checked for TCB. Took ${(System.nanoTime() - startTime) / 1_000_000}ms")

        if (tcb == null) {
            processPacketTcbNotInitialized(connectionKey, packet, totalPacketLength, connectionParams)

            // Timber.i("Processed device-to-network packet. New connection. Took ${(System.nanoTime() - startTime) / 1_000_000}ms")
        } else {
            processPacketTcbExists(connectionKey, tcb, packet, totalPacketLength, connectionParams, responseBuffer, payloadBuffer)
            // Timber.i("Processed device-to-network packet. Existing connection. Took ${(System.nanoTime() - startTime) / 1_000_000}ms")
        }

        //        if (responseBuffer.position() == 0) {
        //            ByteBufferPool.release(responseBuffer)
        //        }
        ByteBufferPool.release(payloadBuffer)
    }

    private fun measurePacketConsumptionTimes(startTimeNano: Long) {
        val diff = startTimeNano - lastTimePacketConsumed

        if (lastTimePacketConsumed == 0L) {
            lastTimePacketConsumed = startTimeNano
        } else {
            totalPacketsConsumed++
            totalTimeToConsumePacket += diff

            if (TimeUnit.NANOSECONDS.toSeconds(diff) >= 1) {
                lastTimePacketConsumed = 0L
                totalPacketsConsumed = 0
                totalTimeToConsumePacket = 0
            } else {
                lastTimePacketConsumed = startTimeNano
                if (totalPacketsConsumed % 500 == 0) {
                    Timber.i(
                        "Packets consumed: %d. Average time to consume: %dns",
                        totalPacketsConsumed,
                        totalTimeToConsumePacket / totalPacketsConsumed
                    )
                }
            }
        }
    }

    private fun measurePacketProcessingTimes(startTimeNano: Long) {
        val diff = startTimeNano - lastTimeProcessPacketEntered

        if (lastTimeProcessPacketEntered == 0L) {
            lastTimeProcessPacketEntered = startTimeNano
        } else {
            totalPacketsProcessed++
            totalTimeToProcessPackets += diff

            if (TimeUnit.NANOSECONDS.toSeconds(diff) >= 1) {
                lastTimeProcessPacketEntered = 0L
                totalPacketsProcessed = 0
                totalTimeToProcessPackets = 0
            } else {
                lastTimeProcessPacketEntered = startTimeNano
                if (totalPacketsProcessed % 500 == 0) {
                    Timber.i(
                        "Packets processed: %d. Average time to consume: %dns",
                        totalPacketsProcessed,
                        totalTimeToProcessPackets / totalPacketsProcessed
                    )
                }
            }
        }
    }

    private fun processPacketTcbNotInitialized(connectionKey: String, packet: Packet, totalPacketLength: Int, connectionParams: TcpConnectionParams) {
        Timber.i(
            "New packet. $connectionKey. TCB not initialized. ${
            TcpPacketProcessor.logPacketDetails(
                packet,
                packet.tcpHeader.sequenceNumber,
                packet.tcpHeader.acknowledgementNumber
            )
            }. Packet length: $totalPacketLength.  Data length: ${packet.tcpPayloadSize(true)}"
        )
        TcpStateFlow.newPacket(connectionKey, TcbState(), packet.asPacketType(), -1).events.forEach {
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
                else -> Timber.e("No connection open and won't open one to $connectionKey. Dropping packet. (action=$it)")
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
        Timber.i(
            "New packet. %s. %s. %s. Packet length: %d.  Data length: %d",
            connectionKey, tcb.tcbState,
            TcpPacketProcessor.logPacketDetails(
                packet,
                packet.tcpHeader.sequenceNumber,
                packet.tcpHeader.acknowledgementNumber
            ),
            totalPacketLength, packet.tcpPayloadSize(true)
        )

        if (packet.tcpHeader.isACK) {
            tcb.acknowledgementNumberToServer = packet.tcpHeader.acknowledgementNumber
        }

        val action =
            TcpStateFlow.newPacket(connectionKey, tcb.tcbState, packet.asPacketType(tcb.finSequenceNumberToClient), tcb.sequenceNumberToClientInitial)
        Timber.v("Action: ${action.events} for ${tcb.ipAndPort}")

        action.events.forEach {
            when (it) {
                is MoveState -> tcb.updateState(it)
                ProcessPacket -> processPacket(tcb, packet, payloadBuffer, connectionParams)
                SendFin -> tcb.sendFinToClient(queues, packet, packet.tcpPayloadSize(true), triggeredByServerEndOfStream = false)
                SendFinWithData -> tcb.sendFinToClient(queues, packet, 0, triggeredByServerEndOfStream = false)
                CloseConnection -> tcb.closeConnection(responseBuffer)
                DelayedCloseConnection -> {
                    vpnCoroutineScope.launch {
                        delay(3_000)
                        tcb.closeConnection(responseBuffer)
                    }
                }
                SendAck -> tcb.sendAck(queues, packet)
                else -> Timber.e("Unknown event for how to process device-to-network packet: ${action.events}")
            }

        }
    }

    private fun openConnection(params: TcpConnectionParams) {
        Timber.v("Opening connection to %s:%s", params.destinationAddress, params.destinationPort)
        val (tcb, channel) = connectionInitializer.initializeConnection(params) ?: return
        TcpStateFlow.socketOpening(TcbState()).events.forEach {
            when (it) {
                is MoveState -> tcb.updateState(it)
                SendSynAck -> {
                    Timber.v("Channel finished connecting to ${tcb.ipAndPort}")
                    synchronized(tcb) {
                        params.packet.updateTcpBuffer(
                            params.responseBuffer,
                            (SYN or ACK).toByte(),
                            tcb.sequenceNumberToClient,
                            tcb.acknowledgementNumberToClient,
                            0
                        )
                        tcb.sequenceNumberToClient++
                        queues.networkToDevice.offer(params.responseBuffer)
                    }
                }
                WaitToConnect -> {
                    Timber.v("Not finished connecting yet to ${tcb.selectionKey}, will register for OP_CONNECT event")
                    selector.wakeup()
                    tcb.selectionKey = channel.register(selector, SelectionKey.OP_CONNECT, tcb)
                }
                else -> Timber.w("Unexpected action: $it")
            }
        }
    }

    private fun processPacket(tcb: TCB, packet: Packet, payloadBuffer: ByteBuffer, connectionParams: TcpConnectionParams) {
        val entryTime = System.nanoTime()
        synchronized(tcb) {
            val payloadSize = payloadBuffer.limit() - payloadBuffer.position()
            if (payloadSize == 0) {
                Timber.v(" ${tcb.ipAndPort} Payload Size is 0. There's nothing to Process")
                return
            }

            val isLocalAddress = determineIfLocalIpAddress(packet)
            val requestingApp = determineRequestingApp(tcb, packet)
            val requestType = determineIfTracker(tcb, packet, requestingApp, payloadBuffer, isLocalAddress)
            Timber.i("App $requestingApp attempting to send $payloadSize bytes to ${tcb.ipAndPort}. $requestType")

            if (requestType is RequestTrackerType.Tracker) {
                // TODO - validate the best option here: send RESET, FIN or DROP packet?
                tcb.sendResetPacket(queues, packet, payloadSize)
                return
            }

            if (!tcb.waitingForNetworkData) {
                Timber.v("Not waiting for network data ${tcb.ipAndPort}; register for OP_READ and wait for network data")
                Timber.i(
                    "Registering for OP_READ. Took: %d",
                    measureNanoTime {
                        selector.wakeup()
                        tcb.selectionKey.interestOps(SelectionKey.OP_READ)
                        tcb.waitingForNetworkData = true
                    }
                )
            }

            try {
                val seqNumber = packet.tcpHeader.acknowledgementNumber
                var ackNumber = increaseOrWraparound(packet.tcpHeader.sequenceNumber, payloadSize.toLong())
                if (packet.tcpHeader.isFIN || packet.tcpHeader.isRST || packet.tcpHeader.isSYN) {
                    ackNumber = increaseOrWraparound(ackNumber, 1)
                }
                tcb.acknowledgementNumberToClient = ackNumber

                socketWriter.writeToSocket(PendingWriteData(payloadBuffer, tcb.channel, payloadSize, tcb, connectionParams, ackNumber, seqNumber))
            } catch (e: IOException) {
                val bytesUnwritten = payloadBuffer.remaining()
                val bytesWritten = payloadSize - bytesUnwritten
                Timber.w(e, "Network write error for ${tcb.ipAndPort}. Wrote $bytesWritten; $bytesUnwritten unwritten")
                tcb.sendResetPacket(queues, packet, bytesWritten)
                return
            }
        }
        measurePacketProcessingTimes(entryTime)
        queues.networkToDevice.offer(connectionParams.responseBuffer)
    }

    private fun determineIfTracker(tcb: TCB, packet: Packet, requestingApp: OriginatingApp, payloadBuffer: ByteBuffer, isLocalAddress: Boolean): RequestTrackerType {
        if (tcb.trackerTypeDetermined) {
            return if (tcb.isTracker) (RequestTrackerType.Tracker(tcb.trackerHostName)) else RequestTrackerType.NotTracker(
                tcb.hostName ?: packet.ip4Header.destinationAddress.hostName
            )
        }

        return trackerDetector.determinePacketType(tcb, packet, payloadBuffer, isLocalAddress, requestingApp)
    }

    private fun determineIfLocalIpAddress(packet: Packet): Boolean {
        return localAddressDetector.isLocalAddress(packet.ip4Header.destinationAddress)
    }

    private fun determineRequestingApp(tcb: TCB, packet: Packet): OriginatingApp {
        if (tcb.requestingAppDetermined) {
            return OriginatingApp(tcb.requestingAppPackage ?: "unknown package", tcb.requestingAppName ?: "unknown app")
        }

        val packageId = originatingAppPackageResolver.resolvePackageId(packet.connectionInfo())

        tcb.requestingAppDetermined = true
        tcb.requestingAppPackage = packageId
        tcb.requestingAppName = appNameResolver.getAppNameForPackageId(packageId).appName

        return OriginatingApp(packageId, tcb.requestingAppName)
    }

}
