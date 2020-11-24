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
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

class TunPacketWriter(private val tunInterface: ParcelFileDescriptor, private val queues: VpnQueues) : Runnable {

    private var running = false

    override fun run() {
        Timber.w("TunPacketWriter started")

        running = true

        // writing back data to the TUN; this means packet from the network flowing back to apps on the device
        val vpnOutput = FileOutputStream(tunInterface.fileDescriptor).channel

        vpnOutput.use { vpnOutput ->
            while (running && !Thread.interrupted()) {
                try {
                    executeWriteLoop(vpnOutput)
                } catch (e: InterruptedException) {
                    Timber.w(e, "Thread interrupted")
                    running = false
                } catch (e: Throwable) {
                    Timber.w(e, "Failed while writing to TUN")
                }
            }
        }
    }

    private fun executeWriteLoop(vpnOutput: FileChannel) {
        try {
            val bufferFromNetwork = queues.networkToDevice.take() ?: return
            bufferFromNetwork.flip()

            while (bufferFromNetwork.hasRemaining()) {
                val bytesWrittenToVpn = vpnOutput.write(bufferFromNetwork)
                if (bytesWrittenToVpn == 0) {
                    Timber.w("Failed to write any bytes to TUN")
                } else {
                    Timber.v("Wrote %d bytes to TUN", bytesWrittenToVpn)
                }
            }

            ByteBufferPool.release(bufferFromNetwork)
        } catch (e: IOException) {
            Timber.w(e, "Failed writing to the TUN")
        }
    }

    fun stop() {
        running = false
        Timber.w("TunPacketWriter stopped")
    }
}
