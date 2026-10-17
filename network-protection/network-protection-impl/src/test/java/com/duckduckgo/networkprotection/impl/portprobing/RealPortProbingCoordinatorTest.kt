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

import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.wireguard.config.Config
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.BufferedReader
import java.io.StringReader

class RealPortProbingCoordinatorTest {

    private val portProber: PortProber = mock()
    private val wgTunnelConfig: WgTunnelConfig = mock()
    private lateinit var coordinator: RealPortProbingCoordinator

    @Before
    fun setup() = runTest {
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(createTestConfig(port = 443L))
        whenever(wgTunnelConfig.getAdvertisedPorts()).thenReturn(listOf(443L, 51820L))
        whenever(wgTunnelConfig.getServerDefaultPort()).thenReturn(443L)
        coordinator = RealPortProbingCoordinator(portProber, RealPortSelector(), wgTunnelConfig)
    }

    @Test
    fun whenOnlyOnePortAdvertisedThenSkipProbing() = runTest {
        whenever(wgTunnelConfig.getAdvertisedPorts()).thenReturn(listOf(443L))

        assertNull(coordinator.probeAndSelect())
    }

    @Test
    fun whenNoPortRespondsThenKeepCurrentPortWithoutSwitching() = runTest {
        whenever(wgTunnelConfig.getActivePort()).thenReturn(51820L)
        whenever(portProber.probePortsInParallel(any(), any(), any())).thenReturn(emptyList())

        val result = coordinator.probeAndSelect()!!

        assertEquals(51820L, result.selectedPort)
        assertFalse(result.shouldSwitchPort)
    }

    @Test
    fun whenCurrentPortStillRespondsThenDoNotSwitch() = runTest {
        whenever(wgTunnelConfig.getActivePort()).thenReturn(51820L)
        whenever(portProber.probePortsInParallel(any(), any(), any())).thenReturn(listOf(443L, 51820L))

        val result = coordinator.probeAndSelect()!!

        assertEquals(443L, result.selectedPort)
        assertFalse(result.shouldSwitchPort)
    }

    @Test
    fun whenOnlyAnotherPortRespondsThenSwitch() = runTest {
        whenever(wgTunnelConfig.getActivePort()).thenReturn(443L)
        whenever(portProber.probePortsInParallel(any(), any(), any())).thenReturn(listOf(51820L))

        val result = coordinator.probeAndSelect()!!

        assertEquals(51820L, result.selectedPort)
        assertTrue(result.shouldSwitchPort)
    }

    @Test
    fun whenNoActivePortThenUseConfigPortAsCurrent() = runTest {
        whenever(wgTunnelConfig.getActivePort()).thenReturn(0L)
        whenever(portProber.probePortsInParallel(any(), any(), any())).thenReturn(listOf(443L))

        val result = coordinator.probeAndSelect()!!

        assertEquals(443L, result.selectedPort)
        assertFalse(result.shouldSwitchPort)
    }

    @Test
    fun whenProbingThenStopEarlyOnceDefaultAndCurrentPortsRespond() = runTest {
        whenever(wgTunnelConfig.getActivePort()).thenReturn(51820L)
        whenever(portProber.probePortsInParallel(any(), any(), any())).thenReturn(listOf(443L, 51820L))

        coordinator.probeAndSelect()

        verify(portProber).probePortsInParallel("1.2.3.4", listOf(443L, 51820L), setOf(443L, 51820L))
    }

    @Test
    fun whenCurrentPortNotAdvertisedThenStopEarlyOnDefaultPortOnly() = runTest {
        whenever(wgTunnelConfig.getActivePort()).thenReturn(9999L)
        whenever(portProber.probePortsInParallel(any(), any(), any())).thenReturn(listOf(443L))

        coordinator.probeAndSelect()

        verify(portProber).probePortsInParallel("1.2.3.4", listOf(443L, 51820L), setOf(443L))
    }

    private fun createTestConfig(port: Long): Config {
        val configString = """
            [Interface]
            PrivateKey = cG8QmezwBwf5HkiLGHhc/LQu/E2Ee6z3MR8t0s9nXUE=
            Address = 10.11.12.1/32

            [Peer]
            PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
            Endpoint = 1.2.3.4:$port
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        return Config.parse(BufferedReader(StringReader(configString)))
    }
}
