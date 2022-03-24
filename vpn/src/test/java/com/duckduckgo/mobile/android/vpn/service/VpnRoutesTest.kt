/*
 * Copyright (c) 2021 DuckDuckGo
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

@file:Suppress("NonAsciiCharacters")

package com.duckduckgo.mobile.android.vpn.service

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class VpnRoutesTest {

    @Test
    fun `all t-mobile wifi calling addresses are excluded from VPN`() {
        assertNoRoutes(findRoutes("66.94.2.0", "66.94.2.255"))
        assertNoRoutes(findRoutes("66.94.6.0", "66.94.7.255"))
        assertNoRoutes(findRoutes("66.94.8.0", "66.94.11.255"))
        assertNoRoutes(findRoutes("208.54.0.0", "208.54.255.255"))
    }

    @Test
    fun `all verizon wifi calling addresses are excluded from VPN`() {
        assertNoRoutes(findRoutes("66.174.0.0", "66.174.255.255"))
        assertNoRoutes(findRoutes("69.82.0.0", "69.83.255.255"))
        assertNoRoutes(findRoutes("69.96.0.0", "69.103.255.255"))
        assertNoRoutes(findRoutes("70.192.0.0", "70.223.255.255"))
        assertNoRoutes(findRoutes("72.96.0.0", "72.127.255.255"))
        assertNoRoutes(findRoutes("75.192.0.0", "75.255.255.255"))
        assertNoRoutes(findRoutes("97.0.0.0", "97.63.255.255"))
        assertNoRoutes(findRoutes("97.128.0.0", "97.191.255.255"))
        assertNoRoutes(findRoutes("174.192.0.0", "174.255.255.255"))
    }

    @Test
    fun `all private local addresses in 10·x·x·x range are excluded from VPN`() {
        val fromRange = "10.0.0.0"
        val toRange = "10.255.255.255"
        assertNoRoutes(findRoutes(fromRange, toRange))
    }

    @Test
    fun `all link-local addresses between 169·254·0·0 and 169·254·255·255 are excluded from VPN`() {
        val fromRange = "169.254.0.0"
        val toRange = "169.254.255.255"
        assertNoRoutes(findRoutes(fromRange, toRange))
    }

    @Test
    fun `all private local addresses between 172·16·0·0 and 172·31·255·255 are excluded from VPN`() {
        val fromRange = "172.16.0.0"
        val toRange = "172.31.255.255"
        assertNoRoutes(findRoutes(fromRange, toRange))
    }

    @Test
    fun `all private local addresses in 192·168·x·x range are excluded from VPN`() {
        val fromRange = "192.168.0.0"
        val toRange = "192.168.255.255"
        assertNoRoutes(findRoutes(fromRange, toRange))
    }

    @Test
    fun `all multicast addresses between 224·0·0·0 and 239·255·255·255 are excluded from VPN`() {
        val fromRange = "224.0.0.0"
        val toRange = "239.255.255.255"
        assertNoRoutes(findRoutes(fromRange, toRange))
    }

    @Test
    fun `broadcast address 255·255·255·255 is excluded from VPN`() {
        val fromRange = "255.255.255.255"
        val toRange = "255.255.255.255"
        assertNoRoutes(findRoutes(fromRange, toRange))
    }

    @Test
    fun `all addresses below private 10·x·x·x range go through VPN`() {
        val fromRange = "0.0.0.0"
        val toRange = "9.255.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between private 10·x·x·x range and wifi calling range go through VPN`() {
        val fromRange = "11.0.0.0"
        val toRange = "66.94.1.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 66·94·3·0 range and wifi calling range go through VPN`() {
        val fromRange = "66.94.3.0"
        val toRange = "66.94.5.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 66·94·12·0 range and wifi calling range go through VPN`() {
        val fromRange = "66.94.12.0"
        val toRange = "66.173.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 66·175·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "66.175.0.0"
        val toRange = "69.81.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 69·84·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "69.84.0.0"
        val toRange = "69.95.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 69·104·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "69.104.0.0"
        val toRange = "70.191.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 70·224·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "70.224.0.0"
        val toRange = "71.255.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 72·128·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "72.128.0.0"
        val toRange = "75.127.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 76·0·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "76.0.0.0"
        val toRange = "96.255.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 97·64·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "97.64.0.0"
        val toRange = "97.127.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 98·0·0·0 range and wifi calling range go through VPN`() {
        val fromRange = "98.0.0.0"
        val toRange = "169.253.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between link local range and 172 private range go through VPN`() {
        val fromRange = "169.255.0.0"
        val toRange = "172.15.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between private 172 range and 174·191·255·255 range go through VPN`() {
        val fromRange = "172.32.0.0"
        val toRange = "174.191.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 175·0·0·0 range and private 192 range go through VPN`() {
        val fromRange = "175.0.0.0"
        val toRange = "192.167.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between private 192 range and 208·53·255·255 go through VPN`() {
        val fromRange = "192.169.0.0"
        val toRange = "208.53.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between 208·55·0·0 range and multicast range go through VPN`() {
        val fromRange = "208.55.0.0"
        val toRange = "223.255.255.255"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `all addresses between multicast range and broadcast address go through VPN`() {
        val fromRange = "240.0.0.0"
        val toRange = "255.255.255.254"
        val routes = findRoutes(fromRange, toRange)
        assertIpGreaterOrEqualTo(fromRange, routes.first().lowAddress)
        assertIpLesserOrEqualTo(toRange, routes.last().highAddress)
        assertNoGaps(routes)
    }

    @Test
    fun `increment IP address`() {
        assertEquals("0.0.0.1", "0.0.0.0".incrementIpAddress())
        assertEquals("0.0.0.255", "0.0.0.254".incrementIpAddress())
        assertEquals("0.0.2.0", "0.0.1.255".incrementIpAddress())
        assertEquals("0.1.3.0", "0.1.2.255".incrementIpAddress())
        assertEquals("255.255.255.255", "255.255.255.254".incrementIpAddress())

        kotlin.runCatching { "255.255.255.255".incrementIpAddress() }.onSuccess { fail("This should fail the test but didn't") }
    }

    private fun assertNoRoutes(routes: List<Route>) {
        assertTrue("Expected no routes but found ${routes.size}", routes.isEmpty())
    }

    private fun assertIpLesserOrEqualTo(
        ipAddress: String,
        compareTo: String
    ) {
        assertTrue("$ipAddress needs to be <= $compareTo", ipAddress.normalizeIpAddress() <= compareTo.normalizeIpAddress())
    }

    private fun assertIpGreaterOrEqualTo(
        ipAddress: String,
        compareTo: String
    ) {
        assertTrue("$ipAddress needs to be >= $compareTo", ipAddress.normalizeIpAddress() >= compareTo.normalizeIpAddress())
    }

    private fun findRoutes(
        lowest: String,
        highest: String
    ): List<Route> {
        return VpnRoutes.includedRoutes
            .filter { it.lowAddress.normalizeIpAddress() <= highest.normalizeIpAddress() }
            .filter { it.highAddress.normalizeIpAddress() >= lowest.normalizeIpAddress() }
    }

    private fun assertNoGaps(routes: List<Route>) {
        routes.zipWithNext().forEach {
            val highestFromCurrentRoute = it.first.highAddress
            val expectedNextIpAddress = highestFromCurrentRoute.incrementIpAddress()
            val lowestFromNextRoute = it.second.lowAddress
            assertEquals("Gap found in routes: $expectedNextIpAddress", expectedNextIpAddress, lowestFromNextRoute)
        }
    }

    private fun String.incrementIpAddress(): String {
        if (this == "255.255.255.255") {
            throw IllegalArgumentException("Cannot increment IP address; already maxed out at 255.255.255.255")
        }

        val addressBytes = InetAddress.getByName(this).address

        for (index in addressBytes.size - 1 downTo 0) {
            if (addressBytes[index].canIncrement()) {
                addressBytes[index]++
                break
            } else {
                addressBytes[index] = 0
            }
        }

        return InetAddress.getByAddress(addressBytes).hostAddress
    }

    private fun String.normalizeIpAddress(): String {
        return String.format(
            "%s.%s.%s.%s",
            split(".")[0].padStart(3, '0'),
            split(".")[1].padStart(3, '0'),
            split(".")[2].padStart(3, '0'),
            split(".")[3].padStart(3, '0')
        )
    }

    private fun Byte.canIncrement(): Boolean {
        val uByte = this.toInt() and 0xFF
        return uByte < 255
    }
}
