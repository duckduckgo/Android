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

import android.os.Process.setThreadPriority
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import thirdpartyneedsrewritten.hexene.localvpn.HexenePacket
import thirdpartyneedsrewritten.ip.IpDatagram
import timber.log.Timber
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DeviceToNetworkPacketProcessor(
    private val datagramChannelCreator: DatagramChannelCreator,
    private val queues: VpnQueues
) {

    private var pollJob: Job? = null

    val selector: Selector = Selector.open()

    fun start() {
        Timber.i("Starting DeviceToNetworkPacketProcessor.")

        if (pollJob == null) {
            pollJob = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) { pollDeviceToNetwork() }
        }

    }

    fun stop() {
        Timber.i("Stopping DeviceToNetworkPacketProcessor.")
        pollJob?.cancel()
        pollJob = null
    }

    private fun pollDeviceToNetwork() {
        setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

        while (pollJob?.isActive == true) {

            kotlin.runCatching {
                run {
                    val packet = queues.deviceToNetwork.poll(1, TimeUnit.SECONDS)
                    if (packet == null) {
                        Timber.v("No packets available %d", System.currentTimeMillis())
                    } else {
                        Timber.v("Got a packet. %d packets remain", queues.deviceToNetwork.size)

                        //val destination = InetSocketAddress(IpDatagram.readDestinationIP(packet.rawData), IpDatagram.readDestinationPort(packet.rawData))
                        //Timber.i("Packet destination: %s:%s ", destination.address, destination.port)

                        val outputChannel = datagramChannelCreator.createDatagram()
                        val destinationAddress = packet.ip4Header.destinationAddress
                        val destinationPort = packet.udpHeader.destinationPort
                        outputChannel.connect(InetSocketAddress(destinationAddress, destinationPort))

                        packet.swapSourceAndDestination()

                        selector.wakeup()
                        outputChannel.register(selector, SelectionKey.OP_READ, packet)

                        val payloadBuffer = packet.backingBuffer
                        while (payloadBuffer.hasRemaining()) {
                            val bytesWritten = outputChannel.write(payloadBuffer)
                            Timber.v("Wrote %d bytes to network (%s:%s)", bytesWritten, destinationAddress, destinationPort)
                        }
                    }
                }

                networkToDeviceProcessing()

            }.onFailure {
                Timber.w(it, "Failed to process packet")
            }
        }
    }

    private fun networkToDeviceProcessing() {
        val selectedKeys = selector.selectNow()
        Timber.d("%d selected keys", selectedKeys)
        val iterator = selector.selectedKeys().iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()

            kotlin.runCatching {
                if (key.isValid && key.isReadable) {
                    iterator.remove()

                    val receiveBuffer = ByteBuffer.allocate(Short.MAX_VALUE.toInt())
                    receiveBuffer.position(HexenePacket.IP4_HEADER_SIZE + HexenePacket.UDP_HEADER_SIZE)

                    val inputChannel = key.channel() as DatagramChannel
                    val readBytes = inputChannel.read(receiveBuffer)
                    Timber.i("Read %d bytes from datagram channel %s", readBytes, String(receiveBuffer.array()))

                    val referencePacket = key.attachment() as HexenePacket
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
        private const val HEADER_SIZE = HexenePacket.IP4_HEADER_SIZE + HexenePacket.UDP_HEADER_SIZE
    }

//    fun addPacket(packet: Packet) {
//        queue.offer(packet)
//    }

}