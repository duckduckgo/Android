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

package com.duckduckgo.mobile.android.vpn

import android.os.Looper
import android.os.Process.setThreadPriority
import com.duckduckgo.mobile.android.vpn.data.Packet
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import thirdpartyneedsrewritten.ip.IpDatagram
import timber.log.Timber
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class DeviceToNetworkPacketProcessor(private val datagramChannel: DatagramChannel) {

    private val queue: BlockingQueue<Packet> = LinkedBlockingQueue<Packet>()
    private var pollJob: Job? = null
    private var pollSelectorJob: Job? = null
    lateinit var packetHandler: PacketHandler

    private val SOCKET_BYTEBUFFER_WRITE_SIZE = 1024 * 16
    private val socketBuffer = ByteBuffer.allocateDirect(SOCKET_BYTEBUFFER_WRITE_SIZE)

    val selector: Selector = SelectorProvider.provider().openSelector()
    private var datagramChannelMap: Map<Channel, Packet> = HashMap()

    fun start() {
        Timber.i("Starting DeviceToNetworkPacketProcessor.")

        if (pollJob == null) {
            pollJob = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollNetworkToDevice() }
        }

        if (pollSelectorJob == null) {
            pollSelectorJob = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollSelector() }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun pollNetworkToDevice() {
        setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJob?.isActive == true) {

            kotlin.runCatching {
                val packet = queue.poll(1, TimeUnit.SECONDS)
                if (packet == null) {
                    Timber.v("No packets available %d", System.currentTimeMillis())
                } else {
                    Timber.i("Got a packet. %d packets remain", queue.size)

                    val destination = InetSocketAddress(IpDatagram.readDestinationIP(packet.rawData), IpDatagram.readDestinationPort(packet.rawData))
                    Timber.i("Packet destination: %s:%s %s", destination.address, destination.port, String(packet.rawData.array()))


                    selector.wakeup()
                    val key = datagramChannel.register(selector, SelectionKey.OP_WRITE, packet)
                }
            }.onFailure {
                Timber.w(it, "Failed to process packet")
            }

        }
    }

    private fun pollSelector() {

        while (pollSelectorJob?.isActive == true) {

            try {
                Timber.i("About to select. main thread? %s", Thread.currentThread().id == Looper.getMainLooper().thread.id)
                selector.select()
                Timber.e("Finished selecting")
                val selectedKeys = selector.selectedKeys().iterator()
                while (selectedKeys.hasNext()) {
                    val key = selectedKeys.next()

                    if (key.isValid && key.isReadable) {
                        // read from channel
                        Timber.i("SELECTOR: read from channel")
                    } else if (key.isValid && key.isWritable) {
                        writeToChannel(key)
                        key.cancel()
                    } else if (key.isValid && key.isConnectable) {
                        // initialize connection (TCP only?)
                        Timber.i("SELECTOR: initialize connection")
                    }

                    selectedKeys.remove()
                }

            } catch (e: CancelledKeyException) {
                Timber.w(e, "Key cancelled")
            } catch (e: Exception) {
                Timber.w(e, "General issue encountered while processing selected keys")
            }
        }
    }

    private fun writeToChannel(key: SelectionKey) {
        Timber.i("SELECTOR: write to channel. main thread? %s", Thread.currentThread().id == Looper.getMainLooper().thread.id)

        if (key.channel() is DatagramChannel) {

            Timber.i("Ready to write: %s", key.attachment())

//            val packet = key.attachment() as Packet
//            val destination = InetSocketAddress(IpDatagram.readDestinationIP(packet.rawData), IpDatagram.readDestinationPort(packet.rawData))
//            val packetData: ByteArray = UDPPacket.extractUDPv4Data(packet.rawData.array())
//            Timber.d("Writing to datagram channel to %s, %d bytes", destination, packetData.size)

            // put data in socket buffer somehow


//            datagramChannel.let {
//                it.send(socketBuffer, destination)
//                it.register(selector, SelectionKey.OP_WRITE)
//            }

        }
    }

    fun addPacket(packet: Packet) {
        queue.offer(packet)
    }

}