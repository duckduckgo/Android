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
import xyz.hexene.localvpn.ByteBufferPool
import xyz.hexene.localvpn.Packet
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class TunPacketReader(private val tunInterface: ParcelFileDescriptor, private val queues: VpnQueues) : Runnable {

    private var running = false
    var bufferToNetwork = byteBuffer()

    override fun run() {
        Timber.w("TunPacketReader started")

        running = true

        // reading from the TUN; this means packets coming from apps on this device
        val vpnInput = FileInputStream(tunInterface.fileDescriptor).channel

        try {
            while (running && !Thread.interrupted()) {
                try {
                    executeReadLoop(vpnInput)
                } catch (e: Throwable) {
                    Timber.w(e, "Failed while reading from the TUN")
                }
            }

        } catch (e: InterruptedException) {
            Timber.w(e, "Thread interrupted")
            running = false
        } catch (e: Throwable) {
            Timber.e(e, "Fatal error encountered")
            running = false
        } finally {
            vpnInput.close()
        }
    }

    private fun executeReadLoop(vpnInput: FileChannel) {
        bufferToNetwork = byteBuffer()

        val inPacketLength = vpnInput.read(bufferToNetwork)
        if (inPacketLength == 0) {
            ByteBufferPool.release(bufferToNetwork)
            return
        }

        if (bufferToNetwork[0] == 0.toByte()) {
            Timber.i("Control message; ignore this")
            ByteBufferPool.release(bufferToNetwork)
            return
        }

        bufferToNetwork.flip()
        val packet = Packet(bufferToNetwork)
        if (packet.isUDP) {
            queues.udpDeviceToNetwork.offer(packet)
        } else if (packet.isTCP) {
            queues.tcpDeviceToNetwork.offer(packet)
        }
    }

    private fun byteBuffer(): ByteBuffer {
        return ByteBufferPool.acquire()
    }

    fun stop() {
        running = false
        Timber.w("TunPacketReader stopped")
    }
}
