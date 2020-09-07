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
import android.os.Looper
import android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
import android.os.Process.setThreadPriority
import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveClientToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveServerToState
import com.duckduckgo.mobile.android.vpn.processor.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import kotlinx.coroutines.Job
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader.ACK
import xyz.hexene.localvpn.Packet.TCPHeader.FIN
import xyz.hexene.localvpn.TCB
import xyz.hexene.localvpn.TCB.TCBStatus.FIN_WAIT_2
import xyz.hexene.localvpn.TCB.TCBStatus.LAST_ACK
import java.nio.ByteBuffer
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors
import kotlin.math.pow

class TcpPacketProcessor(queues: VpnQueues, networkChannelCreator: NetworkChannelCreator, trackerDetector: VpnTrackerDetector) : Runnable {

    val selector: Selector = Selector.open()

    private var pollJobDeviceToNetwork: Job? = null
    private var pollJobNetworkToDevice: Job? = null

    private val handler: Handler = Handler(Looper.getMainLooper())

    private val tcpSocketWriter = TcpSocketWriter(selector)
    private val tcpNetworkToDevice = TcpNetworkToDevice(queues, selector, tcpSocketWriter, handler)
    private val tcpDeviceToNetwork =
        TcpDeviceToNetwork(queues, selector, tcpSocketWriter, TcpConnectionInitializer(queues, networkChannelCreator), handler, trackerDetector)

    private val readWriteExecutorService = Executors.newFixedThreadPool(2)

    override fun run() {
        Timber.i("Starting ${this::class.simpleName}")

//        if (pollJobDeviceToNetwork == null) {
//            pollJobDeviceToNetwork = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollForDeviceToNetworkWork() }
//        }
//
//        if (pollJobNetworkToDevice == null) {
//            pollJobNetworkToDevice = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollForNetworkToDeviceWork() }
//        }

        readWriteExecutorService.submit { pollForDeviceToNetworkWork() }
        readWriteExecutorService.submit { pollForNetworkToDeviceWork() }
    }

    fun stop() {
        Timber.i("Stopping ${this::class.simpleName}")

        pollJobDeviceToNetwork?.cancel()
        pollJobDeviceToNetwork = null

        pollJobNetworkToDevice?.cancel()
        pollJobNetworkToDevice = null
    }

    private fun pollForDeviceToNetworkWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        //while (pollJobDeviceToNetwork?.isActive == true) {
        while (!Thread.interrupted() && !readWriteExecutorService.isShutdown) {
            kotlin.runCatching {
                tcpDeviceToNetwork.deviceToNetworkProcessing()
            }.onFailure {
                Timber.w(it, "Failed to process TCP device-to-network packet")
            }
        }
    }

    private fun pollForNetworkToDeviceWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        //while (pollJobNetworkToDevice?.isActive == true) {
        while (!Thread.interrupted() && !readWriteExecutorService.isShutdown) {
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
        fun logPacketDetails(packet: Packet, initialSeqNumber: Long, sequenceNumber: Long, acknowledgementNumber: Long): String {
            with(packet.tcpHeader) {
                return "\tflags:[ ${isSYN.printFlag("SYN")}${isACK.printFlag("ACK")}${isFIN.printFlag("FIN")}${isPSH.printFlag("PSH")}${isRST.printFlag("RST")}${isURG.printFlag(
                    "URG"
                )}]. [ackNumber=$acknowledgementNumber, sequenceNumber={ ${sequenceNumber - initialSeqNumber} / $sequenceNumber } ]"
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

        private fun TCB.updateStatus(newStatus: TcbState) {
            this.tcbState = newStatus
        }
