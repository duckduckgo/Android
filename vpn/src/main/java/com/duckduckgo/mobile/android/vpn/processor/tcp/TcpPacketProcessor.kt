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
import android.os.SystemClock
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.Packet.TCPHeader
import xyz.hexene.localvpn.TCB
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SelectionKey.OP_READ
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.Executors

class TcpPacketProcessor(private val queues: VpnQueues, networkChannelCreator: NetworkChannelCreator) {

    val selector: Selector = Selector.open()

    private var pollJobDeviceToNetwork: Job? = null
    private var pollJobNetworkToDevice: Job? = null

    private val connectionInitializer = TcpConnectionInitializer(queues, selector, networkChannelCreator)


    fun start() {
        Timber.i("Starting ${this::class.simpleName}")

        if (pollJobDeviceToNetwork == null) {
            pollJobDeviceToNetwork = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollForDeviceToNetworkWork() }
        }

        if (pollJobNetworkToDevice == null) {
            pollJobNetworkToDevice = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollForNetworkToDeviceWork() }
        }

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

        while (pollJobDeviceToNetwork?.isActive == true) {
            kotlin.runCatching {
                deviceToNetworkProcessing()
            }.onFailure {
                Timber.w(it, "Failed to process TCP device-to-network packet")
            }
        }
    }

    private fun pollForNetworkToDeviceWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJobNetworkToDevice?.isActive == true) {
            kotlin.runCatching {
                networkToDeviceProcessing()
            }.onFailure {
                Timber.w(it, "Failed to process TCP network-to-device packet")
            }
        }
    }

    /**
     * Reads data from the network when the selector tells us it has a readable key.
     * When data is read, we add it to the network-to-device queue, which will result in the packet being written back to the TUN.
     */
    private fun networkToDeviceProcessing() {
        Timber.v("Waiting for next network-to-device packet")
        val startTime = SystemClock.uptimeMillis()
        val channelsReady = selector.select()

        if (channelsReady == 0) {
            Timber.v("Selector woken up but no channels ready; sleeping again")
            Thread.sleep(100)
            return
        }

        Timber.v("Got next network-to-device packet after ${SystemClock.uptimeMillis() - startTime}ms wait")

        val iterator = selector.selectedKeys().iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()

            kotlin.runCatching {
                if (key.isValid && key.isReadable) {
                    iterator.remove()
                    processRead(key)
                } else if (key.isValid && key.isConnectable) {
                    processConnect(key, iterator)
                }
            }.onFailure {
                Timber.w(it, "Failure processing selected key for selector")
                key.cancel()
            }
        }
    }

    /**
     * Reads from the device-to-network queue. For any packets in this queue, a new DatagramChannel is created and the packet is written.
     * Instructs the selector we'll be interested in OP_READ for receiving the response to the packet we write.
     */
    private fun deviceToNetworkProcessing() {
        Timber.v("Waiting for next device-to-network packet")
        val startTime = SystemClock.uptimeMillis()
        val packet = queues.tcpDeviceToNetwork.take()
        Timber.v("Got next device-to-network packet after ${SystemClock.uptimeMillis() - startTime}ms wait")

        val destinationAddress = packet.ip4Header.destinationAddress
        val destinationPort = packet.tcpHeader.destinationPort
        val sourcePort = packet.tcpHeader.sourcePort

        val payloadBuffer = packet.backingBuffer
        packet.backingBuffer = null

        val responseBuffer = ByteBufferPool.acquire()
        val connectionParams = TcpConnectionParams(destinationAddress.hostAddress, destinationPort, sourcePort, packet, responseBuffer)
        val connectionKey = connectionParams.key()

        val tcpHeader = packet.tcpHeader
        val isSyn = tcpHeader.isSYN
        val isAck = tcpHeader.isACK
        val isFin = tcpHeader.isFIN
        val isPsh = tcpHeader.isPSH
        val isRst = tcpHeader.isRST
        val isUrg = tcpHeader.isURG

        Timber.i("Device-to-network TCP packet, to be sent to the internet. destination [address: ${destinationAddress.hostAddress}, port: $destinationPort], source port: $sourcePort, connection key: $connectionKey. packet type:\tsyn=$isSyn,\tack=$isAck,\tfin=$isFin,\tpsh=$isPsh,\trst=$isRst,\turg=$isUrg.")

        val tcb = TCB.getTCB(connectionKey)
        if (tcb == null) {
            Timber.i("Need to initialize TCP connection for $connectionKey")
            connectionInitializer.initializeConnection(connectionParams)
        } else {
            when {
                tcpHeader.isSYN -> processDuplicateSyn(tcb, connectionParams)
                tcpHeader.isRST -> closeConnection(tcb, responseBuffer)
                tcpHeader.isFIN -> processFin(tcb, connectionParams)
                tcpHeader.isACK -> processAck(tcb, payloadBuffer, connectionParams)
                else -> Timber.w("TCP packet has no known flags; dropping")
            }
        }

        if (responseBuffer.position() == 0) {
            ByteBufferPool.release(responseBuffer)
        }
        ByteBufferPool.release(payloadBuffer)
    }

    private fun processAck(tcb: TCB, payloadBuffer: ByteBuffer, connectionParams: TcpConnectionParams) {
        Timber.v("Processing ACK packet")

        val payloadSize = payloadBuffer.limit() - payloadBuffer.position()
        synchronized(tcb) {
            val socket = tcb.channel as SocketChannel
            if (tcb.status == TCB.TCBStatus.SYN_RECEIVED) {
                tcb.status = TCB.TCBStatus.ESTABLISHED

                selector.wakeup()
                tcb.selectionKey = socket.register(selector, OP_READ, tcb)
                tcb.waitingForNetworkData = true
            } else if (tcb.status == TCB.TCBStatus.LAST_ACK) {
                closeConnection(tcb, connectionParams.responseBuffer)
                return
            }

            if (payloadSize == 0) return

            if (!tcb.waitingForNetworkData) {
                selector.wakeup()
                tcb.selectionKey.interestOps(OP_READ)
                tcb.waitingForNetworkData = true
            }

            try {
                while (payloadBuffer.hasRemaining()) {
                    socket.write(payloadBuffer)
                }
            } catch (e: IOException) {
                Timber.w(e, "Network write error")
                sendResetPacket(tcb, payloadSize, connectionParams.responseBuffer)
                return
            }

            tcb.myAcknowledgementNum = connectionParams.packet.tcpHeader.sequenceNumber + payloadSize
            tcb.theirAcknowledgementNum = connectionParams.packet.tcpHeader.acknowledgementNumber
            tcb.referencePacket.updateTCPBuffer(connectionParams.responseBuffer, TCPHeader.ACK.toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
        }


        queues.networkToDevice.offer(connectionParams.responseBuffer)
    }

    private fun processFin(tcb: TCB, connectionParams: TcpConnectionParams) {
        Timber.v("Processing FIN packet")
        val packet = tcb.referencePacket
        tcb.myAcknowledgementNum = connectionParams.packet.tcpHeader.sequenceNumber + 1
        tcb.theirAcknowledgementNum = connectionParams.packet.tcpHeader.acknowledgementNumber

        if (tcb.waitingForNetworkData) {
            tcb.status = TCB.TCBStatus.CLOSE_WAIT
            packet.updateTCPBuffer(connectionParams.responseBuffer, TCPHeader.ACK.toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
        } else {
            tcb.status = TCB.TCBStatus.LAST_ACK
            packet.updateTCPBuffer(connectionParams.responseBuffer, (TCPHeader.FIN or TCPHeader.ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
            tcb.mySequenceNum++
        }
        queues.networkToDevice.offer(connectionParams.responseBuffer)
    }

    private fun processDuplicateSyn(tcb: TCB, params: TcpConnectionParams) {
        Timber.v("Processing duplicate SYN")

        synchronized(tcb) {
            if (tcb.status == TCB.TCBStatus.SYN_SENT) {
                tcb.myAcknowledgementNum = params.packet.tcpHeader.sequenceNumber + 1
                return
            }
        }

        sendResetPacket(tcb, 1, params.responseBuffer)
    }

    private fun sendResetPacket(tcb: TCB, previousPayLoadSize: Int, responseBuffer: ByteBuffer) {
        tcb.referencePacket.updateTCPBuffer(responseBuffer, TCPHeader.RST.toByte(), 0, tcb.myAcknowledgementNum + previousPayLoadSize, 0)
        queues.networkToDevice.offer(responseBuffer)
        TCB.closeTCB(tcb)
    }

    private fun processRead(key: SelectionKey) {
        val receiveBuffer = ByteBufferPool.acquire()
        receiveBuffer.position(HEADER_SIZE)

        val tcb = key.attachment() as TCB

        synchronized(tcb) {
            val packet = tcb.referencePacket
            val destinationAddress = packet.ip4Header.destinationAddress
            val destinationPort = packet.tcpHeader.destinationPort
            val sourcePort = packet.tcpHeader.sourcePort
            val sourceAddress = packet.ip4Header.sourceAddress

            Timber.i("Network-to-device TCP packet, to be sent to TUN. [destination address: ${destinationAddress.hostAddress}, port: $destinationPort], [source address: $sourceAddress, port: $sourcePort], connection key: ${tcb.ipAndPort}")

            val channel = key.channel() as SocketChannel
            try {
                val readBytes = channel.read(receiveBuffer)

                if (endOfStream(readBytes)) {
                    key.interestOps(0)
                    if (tcb.status != TCB.TCBStatus.CLOSE_WAIT) {
                        ByteBufferPool.release(receiveBuffer)
                        return
                    }

                    tcb.status = TCB.TCBStatus.LAST_ACK
                    packet.updateTCPBuffer(receiveBuffer, TCPHeader.FIN.toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
                    tcb.mySequenceNum++
                } else {
                    Timber.v("Read TCP packet from network. $readBytes bytes from ${packet.ip4Header.destinationAddress}")
                    packet.updateTCPBuffer(receiveBuffer, (TCPHeader.PSH or TCPHeader.ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, readBytes)
                    tcb.mySequenceNum += readBytes
                    receiveBuffer.position(HEADER_SIZE + readBytes)
                }
            } catch (e: IOException) {
                Timber.w(e, "Network read error")
                packet.updateTCPBuffer(receiveBuffer, TCPHeader.RST.toByte(), 0, tcb.myAcknowledgementNum, 0)
                queues.networkToDevice.offer(receiveBuffer)
                TCB.closeTCB(tcb)
                return
            }
            queues.networkToDevice.offer(receiveBuffer)
        }

    }

    private fun endOfStream(readBytes: Int) = readBytes == -1

    private fun processConnect(key: SelectionKey, iterator: MutableIterator<SelectionKey>) {
        val tcb = key.attachment() as TCB
        val packet = tcb.referencePacket
        runCatching {
            if (tcb.channel.finishConnect()) {
                Timber.i("Finished connecting to ${packet.ip4Header.sourceAddress}")

                iterator.remove()
                tcb.status = TCB.TCBStatus.SYN_RECEIVED

                val responseBuffer = ByteBufferPool.acquire()
                packet.updateTCPBuffer(responseBuffer, (TCPHeader.SYN or TCPHeader.ACK).toByte(), tcb.mySequenceNum, tcb.myAcknowledgementNum, 0)
                queues.networkToDevice.offer(responseBuffer)
                tcb.mySequenceNum++
                key.interestOps(OP_READ)
            } else {
                Timber.v("Not finished connecting yet")
            }
        }.onFailure {
            Timber.w(it, "Failed to process TCP connect")
            val responseBuffer = ByteBufferPool.acquire()
            packet.updateTCPBuffer(responseBuffer, TCPHeader.RST.toByte(), 0, tcb.myAcknowledgementNum, 0)
            queues.networkToDevice.offer(responseBuffer)
            TCB.closeTCB(tcb)
        }
    }

    private fun closeConnection(tcb: TCB?, buffer: ByteBuffer) {
        Timber.v("Closing TCB connection")
        ByteBufferPool.release(buffer)
        TCB.closeTCB(tcb)
    }

    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.TCP_HEADER_SIZE
    }

}