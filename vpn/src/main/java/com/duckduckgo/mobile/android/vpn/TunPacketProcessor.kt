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

import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer


class TunPacketProcessor(private val tunInterface: ParcelFileDescriptor,
                         private val packetHandler: PacketHandler) {

    private val packet = ByteBuffer.allocate(Short.MAX_VALUE.toInt())
    private var running = false

    fun run() {
        Timber.w("TunPacketProcess started")

        running = true

        // reading from the TUN; this means packets coming from apps on this device
        val inStream = FileInputStream(tunInterface.fileDescriptor)

        // communicating with servers outside of the device
        val outStream = FileOutputStream(tunInterface.fileDescriptor)

        while (running) {
            try {
                val inPacketLength = inStream.read(packet.array())
                if (inPacketLength > 0) {
                    packet.limit(inPacketLength)
                    packetHandler.handleDeviceToNetworkPacket(packet)
                    packet.clear()
                }

            } catch (e: Exception) {
                Timber.w(e, "Failed polling for VPN data")
                running = false
            }

        }

    }
}