//
//        fun TCB.sendFinToClient(queues: VpnQueues, packet: Packet, connectionParams: TcpConnectionParams) {
//            synchronized(this) {
//                val buffer = ByteBufferPool.acquire()
//                packet.updateTcpBuffer(buffer, (FIN).toByte(), sequenceNumberToClient, acknowledgementNumberToClient, 0)
//                sequenceNumberToClient = increaseOrWraparound(sequenceNumberToClient, 1)
//                queues.networkToDevice.offer(buffer)
//            }
//        }

        fun TCB.sendFinToClient(queues: VpnQueues, packet: Packet, connectionParams: TcpConnectionParams) {
            val buffer = ByteBufferPool.acquire()
            synchronized(this) {
                val payloadSize = packet.tcpPayloadSize(true)

                // ackNumber == last seqNumber received, plus payload size received, plus 1 because we received FIN
                //acknowledgementNumberToClient = increaseOrWraparound(packet.tcpHeader.sequenceNumber, payloadSize.toLong() + 1)

                Timber.i(
                    "%s - Sending FIN, previous seqNum=%d, payloadSize=%d, ackNum =%d",
                    ipAndPort,
                    sequenceNumberToClient,
                    payloadSize,
                    acknowledgementNumberToClient
                )

                packet.updateTcpBuffer(buffer, (FIN).toByte(), sequenceNumberToClient, acknowledgementNumberToClient, 0)
                sequenceNumberToClient = increaseOrWraparound(sequenceNumberToClient, 1)
                finSequenceNumberToClient = sequenceNumberToClient

                Timber.w("%s - FINSequenceNumberToClient is %d", ipAndPort, finSequenceNumberToClient)
            }
            queues.networkToDevice.offer(buffer)

            // client would normally be in FIN_WAIT_1 until it gets the ACK (FIN_WAIT_2). Safe to jump straight to FIN_WAIT_2
            // server would normally be in CLOSE_WAIT until it sends its FIN. Safe to jump straight to LAST_ACK

            tcbState = TcbState(clientState = FIN_WAIT_2, serverState = LAST_ACK)

            try {
                channel.close()
            } catch (e: Exception) {
                Timber.w(e, "Problem closing socket connection for %s", ipAndPort)
            }
        }

        fun TCB.sendAck(queues: VpnQueues, packet: Packet, connectionParams: TcpConnectionParams) {
            synchronized(this) {
                val payloadSize = packet.tcpPayloadSize(true)

                acknowledgementNumberToClient = increaseOrWraparound(packet.tcpHeader.sequenceNumber, payloadSize.toLong())
                sequenceNumberToClient = packet.tcpHeader.acknowledgementNumber

                if (packet.tcpHeader.isRST || packet.tcpHeader.isSYN || packet.tcpHeader.isFIN) {
                    acknowledgementNumberToClient = increaseOrWraparound(acknowledgementNumberToClient, 1)
                }

                Timber.i(
                    "%s - Sending ACK, previous seqNum=%d, payloadSize=%d, ackNum =%d (ackFromPacket: %d)",
                    ipAndPort,
                    sequenceNumberToClient,
                    payloadSize,
                    acknowledgementNumberToClient,
                    packet.tcpHeader.acknowledgementNumber
                )

                val buffer = ByteBufferPool.acquire()
                packet.updateTcpBuffer(buffer, (ACK).toByte(), sequenceNumberToClient, acknowledgementNumberToClient, 0)
                //sequenceNumberToClient = increaseOrWraparound(sequenceNumberToClient, 1)
                queues.networkToDevice.offer(buffer)
            }
        }

        @Synchronized
        fun TCB.sendResetPacket(queues: VpnQueues, payloadSize: Int) {
            val buffer = ByteBufferPool.acquire()
            synchronized(this) {
                Timber.i(
                    "%s - Sending RST, previous seqNum=%d, payloadSize=%d, ackNum =%d",
                    ipAndPort,
                    sequenceNumberToClient,
                    payloadSize,
                    acknowledgementNumberToClient
                )

                this.referencePacket.updateTcpBuffer(buffer, Packet.TCPHeader.RST.toByte(), 0, this.acknowledgementNumberToClient + payloadSize, 0)
            }
            queues.networkToDevice.offerFirst(buffer)
            TCB.closeTCB(this)
        }

        @Synchronized
        fun TCB.closeConnection(buffer: ByteBuffer) {
            Timber.v("Closing TCB connection $ipAndPort}")
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
            updateStatus(tcbState.copy(serverState = newState.state))
        }

        private fun TCB.updateState(newState: MoveClientToState) {
            Timber.v("Updating client state: ${newState.state}")
            updateStatus(tcbState.copy(clientState = newState.state))
        }

        fun increaseOrWraparound(current: Long, increment: Long): Long {
            return (current + increment) % MAX_SEQUENCE_NUMBER
        }

        private val MAX_SEQUENCE_NUMBER = (2.0.pow(32.0) - 1).toLong()
    }

}