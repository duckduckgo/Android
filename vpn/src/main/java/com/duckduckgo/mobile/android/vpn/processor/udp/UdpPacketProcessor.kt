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

package com.duckduckgo.mobile.android.vpn.processor.udp

import android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
import android.os.Process.setThreadPriority
import android.os.SystemClock
import android.util.LruCache
import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import kotlinx.coroutines.*
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import java.io.IOException
import java.lang.Runnable
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UdpPacketProcessor(private val queues: VpnQueues, private val networkChannelCreator: NetworkChannelCreator) : Runnable{

    private var pollJobDeviceToNetwork: Job? = null
    private var pollJobNetworkToDevice: Job? = null

    val selector: Selector = Selector.open()

    private val channelCache = object : LruCache<String, DatagramChannel>(2) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: DatagramChannel?, newValue: DatagramChannel?) {
            Timber.i("UDP channel cache entry removed: $key. Evicted? $evicted")
            if (evicted) {
                oldValue?.close()
            }
        }
    }

    override fun run() {
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

        channelCache.evictAll()
    }

    private suspend fun pollForDeviceToNetworkWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        try {
            while (pollJobDeviceToNetwork?.isActive == true) {
                kotlin.runCatching {
                    deviceToNetworkProcessing()
                }.onFailure {
                    Timber.w(it, "Failed to process device-to-network packet")
                }
            }
        } finally {
            Timber.i("Evicting all from the channel cache")
            channelCache.evictAll()
        }
    }

    private suspend fun pollForNetworkToDeviceWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJobNetworkToDevice?.isActive == true) {
            kotlin.runCatching {
                networkToDeviceProcessing()
            }.onFailure {
                Timber.w(it, "Failed to process network-to-device packet")
            }
        }
    }

    /**
     * Reads from the device-to-network queue. For any packets in this queue, a new DatagramChannel is created and the packet is written.
     * Instructs the selector we'll be interested in OP_READ for receiving the response to the packet we write.
     */
    private suspend fun deviceToNetworkProcessing() {
        val packet = queues.udpDeviceToNetwork.poll()
        if (packet == null) {
            delay(10)
            return
        }

        val destinationAddress = packet.ip4Header.destinationAddress
        val destinationPort = packet.udpHeader.destinationPort
        val cacheKey = generateCacheKey(packet)

        var channel = channelCache[cacheKey]
        if (channel == null) {
            channel = createChannel(InetSocketAddress(destinationAddress, destinationPort), cacheKey)
            if (channel == null) {
                ByteBufferPool.release(packet.backingBuffer)
                return
            }
        }

        packet.swapSourceAndDestination()

        selector.wakeup()
        channel.register(selector, SelectionKey.OP_READ, packet)

        try {
            val payloadBuffer = packet.backingBuffer ?: return
            while (payloadBuffer.hasRemaining()) {
                val bytesWritten = channel.write(payloadBuffer)
                Timber.v("Wrote %d bytes to network (%s:%d)", bytesWritten, destinationAddress, destinationPort)
            }
        } catch (e: IOException) {
            Timber.w("Network write error")
            channelCache.remove(cacheKey)
        }
    }

    private fun createChannel(destination: InetSocketAddress, cacheKey: String): DatagramChannel? {
        val channel = networkChannelCreator.createDatagramChannel()
        try {
            channel.connect(destination)
        } catch (e: IOException) {
            Timber.w(e, "Failed to connect to UDP ${destination.hostName}:${destination.port}")
            channel.close()
            return null
        }

        channelCache.put(cacheKey, channel)
        return channel
    }

    /**
     * Reads data from the network when the selector tells us it has a readable key.
     * When data is read, we add it to the network-to-device queue, which will result in the packet being written back to the TUN.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun networkToDeviceProcessing() {
        val startTime = SystemClock.uptimeMillis()
        val channelsReady = selector.select(TimeUnit.SECONDS.toMillis(1))

        if (channelsReady == 0) {
            delay(10)
            return
        }

        val iterator = selector.selectedKeys().iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()

            kotlin.runCatching {
                if (key.isValid && key.isReadable) {
                    iterator.remove()

                    Timber.v("Got next network-to-device packet [isReadable] after ${SystemClock.uptimeMillis() - startTime}ms wait")

                    val receiveBuffer = ByteBufferPool.acquire()
                    receiveBuffer.position(Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE)

                    val inputChannel = (key.channel() as DatagramChannel)
                    val readBytes = inputChannel.read(receiveBuffer)
                    Timber.i("Read %d bytes from datagram channel %s", readBytes, inputChannel)

                    val referencePacket = key.attachment() as Packet
                    referencePacket.updateUdpBuffer(receiveBuffer, readBytes)
                    receiveBuffer.position(HEADER_SIZE + readBytes)

                    queues.networkToDevice.offer(receiveBuffer)
                }
            }.onFailure {
                Timber.w(it, "Failure processing selected key for selector")
                key.cancel()
            }
        }
    }

    private fun generateCacheKey(packet: Packet): String {
        return "${packet.ip4Header.destinationAddress}:${packet.udpHeader.destinationPort}:${packet.udpHeader.sourcePort}"
    }

    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE
    }

}