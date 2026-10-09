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
import com.duckduckgo.mobile.android.vpn.service.VpnSocketProtector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class RealPortProberTest {

    private val socketProtector: VpnSocketProtector = mock()
    private val prober = RealPortProber(socketProtector, object : DispatcherProvider {})
    private val servers = mutableListOf<DatagramSocket>()

    @Before
    fun setup() {
        whenever(socketProtector.protect(any<DatagramSocket>())).thenReturn(true)
    }

    @After
    fun tearDown() {
        servers.forEach { it.close() }
    }

    @Test
    fun whenPortRepliesWithExpectedResponseThenReturnIt() = runBlocking {
        val responding = startServer(reply = "DDG")
        val silent = startServer(reply = null)

        val result = prober.probePortsInParallel(LOCALHOST, listOf(silent, responding), earlyExitPorts = emptySet())

        assertEquals(listOf(responding), result)
    }

    @Test
    fun whenPortRepliesWithUnexpectedResponseThenIgnoreIt() = runBlocking {
        val wrongReply = startServer(reply = "NOPE")

        val result = prober.probePortsInParallel(LOCALHOST, listOf(wrongReply), earlyExitPorts = emptySet())

        assertEquals(emptyList<Long>(), result)
    }

    @Test
    fun whenEarlyExitPortsRespondThenDoNotWaitForSilentPorts() = runBlocking {
        val responding = startServer(reply = "DDG")
        val silent = startServer(reply = null)

        val start = System.currentTimeMillis()
        val result = prober.probePortsInParallel(LOCALHOST, listOf(responding, silent), earlyExitPorts = setOf(responding))
        val elapsed = System.currentTimeMillis() - start

        assertEquals(listOf(responding), result)
        assertTrue("took ${elapsed}ms", elapsed < 400)
    }

    @Test
    fun whenNoEarlyExitPortsThenWaitForSilentPortsUntilTimeout() = runBlocking {
        val responding = startServer(reply = "DDG")
        val silent = startServer(reply = null)

        val start = System.currentTimeMillis()
        prober.probePortsInParallel(LOCALHOST, listOf(responding, silent), earlyExitPorts = emptySet())
        val elapsed = System.currentTimeMillis() - start

        assertTrue("took ${elapsed}ms", elapsed >= 1400)
    }

    @Test
    fun whenFirstProbeIsLostThenRetransmitWithinHalfASecond() = runBlocking {
        val responding = startServer(reply = "DDG", ignoreFirst = 1)

        val start = System.currentTimeMillis()
        val result = prober.probePortsInParallel(LOCALHOST, listOf(responding), earlyExitPorts = setOf(responding))
        val elapsed = System.currentTimeMillis() - start

        assertEquals(listOf(responding), result)
        assertTrue("took ${elapsed}ms", elapsed < 900)
    }

    private fun startServer(reply: String?, ignoreFirst: Int = 0): Long {
        val server = DatagramSocket(0, InetAddress.getByName(LOCALHOST)).also { servers += it }
        if (reply != null) {
            thread(isDaemon = true) {
                var received = 0
                val buffer = ByteArray(64)
                runCatching {
                    while (!server.isClosed) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        server.receive(packet)
                        if (received++ < ignoreFirst) continue
                        val bytes = reply.toByteArray()
                        server.send(DatagramPacket(bytes, bytes.size, packet.socketAddress))
                    }
                }
            }
        }
        return server.localPort.toLong()
    }

    companion object {
        private const val LOCALHOST = "127.0.0.1"
    }
}
