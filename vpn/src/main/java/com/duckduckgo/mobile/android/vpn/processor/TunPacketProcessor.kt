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

package com.duckduckgo.mobile.android.vpn.processor

import android.os.ParcelFileDescriptor
import com.duckduckgo.mobile.android.vpn.service.VpnQueues
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel


class TunPacketProcessor(
    private val tunInterface: ParcelFileDescriptor,
    private val queues: VpnQueues
) {

    private var running = false

    fun run() {
        Timber.w("TunPacketProcess started")

        running = true

        // reading from the TUN; this means packets coming from apps on this device
        val vpnInput = FileInputStream(tunInterface.fileDescriptor).channel

        // writing back data to the TUN; this means packet from the network flowing back to apps on the device
        val vpnOutput = FileOutputStream(tunInterface.fileDescriptor).channel

        try {
            while (running) {
                val dataSent = executeReadLoop(vpnInput)
                val dataReceived = executeWriteLoop(vpnOutput)

                if (!dataSent && !dataReceived) {
                    Thread.sleep(10)
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Failed while reading or writing from the TUN")
            running = false
        } finally {
            vpnInput.close()
            vpnOutput.close()
        }
    }

    private fun executeReadLoop(vpnInput: FileChannel): Boolean {
        var bufferToNetwork = byteBuffer()
        var dataSent = true

        if (dataSent) {
            bufferToNetwork = byteBuffer()
        } else {
            bufferToNetwork.clear()
        }

        val inPacketLength = vpnInput.read(bufferToNetwork)
        if (inPacketLength > 0) {
            dataSent = true
            bufferToNetwork.flip()
            val packet = Packet(bufferToNetwork)
            if (packet.isUDP) {
                queues.udpDeviceToNetwork.offer(packet)
            } else if (packet.isTCP) {
                queues.tcpDeviceToNetwork.offer(packet)
            }
        } else {
            dataSent = false
        }
        return dataSent
    }

    private fun byteBuffer(): ByteBuffer {
        return ByteBuffer.allocate(Short.MAX_VALUE.toInt())
    }

    private fun executeWriteLoop(vpnOutput: FileChannel): Boolean {
        val bufferFromNetwork = queues.networkToDevice.poll() ?: return false

        bufferFromNetwork.flip()
        var dataReceived = false
        while (bufferFromNetwork.hasRemaining()) {
            val bytesWrittenToVpn = vpnOutput.write(bufferFromNetwork)
            if (bytesWrittenToVpn > 0) dataReceived = true
        }

        return dataReceived
    }

    fun stop() {
        running = false
        Timber.w("TunPacketProcess stopped")
    }
}