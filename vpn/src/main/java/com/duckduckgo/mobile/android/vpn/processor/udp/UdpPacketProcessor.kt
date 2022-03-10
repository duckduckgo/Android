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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.mobile.android.vpn.health.HealthMetricCounter
import com.duckduckgo.mobile.android.vpn.processor.packet.connectionInfo
import com.duckduckgo.mobile.android.vpn.processor.packet.totalHeaderSize
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver.OriginatingApp
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.OriginatingAppPackageIdentifierStrategy
import com.duckduckgo.mobile.android.vpn.service.NetworkChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import com.duckduckgo.mobile.android.vpn.store.PACKET_TYPE_UDP
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import timber.log.Timber
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UdpPacketProcessor @AssistedInject constructor(
    private val queues: VpnQueues,
    @Assisted private val networkChannelCreator: NetworkChannelCreator,
    private val packetPersister: PacketPersister,
    private val originatingAppPackageResolver: OriginatingAppPackageIdentifierStrategy,
    private val appNameResolver: AppNameResolver,
    private val channelCache: UdpChannelCache,
    private val healthMetricCounter: HealthMetricCounter,
    private val appBuildConfig: AppBuildConfig,
) : Runnable {

    @AssistedFactory
    interface Factory {
        fun build(networkChannelCreator: NetworkChannelCreator): UdpPacketProcessor
    }

    private var pollJobDeviceToNetwork: Job? = null
    private var pollJobNetworkToDevice: Job? = null

    val selector: Selector = Selector.open()

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

    private fun pollForDeviceToNetworkWork() {
        setThreadPriority(THREAD_PRIORITY_URGENT_DISPLAY)

        try {
            while (pollJobDeviceToNetwork?.isActive == true) {
                try {
                    deviceToNetworkProcessing()
                } catch (e: IOException) {
                    Timber.w(e, "Failed to process UDP device-to-network packet")
                } catch (e: CancelledKeyException) {
                    Timber.w(e, "Failed to process UDP device-to-network packet")
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
            try {
                networkToDeviceProcessing()
            } catch (e: IOException) {
                Timber.w(e, "Failed to process UDP network-to-device packet")
            } catch (e: CancelledKeyException) {
                Timber.w(e, "Failed to process UDP network-to-device packet")
            }
        }
    }

    /**
     * Reads from the device-to-network queue. For any packets in this queue, a new DatagramChannel is created and the packet is written.
     * Instructs the selector we'll be interested in OP_READ for receiving the response to the packet we write.
     */
    private fun deviceToNetworkProcessing() {
        val packet = queues.udpDeviceToNetwork.take() ?: return

        healthMetricCounter.onReadFromDeviceToNetworkQueue(isUdp = true)

        val destinationAddress = packet.ipHeader.destinationAddress
        val destinationPort = packet.udpHeader.destinationPort
        val cacheKey = generateCacheKey(packet)
        val connectionInfo = packet.connectionInfo()

        var channelDetails = channelCache[cacheKey]
        if (channelDetails == null) {
            val channel = createChannel(InetSocketAddress(destinationAddress, destinationPort))

            if (channel == null) {
                ByteBufferPool.release(packet.backingBuffer)
                return
            }

            // only resolve app name/packageId in debug builds to speed up UDP processing
            val (packageId, appName) = if (appBuildConfig.isDebug) {
                val appPackage = originatingAppPackageResolver.resolvePackageId(connectionInfo)
                appPackage to appNameResolver.getAppNameForPackageId(appPackage).appName
            } else {
                "some.package.id" to "some.app.name"
            }
            channelDetails = ChannelDetails(channel, OriginatingApp(packageId, appName))
            channelCache.put(cacheKey, channelDetails)
        }

        packet.swapSourceAndDestination()

        selector.wakeup()
        channelDetails.datagramChannel.register(selector, SelectionKey.OP_READ, packet)

        try {
            val payloadBuffer = packet.backingBuffer ?: return

            Timber.d(
                "App ${channelDetails.originatingApp} attempting to send ${packet.backingBuffer?.remaining()} " +
                    "bytes to ${connectionInfo.destinationAddress}"
            )

            while (payloadBuffer.hasRemaining()) {
                val bytesWritten = channelDetails.datagramChannel.write(payloadBuffer)
                Timber.v("UDP packet. Sent $bytesWritten bytes to $cacheKey")

                packetPersister.persistDataSent(bytesWritten, PACKET_TYPE_UDP)
            }
        } catch (e: Exception) {
            when (e) {
                is IOException, is IllegalArgumentException -> {
                    Timber.w("Network write error writing to $cacheKey")
                    channelCache.remove(cacheKey)
                }
                else -> throw e
            }
        }
    }

    private fun createChannel(destination: InetSocketAddress): DatagramChannel? {
        val channel = networkChannelCreator.createDatagramChannel()
        try {
            channel.connect(destination)
        } catch (e: IOException) {
            Timber.w(e, "Failed to connect to UDP ${destination.hostName}:${destination.port}")
            channel.close()
            return null
        }

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

            var receiveBufferRef: ByteBuffer? = null

            kotlin.runCatching {
                if (key.isValid && key.isReadable) {
                    iterator.remove()

                    Timber.v("Got next network-to-device packet [isReadable] after ${SystemClock.uptimeMillis() - startTime}ms wait")

                    val receiveBuffer = ByteBufferPool.acquire()
                    receiveBufferRef = receiveBuffer
                    val referencePacket = key.attachment() as Packet
                    receiveBuffer.position(referencePacket.totalHeaderSize())

                    val inputChannel = (key.channel() as DatagramChannel)
                    val readBytes = inputChannel.read(receiveBuffer)
                    packetPersister.persistDataReceived(readBytes, PACKET_TYPE_UDP)
                    referencePacket.updateUdpBuffer(receiveBuffer, readBytes)
                    receiveBuffer.position(referencePacket.totalHeaderSize() + readBytes)

                    queues.networkToDevice.offer(receiveBuffer)
                }
            }.onFailure {
                Timber.w(it, "Failure processing selected key for selector")
                key.cancel()
                receiveBufferRef?.let { buff -> ByteBufferPool.release(buff) }
            }
        }
    }

    private fun generateCacheKey(packet: Packet): String {
        return "${packet.ipHeader.destinationAddress}:${packet.udpHeader.destinationPort}:${packet.udpHeader.sourcePort}"
    }

    data class ChannelDetails(
        val datagramChannel: DatagramChannel,
        val originatingApp: OriginatingApp
    )
}
