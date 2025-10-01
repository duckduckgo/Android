/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.config

import logcat.logcat
import java.net.InetAddress

typealias IpRange = Pair<String, Int>

class WgVpnRoutes {

    fun generateVpnRoutes(excludedRanges: Map<String, Int>): Map<String, Int> {
        // special cases
        // empty, return all space
        if (excludedRanges.isEmpty()) {
            return mapOf("0.0.0.0" to 0)
        }
        // all, return no routes
        if (excludedRanges["0.0.0.0"] == 0) {
            return emptyMap()
        }

        val listExclude = excludedRanges.map { CIDR(InetAddress.getByName(it.key), it.value) }.sorted()

        return runCatching {
            val routes = mutableMapOf<InetAddress, Int>()
            var start = InetAddress.getByName("0.0.0.0")
            listExclude.forEach { exclude ->
                CIDR.createFrom(start, exclude.start?.minus1()!!).forEach { include ->
                    routes[include.address] = include.prefix
                }
                start = exclude.end?.plus1()!!
            }

            if (start.toLong() != 0L) {
                CIDR.createFrom(start, InetAddress.getByName("255.255.255.255")).forEach { remaining ->
                    routes[remaining.address] = remaining.prefix
                }
            }
            // routes
            routes.mapKeys { it.key.hostAddress!! }
        }.getOrDefault(emptyMap()).also {
            logcat { "Runtime-generated routes $it" }
        }
    }

