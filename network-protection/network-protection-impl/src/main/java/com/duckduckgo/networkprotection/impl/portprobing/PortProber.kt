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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnSocketProtector
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

interface PortProber {
    /**
     * Probes multiple UDP ports in parallel
     * @param serverIp the server IP address to probe
     * @param ports the list of ports to probe
     * @param earlyExitPorts probing stops as soon as all of these ports have responded; empty to wait for every port
     * @return the ports that responded, in the order of [ports]
     */
    suspend fun probePortsInParallel(serverIp: String, ports: List<Long>, earlyExitPorts: Set<Long>): List<Long>
}

@ContributesBinding(VpnScope::class)
class RealPortProber @Inject constructor(
    private val socketProtector: VpnSocketProtector,
    private val dispatcherProvider: DispatcherProvider,
) : PortProber {

    override suspend fun probePortsInParallel(serverIp: String, ports: List<Long>, earlyExitPorts: Set<Long>): List<Long> {
        if (ports.isEmpty()) {
            logcat { "Port probe: no ports to probe" }
            return emptyList()
        }

        logcat { "Port probe: probing ports $ports on $serverIp" }

        val sockets = LinkedHashMap<Long, DatagramSocket?>()
        try {
            withContext(dispatcherProvider.io()) { ports.forEach { sockets[it] = createProbeSocket(it) } }

            return coroutineScope {
                val probes = sockets.mapValues { (port, socket) ->
                    async {
                        val responded = socket != null && probePort(socket, serverIp, port)
                        logcat { "Port probe: port $port ${if (responded) "responded" else "did not respond"}" }
                        responded
                    }
                }

                withTimeoutOrNull(PROBE_TIMEOUT_MS.milliseconds) {
                    val earlyExitProbes = probes.filterKeys { it in earlyExitPorts }.values
                    if (earlyExitProbes.isEmpty() || !earlyExitProbes.awaitAll().all { it }) {
                        probes.values.awaitAll()
                    }
                }
                sockets.values.forEach { it?.close() }

                probes.filter { (_, probe) -> probe.isCompleted && !probe.isCancelled && probe.await() }.keys.toList()
            }.also {
                if (it.isEmpty()) logcat { "Port probe: no ports responded within timeout" }
            }
        } finally {
            sockets.values.forEach { it?.close() }
        }
    }

    private fun createProbeSocket(port: Long): DatagramSocket? {
        return try {
            DatagramSocket().apply {
                soTimeout = RETRANSMIT_INTERVAL_MS.toInt()
                // Protect the socket so it bypasses the VPN
                val protected = socketProtector.protect(this)
                logcat { "Port probe: socket protection ${if (protected) "succeeded" else "FAILED"} for port $port" }
            }
        } catch (e: Exception) {
            logcat { "Port probe: failed to create socket for port $port: ${e.message}" }
            null
        }
    }

    private suspend fun probePort(socket: DatagramSocket, serverIp: String, port: Long): Boolean = withContext(dispatcherProvider.io()) {
        try {
            val probeMessage = PROBE_MESSAGE.toByteArray()
            val sendPacket = DatagramPacket(probeMessage, probeMessage.size, InetSocketAddress(serverIp, port.toInt()))
            val receivePacket = DatagramPacket(ByteArray(RECEIVE_BUFFER_SIZE), RECEIVE_BUFFER_SIZE)

            repeat(RETRANSMIT_COUNT) {
                socket.send(sendPacket)
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    if (response == EXPECTED_RESPONSE) {
                        return@withContext true
                    } else {
                        logcat { "Port probe: unexpected response from $serverIp:$port: $response" }
                    }
                } catch (e: SocketTimeoutException) {
                    // no response within the retransmit interval, send again
                }
            }

            false
        } catch (e: Exception) {
            logcat { "Port probe: error probing $serverIp:$port: ${e.message}" }
            false
        }
    }

    companion object {
        private const val PROBE_MESSAGE = "DDGPROBE"
        private const val EXPECTED_RESPONSE = "DDG"
        private const val PROBE_TIMEOUT_MS = 1500L
        private const val RETRANSMIT_INTERVAL_MS = 500L
        private const val RETRANSMIT_COUNT = 3
        private const val RECEIVE_BUFFER_SIZE = 64
    }
}
