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

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.di.TcpNetworkSelector
import com.duckduckgo.mobile.android.vpn.processor.tcp.ConnectionInitializer.TcpConnectionParams
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpPacketProcessor.PendingWriteData
import com.duckduckgo.mobile.android.vpn.service.VpnMemoryCollectorPlugin
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey.OP_READ
import java.nio.channels.SelectionKey.OP_WRITE
import java.nio.channels.Selector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

interface TcpSocketWriter {
    fun writeToSocket(tcb: TCB)
    fun addToWriteQueue(
        pendingWriteData: PendingWriteData,
        skipQueue: Boolean
    )

    fun removeFromWriteQueue(tcb: TCB)
}

@SingleInstanceIn(VpnScope::class)
@ContributesBinding(
    scope = VpnScope::class,
    boundType = TcpSocketWriter::class
)
class RealTcpSocketWriter @Inject constructor(
    @TcpNetworkSelector private val selector: Selector,
    private val queues: VpnQueues
) : TcpSocketWriter, VpnMemoryCollectorPlugin {

    // added an initial capacity based on what I observed from the low memory pixels we send
    private val writeQueue = ConcurrentHashMap<TCB, Deque<PendingWriteData>>(10)

    override fun addToWriteQueue(
        pendingWriteData: PendingWriteData,
        skipQueue: Boolean
    ) {
        val queue = pendingWriteData.tcb.writeQueue()
        if (skipQueue) queue.addFirst(pendingWriteData) else queue.add(pendingWriteData)
        Timber.v(
            "Added to write queue. Size is now %d for %s. there are %d TCB instances in the write queue",
            queue.size,
            getLogLabel(pendingWriteData.tcb),
            writeQueue.keys.size
        )
    }

    override fun removeFromWriteQueue(tcb: TCB) {
        writeQueue.remove(tcb)
        Timber.v("Removed from write queue: there are %d TCB instances in the write queue", writeQueue.keys.size)
    }

    private fun getLogLabel(tcb: TCB) = "${tcb.requestingAppName}/${tcb.requestingAppPackage} ${tcb.ipAndPort}"

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
            Timber.v(
                "Writing data to socket %s: %d bytes. ack=%d, seq=%d",
                tcb.ipAndPort,
                writeData.payloadSize,
                writeData.ackNumber,
                writeData.seqNumber
            )

            val payloadBuffer = writeData.payloadBuffer
            val payloadSize = writeData.payloadSize
            val socket = writeData.socket
            val connectionParams = writeData.connectionParams

            val bytesWritten = socket.write(payloadBuffer)

            if (payloadBuffer.remaining() == 0) {
                Timber.v("Fully wrote %d bytes for %s", payloadSize, getLogLabel(tcb))
                fullyWritten(tcb, writeData, connectionParams)
            } else {
                Timber.w("Partial write. %d bytes remaining to be written for %s", payloadBuffer.remaining(), getLogLabel(tcb))
                partiallyWritten(writeData, bytesWritten, payloadBuffer, payloadSize)
            }
        } while (writeQueue.isNotEmpty())

        Timber.v("Nothing more to write, switching to read mode")

        selector.wakeup()
        tcb.channel.register(selector, OP_READ, tcb)
    }

    private fun partiallyWritten(
        writeData: PendingWriteData,
        bytesWritten: Int,
        payloadBuffer: ByteBuffer,
        payloadSize: Int
    ) {
        Timber.e("Partially written data. %d bytes written. %d bytes remain out of %d", bytesWritten, payloadBuffer.remaining(), payloadSize)

        addToWriteQueue(writeData, skipQueue = true)

        selector.wakeup()
        writeData.socket.register(selector, OP_WRITE or OP_READ, writeData.tcb)
    }

    private fun fullyWritten(
        tcb: TCB,
        writeData: PendingWriteData,
        connectionParams: TcpConnectionParams
    ) {
        tcb.acknowledgementNumberToClient = writeData.ackNumber
        tcb.acknowledgementNumberToServer = writeData.seqNumber

        val seqToClient = tcb.sequenceNumberToClient
        val ackToServer = tcb.acknowledgementNumberToServer
        val seqAckDiff = seqToClient - ackToServer
        Timber.v(
            "%s - seqToClient=%d, ackToClient=%d, ackToServer=%d, diff=%d",
            writeData.tcb.ipAndPort,
            seqToClient,
            tcb.acknowledgementNumberToClient,
            ackToServer,
            seqAckDiff
        )

        val responseBuffer = connectionParams.responseBuffer

        tcb.referencePacket.updateTcpBuffer(
            responseBuffer,
            Packet.TCPHeader.ACK.toByte(),
            tcb.sequenceNumberToClient,
            tcb.acknowledgementNumberToClient,
            0
        )
        ByteBufferPool.release(writeData.payloadBuffer)
        queues.networkToDevice.offer(responseBuffer)
    }

    override fun collectMemoryMetrics(): Map<String, String> {
        Timber.v("Collecting TCP socket write queue")

        val writeQueueSize = writeQueue.size

        // take this opportunity to remove any TCBs which were evicted from the TCB cache and haven't been cleaned up yet
        removeEvictedTCBs()

        return mutableMapOf<String, String>().apply {
            this["tcpWriteQueueSize"] = writeQueueSize.toString()
        }
    }

    private fun removeEvictedTCBs() {
        val removalList = writeQueue.keys.filter { it.connectionEvicted }
        removalList.forEach { writeQueue.remove(it) }
        Timber.v("Cleaned up evicted TCBs. Removed %d", removalList.size)
    }
}

@Module
@ContributesTo(VpnScope::class)
abstract class TcpSocketWriterModule {
    @Binds
    @IntoSet
    abstract fun bindTcpSocketWriterMemoryCollector(tcpSocketWriter: TcpSocketWriter): VpnMemoryCollectorPlugin
}