    /**
     * We want to exclude local network traffic from routing through the VPN, but include everything else
     *
     * The VPN APIs don't allow you to add everything and then exclude some ranges.
     * Instead, we must be selective in what we add to ensure local addresses aren't routed.
     *
     * We exclude:
     *   - private local IP ranges
     *   - special IP addresses 127.0.0.0 to 127.255.255.255
     *   - not allocated to host (Class E) IP address - 240.0.0.0 to 255.255.255.255
     *   - link-local address range
     *   - multicast (Class D) address range - 224.0.0.0 to 239.255.255.255
     *   - broadcast address
     *   - eun controller: 20.93.77.32
     *   - use controller: 20.253.26.112
     */
    companion object {
        private val CLASS_A_PRIVATE_IP_RANGE: IpRange = "10.0.0.0" to 8
        private val CLASS_B_PRIVATE_IP_RANGE: IpRange = "172.16.0.0" to 12
        private val CLASS_B_APIPA_IP_RANGE: IpRange = "169.254.0.0" to 16
        private val CLASS_C_PRIVATE_IP_RANGE: IpRange = "192.168.0.0" to 16
        private val CLASS_C_SPECIAL_IP_RANGE: IpRange = "127.0.0.0" to 8
        private val CLASS_D_SPECIAL_IP_RANGE: IpRange = "224.0.0.0" to 4

        val vpnDefaultExcludedRoutes: Map<String, Int> = mapOf(
            CLASS_A_PRIVATE_IP_RANGE,
            CLASS_C_SPECIAL_IP_RANGE,
            CLASS_B_APIPA_IP_RANGE,
            CLASS_B_PRIVATE_IP_RANGE,
            CLASS_C_PRIVATE_IP_RANGE,
            CLASS_D_SPECIAL_IP_RANGE,
        )

        val vpnExcludedSpecialRoutes: Map<String, Int> = mapOf(
            CLASS_C_SPECIAL_IP_RANGE,
            CLASS_B_APIPA_IP_RANGE,
            CLASS_D_SPECIAL_IP_RANGE,
        )

        val wgVpnDefaultRoutes: Map<String, Int> = mapOf(
            "0.0.0.0" to 5,
            "8.0.0.0" to 7,
            // Excluded range: 10.0.0.0 -> 10.255.255.255
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 6,
            "20.0.0.0" to 10,
            "20.64.0.0" to 12,
            "20.80.0.0" to 13,
            "20.88.0.0" to 14,
            "20.92.0.0" to 16,
            "20.93.0.0" to 18,
            "20.93.64.0" to 21,
            "20.93.72.0" to 22,
            "20.93.76.0" to 24,
            "20.93.77.0" to 27,
            // Excluded range: 20.93.77.32 -> 20.93.77.32
            "20.93.77.33" to 32,
            "20.93.77.34" to 31,
            "20.93.77.36" to 30,
            "20.93.77.40" to 29,
            "20.93.77.48" to 28,
            "20.93.77.64" to 26,
            "20.93.77.128" to 25,
            "20.93.78.0" to 23,
            "20.93.80.0" to 20,
            "20.93.96.0" to 19,
            "20.93.128.0" to 17,
            "20.94.0.0" to 15,
            "20.96.0.0" to 11,
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
            // Excluded range: 20.253.26.112 -> 20.253.26.112
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
            "64.0.0.0" to 3,
            "96.0.0.0" to 4,
            "112.0.0.0" to 5,
            "120.0.0.0" to 6,
            "124.0.0.0" to 7,
            "126.0.0.0" to 8,
            // Excluded range: 127.0.0.0 -> 127.255.255.255
            "128.0.0.0" to 3,
            "160.0.0.0" to 5,
            "168.0.0.0" to 8,
            "169.0.0.0" to 9,
            "169.128.0.0" to 10,
            "169.192.0.0" to 11,
            "169.224.0.0" to 12,
            "169.240.0.0" to 13,
            "169.248.0.0" to 14,
            "169.252.0.0" to 15,
            // Excluded range: 169.254.0.0 -> 169.254.255.255
            "169.255.0.0" to 16,
            "170.0.0.0" to 7,
            "172.0.0.0" to 12,
            // Excluded range: 172.16.0.0 -> 172.31.255.255
            "172.32.0.0" to 11,
            "172.64.0.0" to 10,
            "172.128.0.0" to 9,
            "173.0.0.0" to 8,
            "174.0.0.0" to 7,
            "176.0.0.0" to 4,
            "192.0.0.0" to 9,
            "192.128.0.0" to 11,
            "192.160.0.0" to 13,
            // Excluded range: 192.168.0.0 -> 192.168.255.255
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
            // Excluded range: 224.0.0.0 -> 239.255.255.255
            // Excluded range: 240.0.0.0 -> 255.255.255.255
        )

        val wgVpnRoutesIncludingLocal: Map<String, Int> = mapOf(
            "0.0.0.0" to 4,
            "16.0.0.0" to 6,
            "20.0.0.0" to 10,
            "20.64.0.0" to 12,
            "20.80.0.0" to 13,
            "20.88.0.0" to 14,
            "20.92.0.0" to 16,
            "20.93.0.0" to 18,
            "20.93.64.0" to 21,
            "20.93.72.0" to 22,
            "20.93.76.0" to 24,
            "20.93.77.0" to 27,
            // Excluded range: 20.93.77.32 -> 20.93.77.32
            "20.93.77.33" to 32,
            "20.93.77.34" to 31,
            "20.93.77.36" to 30,
            "20.93.77.40" to 29,
            "20.93.77.48" to 28,
            "20.93.77.64" to 26,
            "20.93.77.128" to 25,
            "20.93.78.0" to 23,
            "20.93.80.0" to 20,
            "20.93.96.0" to 19,
            "20.93.128.0" to 17,
            "20.94.0.0" to 15,
            "20.96.0.0" to 11,
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
            // Excluded range: 20.253.26.112 -> 20.253.26.112
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
            "64.0.0.0" to 3,
            "96.0.0.0" to 4,
            "112.0.0.0" to 5,
            "120.0.0.0" to 6,
            "124.0.0.0" to 7,
            "126.0.0.0" to 8,
            // Excluded range: 127.0.0.0 -> 127.255.255.255
            "128.0.0.0" to 3,
            "160.0.0.0" to 5,
            "168.0.0.0" to 8,
            "169.0.0.0" to 9,
            "169.128.0.0" to 10,
            "169.192.0.0" to 11,
            "169.224.0.0" to 12,
            "169.240.0.0" to 13,
            "169.248.0.0" to 14,
            "169.252.0.0" to 15,
            // Excluded range: 169.254.0.0 -> 169.254.255.255
            "169.255.0.0" to 16,
            "170.0.0.0" to 7,
            "172.0.0.0" to 6,
            "176.0.0.0" to 4,
            "192.0.0.0" to 3,
            // Excluded range: 224.0.0.0 -> 239.255.255.255
            // Excluded range: 240.0.0.0 -> 255.255.255.255
        )
    }
}
