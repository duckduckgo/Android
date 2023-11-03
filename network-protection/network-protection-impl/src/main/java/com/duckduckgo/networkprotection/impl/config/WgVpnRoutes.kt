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

internal class WgVpnRoutes {
    /**
     * We want to exclude local network traffic from routing through the VPN, but include everything else
     *
     * The VPN APIs don't allow you to add everything and then exclude some ranges.
     * Instead, we must be selective in what we add to ensure local addresses aren't routed.
     *
     * We exclude:
     *   - private local IP ranges
     *   - CGNAT address range 100.64.0.0 -> 100.127.255.255
     *   - special IP addresses 127.0.0.0 to 127.255.255.255
     *   - not allocated to host (Class E) IP address - 240.0.0.0 to 255.255.255.255
     *   - link-local address range
     *   - multicast (Class D) address range - 224.0.0.0 to 239.255.255.255
     *   - broadcast address
     */
    companion object {
        val wgVpnDefaultRoutes: Map<String, Int> = mapOf(
            "0.0.0.0" to 5,
            "8.0.0.0" to 7,
            // Excluded range: 10.0.0.0 -> 10.255.255.255
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 4,
            "32.0.0.0" to 3,
            "64.0.0.0" to 3,
            "96.0.0.0" to 6,
            "100.0.0.0" to 10,
            // Excluded range: 100.64.0.0 -> 100.127.255.255
            "100.128.0.0" to 9,
            "101.0.0.0" to 8,
            "102.0.0.0" to 7,
            "104.0.0.0" to 5,
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
            "0.0.0.0" to 5,
            "8.0.0.0" to 7,
            // Excluded range: 10.0.0.0 -> 10.255.255.255
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 4,
            "32.0.0.0" to 3,
            "64.0.0.0" to 3,
            "96.0.0.0" to 6,
            "100.0.0.0" to 10,
            // Excluded range: 100.64.0.0 -> 100.127.255.255
            "100.128.0.0" to 9,
            "101.0.0.0" to 8,
            "102.0.0.0" to 7,
            "104.0.0.0" to 5,
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
