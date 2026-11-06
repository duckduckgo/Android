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

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PortSelectorTest {

    private lateinit var portSelector: PortSelector

    @Before
    fun setup() {
        portSelector = RealPortSelector()
    }

    @Test
    fun `selectPort uses default port when it responds`() {
        val selectedPort = portSelector.selectPort(
            respondedPorts = listOf(51820L, 443L),
            currentPort = 51820L,
            serverDefaultPort = 443L,
            advertisedPorts = listOf(443L, 51820L),
        )

        assertEquals(443L, selectedPort)
    }

    @Test
    fun `selectPort uses fallback port when default does not respond`() {
        val selectedPort = portSelector.selectPort(
            respondedPorts = listOf(51820L),
            currentPort = 443L,
            serverDefaultPort = 443L,
            advertisedPorts = listOf(443L, 51820L),
        )

        assertEquals(51820L, selectedPort)
    }

    @Test
    fun `selectPort keeps current port when nothing responds and current is advertised`() {
        val selectedPort = portSelector.selectPort(
            respondedPorts = emptyList(),
            currentPort = 51820L,
            serverDefaultPort = 443L,
            advertisedPorts = listOf(443L, 51820L),
        )

        assertEquals(51820L, selectedPort)
    }

    @Test
    fun `selectPort uses server default when nothing responds and current not advertised`() {
        val selectedPort = portSelector.selectPort(
            respondedPorts = emptyList(),
            currentPort = 9999L,
            serverDefaultPort = 443L,
            advertisedPorts = listOf(443L, 51820L),
        )

        assertEquals(443L, selectedPort)
    }

    @Test
    fun `orderCandidatePorts puts server default first`() {
        val orderedPorts = portSelector.orderCandidatePorts(
            serverDefaultPort = 443L,
            advertisedPorts = listOf(51820L, 443L, 8080L),
        )

        assertEquals(listOf(443L, 51820L, 8080L), orderedPorts)
    }

    @Test
    fun `orderCandidatePorts deduplicates ports`() {
        val orderedPorts = portSelector.orderCandidatePorts(
            serverDefaultPort = 443L,
            advertisedPorts = listOf(443L, 51820L, 443L),
        )

        assertEquals(listOf(443L, 51820L), orderedPorts)
    }

    @Test
    fun `orderCandidatePorts filters out zero ports`() {
        val orderedPorts = portSelector.orderCandidatePorts(
            serverDefaultPort = 443L,
            advertisedPorts = listOf(443L, 0L, 51820L),
        )

        assertEquals(listOf(443L, 51820L), orderedPorts)
    }
}
