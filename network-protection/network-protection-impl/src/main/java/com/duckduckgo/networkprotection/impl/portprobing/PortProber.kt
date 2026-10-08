/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.portprobing

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnSocketProtector
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

interface PortProber {
    /**
     * Probes multiple UDP ports in parallel to find a reachable one
     * @param serverIp the server IP address to probe
     * @param ports the list of ports to probe
     * @return the first port that responds, or null if none respond
     */
    suspend fun probePortsInParallel(serverIp: String, ports: List<Long>): Long?
}

@ContributesBinding(VpnScope::class)
class RealPortProber @Inject constructor(
    private val socketProtector: VpnSocketProtector,
) : PortProber {

    override suspend fun probePortsInParallel(serverIp: String, ports: List<Long>): Long? = coroutineScope {
        if (ports.isEmpty()) {
            logcat { "Port probe: no ports to probe" }
            return@coroutineScope null
        }

        logcat { "Port probe: probing ports $ports on $serverIp" }

        val probeJobs = ports.map { port ->
            async {
                val result = probePort(serverIp, port.toInt())
                if (result) {
                    logcat { "Port probe: port $port responded" }
                } else {
                    logcat { "Port probe: port $port did not respond" }
                }
                port to result
            }
        }

        withTimeoutOrNull(PROBE_TIMEOUT_MS.milliseconds) {
            probeJobs.joinAll()
        }

        val results = probeJobs.mapNotNull { job ->
            if (job.isCompleted && !job.isCancelled) {
                job.await()
            } else {
                null
            }
        }

        probeJobs.forEach { if (it.isActive) it.cancel() }

        val successfulPort = results.firstOrNull { it.second }?.first

        if (successfulPort == null) {
            logcat { "Port probe: no ports responded within timeout" }
        }

        successfulPort
    }

    private suspend fun probePort(serverIp: String, port: Int): Boolean {
        var socket: DatagramSocket? = null
        try {
            socket = withContext(Dispatchers.IO) {
                DatagramSocket().apply {
                    soTimeout = RECEIVE_TIMEOUT_MS.toInt()
                    // Protect the socket so it bypasses the VPN
                    val protected = socketProtector.protect(this)
                    logcat { "Port probe: socket protection ${if (protected) "succeeded" else "FAILED"} for port $port" }
                }
            }

            val serverAddress = InetSocketAddress(serverIp, port)
            val probeMessage = PROBE_MESSAGE.toByteArray()
            val sendPacket = DatagramPacket(probeMessage, probeMessage.size, serverAddress)

            val receiveBuffer = ByteArray(RECEIVE_BUFFER_SIZE)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            repeat(RETRANSMIT_COUNT) { attempt ->
                socket.send(sendPacket)

                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    if (response == EXPECTED_RESPONSE) {
                        return true
                    } else {
                        logcat { "Port probe: unexpected response from $serverIp:$port: $response" }
                    }
                } catch (e: Exception) {
                    if (attempt < RETRANSMIT_COUNT - 1) {
                        delay(RETRANSMIT_DELAY_MS.milliseconds)
                    }
                }
            }

            return false
        } catch (e: Exception) {
            logcat { "Port probe: error probing $serverIp:$port: ${e.message}" }
            return false
        } finally {
            socket?.close()
        }
    }

    companion object {
        private const val PROBE_MESSAGE = "DDGPROBE"
        private const val EXPECTED_RESPONSE = "DDG"
        private const val PROBE_TIMEOUT_MS = 1500L
        private const val RETRANSMIT_DELAY_MS = 500L
        private const val RETRANSMIT_COUNT = 3
        private const val RECEIVE_TIMEOUT_MS = 500L
        private const val RECEIVE_BUFFER_SIZE = 64
    }
}
