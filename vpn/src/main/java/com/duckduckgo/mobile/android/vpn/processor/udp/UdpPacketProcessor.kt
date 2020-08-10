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
import com.duckduckgo.mobile.android.vpn.service.DatagramChannelCreator
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.Executors

class UdpPacketProcessor(
    private val datagramChannelCreator: DatagramChannelCreator,
    private val queues: VpnQueues
) {

    private var pollJobDeviceToNetwork: Job? = null
    private var pollJobNetworkToDevice: Job? = null

    val selector: Selector = Selector.open()


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
                Timber.w(it, "Failed to process device-to-network packet")
            }
        }
    }

    private fun pollForNetworkToDeviceWork() {
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
    private fun deviceToNetworkProcessing() {
        Timber.v("Waiting for next device-to-network packet")
        val startTime = SystemClock.uptimeMillis()
        val packet = queues.udpDeviceToNetwork.take()
        Timber.v("Got next device-to-network packet after ${SystemClock.uptimeMillis()-startTime}ms wait")
        if (packet == null) {
            Timber.v("No packets available %d", System.currentTimeMillis())
        } else {
            val channel = datagramChannelCreator.createDatagram()
            val destinationAddress = packet.ip4Header.destinationAddress
            val destinationPort = packet.udpHeader.destinationPort
            channel.connect(InetSocketAddress(destinationAddress, destinationPort))

            packet.swapSourceAndDestination()

            selector.wakeup()
            channel.register(selector, SelectionKey.OP_READ, packet)

            val payloadBuffer = packet.backingBuffer
            while (payloadBuffer.hasRemaining()) {
                val bytesWritten = channel.write(payloadBuffer)
                Timber.v("Wrote %d bytes to network (%s:%d)", bytesWritten, destinationAddress, destinationPort)
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
        Timber.v("Selected channels: $channelsReady")

        if (channelsReady == 0) {
            Timber.v("Selector woken up but no channels ready; sleeping again")
            Thread.sleep(10)
            return
        }

        Timber.v("Got next network-to-device packet after ${SystemClock.uptimeMillis()-startTime}ms wait")

        val iterator = selector.selectedKeys().iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()

            kotlin.runCatching {
                if (key.isValid && key.isReadable) {
                    iterator.remove()

                    val receiveBuffer = ByteBuffer.allocate(Short.MAX_VALUE.toInt())
                    receiveBuffer.position(Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE)

                    val inputChannel = (key.channel() as DatagramChannel)
                    val readBytes = inputChannel.read(receiveBuffer)
                    Timber.i("Read %d bytes from datagram channel %s %s", readBytes, inputChannel, String(receiveBuffer.array()))

                    key.cancel()
                    inputChannel.close()

                    val referencePacket = key.attachment() as Packet
                    referencePacket.updateUDPBuffer(receiveBuffer, readBytes)
                    receiveBuffer.position(HEADER_SIZE + readBytes)

                    queues.networkToDevice.offer(receiveBuffer)
                }
            }.onFailure {
                Timber.w(it, "Failure processing selected key for selector")
                key.cancel()
            }
        }
    }

    companion object {
        private const val HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE
    }

}