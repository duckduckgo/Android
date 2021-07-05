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

import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.di.TcpNetworkSelector
import com.duckduckgo.mobile.android.vpn.di.VpnScope
import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.PendingWriteData
import com.duckduckgo.mobile.android.vpn.service.VpnMemoryCollectorPlugin
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey.OP_READ
import java.nio.channels.SelectionKey.OP_WRITE
import java.nio.channels.Selector
import java.util.*
import javax.inject.Inject

interface TcpSocketWriter {
    fun writeToSocket(tcb: TCB)
    fun addToWriteQueue(pendingWriteData: PendingWriteData, skipQueue: Boolean)
}

@VpnScope
@ContributesBinding(
    scope = VpnObjectGraph::class,
    boundType = TcpSocketWriter::class
)
class RealTcpSocketWriter @Inject constructor(
    @TcpNetworkSelector private val selector: Selector,
    private val queues: VpnQueues
) : TcpSocketWriter, VpnMemoryCollectorPlugin {

    // TODO we need to clear this queue out of socket channels sometime
    private val writeQueue = mutableMapOf<TCB, Deque<PendingWriteData>>()

    override fun addToWriteQueue(pendingWriteData: PendingWriteData, skipQueue: Boolean) {
        val queue = pendingWriteData.tcb.writeQueue()
        if (skipQueue) queue.addFirst(pendingWriteData) else queue.add(pendingWriteData)
        Timber.v("Added to write queue. Size is now ${queue.size} for ${getLogLabel(pendingWriteData.tcb)}")
    }

    private fun getLogLabel(tcb: TCB) =
        "${tcb.requestingAppName}/${tcb.requestingAppPackage} ${tcb.ipAndPort}"

    @Synchronized
    private fun TCB.writeQueue(): Deque<PendingWriteData> {
        val existingQueue = writeQueue[this]
        if (existingQueue != null) return existingQueue

        val newQueue = LinkedList<PendingWriteData>()
        writeQueue[this] = newQueue
        return newQueue
    }

    @Synchronized
    override fun writeToSocket(tcb: TCB) {
        val writeQueue = tcb.writeQueue()

        do {
            val writeData = writeQueue.pollFirst() ?: break
            Timber.v("Writing data to socket ${tcb.ipAndPort}: ${writeData.payloadSize} bytes. ack=${writeData.ackNumber}, seq=${writeData.seqNumber}")

            val payloadBuffer = writeData.payloadBuffer
            val payloadSize = writeData.payloadSize
            val socket = writeData.socket
            val connectionParams = writeData.connectionParams

            val bytesWritten = socket.write(payloadBuffer)

            if (payloadBuffer.remaining() == 0) {
                Timber.i("Fully wrote $payloadSize bytes for ${getLogLabel(tcb)}")
                fullyWritten(tcb, writeData, connectionParams)
            } else {
                Timber.w("Partial write. ${payloadBuffer.remaining()} bytes remaining to be written for ${getLogLabel(tcb)}")
                partiallyWritten(writeData, bytesWritten, payloadBuffer, payloadSize)
            }

        } while (writeQueue.isNotEmpty())

        Timber.i("Nothing more to write, switching to read mode")

        selector.wakeup()
        tcb.channel.register(selector, OP_READ, tcb)
    }

    private fun partiallyWritten(writeData: PendingWriteData, bytesWritten: Int, payloadBuffer: ByteBuffer, payloadSize: Int) {
        Timber.e("Hey hey, hey. now what? %d bytes written. %d bytes remain out of %d", bytesWritten, payloadBuffer.remaining(), payloadSize)

        addToWriteQueue(writeData, skipQueue = true)

        selector.wakeup()
        writeData.socket.register(selector, OP_WRITE or OP_READ, writeData.tcb)
    }

    private fun fullyWritten(tcb: TCB, writeData: PendingWriteData, connectionParams: TcpConnectionParams) {
        tcb.acknowledgementNumberToClient = writeData.ackNumber
        tcb.acknowledgementNumberToServer = writeData.seqNumber

        val seqToClient = tcb.sequenceNumberToClient
        val ackToServer = tcb.acknowledgementNumberToServer
        val seqAckDiff = seqToClient - ackToServer
        Timber.i("${writeData.tcb.ipAndPort} - seqToClient=$seqToClient, ackToClient=${tcb.acknowledgementNumberToClient}, ackToServer=$ackToServer, diff=$seqAckDiff")

        val responseBuffer = connectionParams.responseBuffer

        tcb.referencePacket.updateTcpBuffer(
            responseBuffer,
            Packet.TCPHeader.ACK.toByte(),
            tcb.sequenceNumberToClient,
            tcb.acknowledgementNumberToClient,
            0
        )
        queues.networkToDevice.offer(responseBuffer)
    }

    override fun collectMemoryMetrics(): Map<String, String> {
        Timber.v("Collecting TCP socket write queue")

        return mutableMapOf<String, String>().apply {
            this["tcpWriteQueueSize"] = writeQueue.size.toString()
        }
    }
}

@Module
@ContributesTo(VpnObjectGraph::class)
abstract class TcpSocketWriterModule {
    @Binds
    @IntoSet
    abstract fun bindTcpSocketWriterMemoryCollector(tcpSocketWriter: TcpSocketWriter): VpnMemoryCollectorPlugin
}
