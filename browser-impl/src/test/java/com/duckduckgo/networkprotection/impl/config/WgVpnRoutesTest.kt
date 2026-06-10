package com.duckduckgo.networkprotection.impl.config

import org.junit.Assert.assertEquals
import org.junit.Test

class WgVpnRoutesTest {

    private val routes = WgVpnRoutes()

    @Test
    fun `test exclude 10 0 0 0 8 and 127 0 0 0 8`() {
        val excludedRanges = mapOf("10.0.0.0" to 8, "127.0.0.0" to 8)
        val result = routes.generateVpnRoutes(excludedRanges)

        val expectedRoutes = mapOf(
            "0.0.0.0" to 5,
            "8.0.0.0" to 7,
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 4,
            "32.0.0.0" to 3,
            "64.0.0.0" to 3,
            "96.0.0.0" to 4,
            "112.0.0.0" to 5,
            "120.0.0.0" to 6,
            "124.0.0.0" to 7,
            "126.0.0.0" to 8,
            "128.0.0.0" to 1,
        )

        assertEquals(expectedRoutes, result)
    }

    @Test
    fun `test exclude only 10 0 0 0 8`() {
        val excludedRanges = mapOf("10.0.0.0" to 8)
        val result = routes.generateVpnRoutes(excludedRanges)

        val expectedRoutes = mapOf(
            "0.0.0.0" to 5,
            "8.0.0.0" to 7,
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 4,
            "32.0.0.0" to 3,
            "64.0.0.0" to 2,
            "128.0.0.0" to 1,
        )

        assertEquals(expectedRoutes, result)
    }

    @Test
    fun `test exclude full IPv4 range`() {
        val excludedRanges = mapOf("0.0.0.0" to 0)
        val result = routes.generateVpnRoutes(excludedRanges)

        println(result)
        assertEquals(emptyMap<String, Int>(), result)
    }

    @Test
    fun `test exclude nothing (full range should remain)`() {
        val excludedRanges = emptyMap<String, Int>()
        val result = routes.generateVpnRoutes(excludedRanges)

        val expectedRoutes = mapOf("0.0.0.0" to 0)
        assertEquals(expectedRoutes, result)
    }

    @Test
    fun `test exclude small subnets`() {
        val excludedRanges = mapOf("192.168.1.0" to 24, "192.168.2.0" to 24)
        val result = routes.generateVpnRoutes(excludedRanges)

        val expectedRoutes = mapOf(
            "0.0.0.0" to 1,
            "128.0.0.0" to 2,
            "192.0.0.0" to 9,
            "192.128.0.0" to 11,
            "192.160.0.0" to 13,
            "192.168.0.0" to 24,
            "192.168.3.0" to 24,
            "192.168.4.0" to 22,
            "192.168.8.0" to 21,
            "192.168.16.0" to 20,
            "192.168.32.0" to 19,
            "192.168.64.0" to 18,
            "192.168.128.0" to 17,
            "192.169.0.0" to 16,
            "192.170.0.0" to 15,
            "192.172.0.0" to 14,
            "192.176.0.0" to 12,
            "192.192.0.0" to 10,
            "193.0.0.0" to 8,
            "194.0.0.0" to 7,
            "196.0.0.0" to 6,
            "200.0.0.0" to 5,
            "208.0.0.0" to 4,
            "224.0.0.0" to 3,
        )

        assertEquals(expectedRoutes, result)
    }

    @Test
    fun `test exclude single IP address`() {
        val excludedRanges = mapOf(
            "20.253.26.112" to 32,
        )
        val result = routes.generateVpnRoutes(excludedRanges)

        val expectedRoutes = mapOf(
            "0.0.0.0" to 4,
            "16.0.0.0" to 6,
            "20.0.0.0" to 9,
            "20.128.0.0" to 10,
            "20.192.0.0" to 11,
            "20.224.0.0" to 12,
            "20.240.0.0" to 13,
            "20.248.0.0" to 14,
            "20.252.0.0" to 16,
            "20.253.0.0" to 20,
            "20.253.16.0" to 21,
            "20.253.24.0" to 23,
            "20.253.26.0" to 26,
            "20.253.26.64" to 27,
            "20.253.26.96" to 28,
            "20.253.26.113" to 32,
            "20.253.26.114" to 31,
            "20.253.26.116" to 30,
            "20.253.26.120" to 29,
            "20.253.26.128" to 25,
            "20.253.27.0" to 24,
            "20.253.28.0" to 22,
            "20.253.32.0" to 19,
            "20.253.64.0" to 18,
            "20.253.128.0" to 17,
            "20.254.0.0" to 15,
            "21.0.0.0" to 8,
            "22.0.0.0" to 7,
            "24.0.0.0" to 5,
            "32.0.0.0" to 3,
            "64.0.0.0" to 2,
            "128.0.0.0" to 1,
        )

        assertEquals(expectedRoutes, result)
    }

    @Test
    fun `test exclude multiple subnets including single IP addresses`() {
        val excludedRanges = mapOf(
            "10.0.0.0" to 8,
            "127.0.0.0" to 8,
            "169.254.0.0" to 16,
            "172.16.0.0" to 12,
            "192.168.0.0" to 16,
            "224.0.0.0" to 4,
            "240.0.0.0" to 4,
            "20.93.77.32" to 32,
            "20.253.26.112" to 32,
        )
        val result = routes.generateVpnRoutes(excludedRanges)

        val expectedRoutes = WgVpnRoutes.wgVpnDefaultRoutes

        assertEquals(expectedRoutes, result)
    }

    @Test
    fun `test exclude multiple subnets including local and including single IP addresses`() {
        val excludedRanges = mapOf(
            "127.0.0.0" to 8,
            "169.254.0.0" to 16,
            "224.0.0.0" to 4,
            "240.0.0.0" to 4,
            "20.93.77.32" to 32,
            "20.253.26.112" to 32,
        )
        val result = routes.generateVpnRoutes(excludedRanges)

        val expectedRoutes = WgVpnRoutes.wgVpnRoutesIncludingLocal

        assertEquals(expectedRoutes, result)
    }
}
