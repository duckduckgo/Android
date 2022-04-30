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

package com.duckduckgo.mobile.android.vpn.service

class VpnRoutes {

    companion object {

        /**
         * We want to exclude local network traffic from routing through the VPN, but include everything else
         *
         * The VPN APIs don't allow you to add everything and then exclude some ranges.
         * Instead, we must be selective in what we add to ensure local addresses aren't routed.
         *
         * We exclude:
         *   - private local IP ranges
         *   - link-local address range
         *   - multicast address range
         *   - broadcast address
         *   - T-mobile wi-fi calling
         *      - 66.94.2.0/24  -> 66.94.2.255
         *      - 66.94.6.0/23  -> 66.94.7.255
         *      - 66.94.8.0/22  -> 66.94.11.255
         *      - 208.54.0.0/16 -> 208.54.255.255
         *   - Verizon wi-fi calling
         *      - 66.174.0.0/16 -> 66.174.255.255
         *      - 69.82.0.0/15  -> 69.83.255.255
         *      - 69.96.0.0/13  -> 69.103.255.255
         *      - 70.192.0.0/11 -> 70.223.255.255
         *      - 72.96.0.0/9   -> 72.127.255.255
         *      - 75.192.0.0/9  -> 75.255.255.255
         *      - 97.0.0.0/10   -> 97.63.255.255
         *      - 97.128.0.0/9  -> 97.255.255.255
         *      - 174.192.0.0/9 -> 174.255.255.255
         */
        val includedRoutes: List<Route> = listOf(
            Route(address = "0.0.0.0", maskWidth = 5, lowAddress = "0.0.0.0", highAddress = "7.255.255.255"),
            Route(address = "8.0.0.0", maskWidth = 7, lowAddress = "8.0.0.0", highAddress = "9.255.255.255"),
            // Excluded range: 10.0.0.0 -> 10.255.255.255
            Route(address = "11.0.0.0", maskWidth = 8, lowAddress = "11.0.0.0", highAddress = "11.255.255.255"),
            Route(address = "12.0.0.0", maskWidth = 6, lowAddress = "12.0.0.0", highAddress = "15.255.255.255"),
            Route(address = "16.0.0.0", maskWidth = 4, lowAddress = "16.0.0.0", highAddress = "31.255.255.255"),
            Route(address = "32.0.0.0", maskWidth = 3, lowAddress = "32.0.0.0", highAddress = "63.255.255.255"),
            Route(address = "64.0.0.0", maskWidth = 7, lowAddress = "64.0.0.0", highAddress = "65.255.255.255"),
            Route(address = "66.0.0.0", maskWidth = 10, lowAddress = "66.0.0.0", highAddress = "66.63.255.255"),
            Route(address = "66.64.0.0", maskWidth = 12, lowAddress = "66.64.0.0", highAddress = "66.79.255.255"),
            Route(address = "66.80.0.0", maskWidth = 13, lowAddress = "66.80.0.0", highAddress = "66.87.255.255"),
            Route(address = "66.88.0.0", maskWidth = 14, lowAddress = "66.88.0.0", highAddress = "66.91.255.255"),
            Route(address = "66.92.0.0", maskWidth = 15, lowAddress = "66.92.0.0", highAddress = "66.93.255.255"),
            Route(address = "66.94.0.0", maskWidth = 23, lowAddress = "66.94.0.0", highAddress = "66.94.1.255"),
            // Excluded range: 66.94.2.0 -> 66.94.2.255
            Route(address = "66.94.3.0", maskWidth = 24, lowAddress = "66.94.3.0", highAddress = "66.94.3.255"),
            Route(address = "66.94.4.0", maskWidth = 23, lowAddress = "66.94.4.0", highAddress = "66.94.5.255"),
            // Excluded range: 66.94.6.0 -> 66.94.7.255
            // Excluded range: 66.94.8.0 -> 66.94.11.255
            Route(address = "66.94.12.0", maskWidth = 22, lowAddress = "66.94.12.0", highAddress = "66.94.15.255"),
            Route(address = "66.94.16.0", maskWidth = 20, lowAddress = "66.94.16.0", highAddress = "66.94.31.255"),
            Route(address = "66.94.32.0", maskWidth = 19, lowAddress = "66.94.32.0", highAddress = "66.94.63.255"),
            Route(address = "66.94.64.0", maskWidth = 18, lowAddress = "66.94.64.0", highAddress = "66.94.127.255"),
            Route(address = "66.94.128.0", maskWidth = 17, lowAddress = "66.94.128.0", highAddress = "66.94.255.255"),
            Route(address = "66.95.0.0", maskWidth = 16, lowAddress = "66.95.0.0", highAddress = "66.95.255.255"),
            Route(address = "66.96.0.0", maskWidth = 11, lowAddress = "66.96.0.0", highAddress = "66.127.255.255"),
            Route(address = "66.128.0.0", maskWidth = 11, lowAddress = "66.128.0.0", highAddress = "66.159.255.255"),
            Route(address = "66.160.0.0", maskWidth = 13, lowAddress = "66.160.0.0", highAddress = "66.167.255.255"),
            Route(address = "66.168.0.0", maskWidth = 14, lowAddress = "66.168.0.0", highAddress = "66.171.255.255"),
            Route(address = "66.172.0.0", maskWidth = 15, lowAddress = "66.172.0.0", highAddress = "66.173.255.255"),
            // Excluded range: 66.174.0.0 -> 66.174.255.255
            Route(address = "66.175.0.0", maskWidth = 16, lowAddress = "66.175.0.0", highAddress = "66.175.255.255"),
            Route(address = "66.176.0.0", maskWidth = 12, lowAddress = "66.176.0.0", highAddress = "66.191.255.255"),
            Route(address = "66.192.0.0", maskWidth = 10, lowAddress = "66.192.0.0", highAddress = "66.255.255.255"),
            Route(address = "67.0.0.0", maskWidth = 8, lowAddress = "67.0.0.0", highAddress = "67.255.255.255"),
            Route(address = "68.0.0.0", maskWidth = 8, lowAddress = "68.0.0.0", highAddress = "68.255.255.255"),
            Route(address = "69.0.0.0", maskWidth = 10, lowAddress = "69.0.0.0", highAddress = "69.63.255.255"),
            Route(address = "69.64.0.0", maskWidth = 12, lowAddress = "69.64.0.0", highAddress = "69.79.255.255"),
            Route(address = "69.80.0.0", maskWidth = 15, lowAddress = "69.80.0.0", highAddress = "69.81.255.255"),
            // Excluded range: 69.82.0.0 -> 69.83.255.255
            Route(address = "69.84.0.0", maskWidth = 14, lowAddress = "69.84.0.0", highAddress = "69.87.255.255"),
            Route(address = "69.88.0.0", maskWidth = 13, lowAddress = "69.88.0.0", highAddress = "69.95.255.255"),
            // Excluded range: 69.96.0.0 -> 69.103.255.255
            Route(address = "69.104.0.0", maskWidth = 13, lowAddress = "69.104.0.0", highAddress = "69.111.255.255"),
            Route(address = "69.112.0.0", maskWidth = 12, lowAddress = "69.112.0.0", highAddress = "69.127.255.255"),
            Route(address = "69.128.0.0", maskWidth = 9, lowAddress = "69.128.0.0", highAddress = "69.255.255.255"),
            Route(address = "70.0.0.0", maskWidth = 9, lowAddress = "70.0.0.0", highAddress = "70.127.255.255"),
            Route(address = "70.128.0.0", maskWidth = 10, lowAddress = "70.128.0.0", highAddress = "70.191.255.255"),
            // Excluded range: 70.192.0.0 -> 70.223.255.255
            Route(address = "70.224.0.0", maskWidth = 11, lowAddress = "70.224.0.0", highAddress = "70.255.255.255"),
            Route(address = "71.0.0.0", maskWidth = 8, lowAddress = "71.0.0.0", highAddress = "71.255.255.255"),
            // Excluded range: 72.0.0.0 -> 72.127.255.255
            Route(address = "72.128.0.0", maskWidth = 9, lowAddress = "72.128.0.0", highAddress = "72.255.255.255"),
            Route(address = "73.0.0.0", maskWidth = 8, lowAddress = "73.0.0.0", highAddress = "73.255.255.255"),
            Route(address = "74.0.0.0", maskWidth = 8, lowAddress = "74.0.0.0", highAddress = "74.255.255.255"),
            Route(address = "75.0.0.0", maskWidth = 9, lowAddress = "75.0.0.0", highAddress = "75.127.255.255"),
            // Excluded range: 75.128.0.0 -> 75.255.255.255
            Route(address = "76.0.0.0", maskWidth = 6, lowAddress = "76.0.0.0", highAddress = "79.255.255.255"),
            Route(address = "80.0.0.0", maskWidth = 4, lowAddress = "80.0.0.0", highAddress = "95.255.255.255"),
            Route(address = "96.0.0.0", maskWidth = 8, lowAddress = "96.0.0.0", highAddress = "96.255.255.255"),
            // Excluded range: 97.0.0.0 -> 97.63.255.255
            Route(address = "97.64.0.0", maskWidth = 10, lowAddress = "97.64.0.0", highAddress = "97.127.255.255"),
            // Excluded range: 97.128.0.0 -> 97.255.255.255
            Route(address = "98.0.0.0", maskWidth = 7, lowAddress = "98.0.0.0", highAddress = "99.255.255.255"),
            Route(address = "100.0.0.0", maskWidth = 6, lowAddress = "100.0.0.0", highAddress = "103.255.255.255"),
            Route(address = "104.0.0.0", maskWidth = 5, lowAddress = "104.0.0.0", highAddress = "111.255.255.255"),
            Route(address = "112.0.0.0", maskWidth = 4, lowAddress = "112.0.0.0", highAddress = "127.255.255.255"),
            Route(address = "128.0.0.0", maskWidth = 3, lowAddress = "128.0.0.0", highAddress = "159.255.255.255"),
            Route(address = "160.0.0.0", maskWidth = 5, lowAddress = "160.0.0.0", highAddress = "167.255.255.255"),
            Route(address = "168.0.0.0", maskWidth = 8, lowAddress = "168.0.0.0", highAddress = "168.255.255.255"),
            Route(address = "169.0.0.0", maskWidth = 9, lowAddress = "169.0.0.0", highAddress = "169.127.255.255"),
            Route(address = "169.128.0.0", maskWidth = 10, lowAddress = "169.128.0.0", highAddress = "169.191.255.255"),
            Route(address = "169.192.0.0", maskWidth = 11, lowAddress = "169.192.0.0", highAddress = "169.223.255.255"),
            Route(address = "169.224.0.0", maskWidth = 12, lowAddress = "169.224.0.0", highAddress = "169.239.255.255"),
            Route(address = "169.240.0.0", maskWidth = 13, lowAddress = "169.240.0.0", highAddress = "169.247.255.255"),
            Route(address = "169.248.0.0", maskWidth = 14, lowAddress = "169.248.0.0", highAddress = "169.251.255.255"),
            Route(address = "169.252.0.0", maskWidth = 15, lowAddress = "169.252.0.0", highAddress = "169.253.255.255"),
            // Excluded range: 169.254.0.0 -> 169.254.255.255
            Route(address = "169.255.0.0", maskWidth = 16, lowAddress = "169.255.0.0", highAddress = "169.255.255.255"),
            Route(address = "170.0.0.0", maskWidth = 7, lowAddress = "170.0.0.0", highAddress = "171.255.255.255"),
            Route(address = "172.0.0.0", maskWidth = 12, lowAddress = "172.0.0.0", highAddress = "172.15.255.255"),
            // Excluded range: 172.16.0.0 -> 172.31.255.255
            Route(address = "172.32.0.0", maskWidth = 11, lowAddress = "172.32.0.0", highAddress = "172.63.255.255"),
            Route(address = "172.64.0.0", maskWidth = 10, lowAddress = "172.64.0.0", highAddress = "172.127.255.255"),
            Route(address = "172.128.0.0", maskWidth = 9, lowAddress = "172.128.0.0", highAddress = "172.255.255.255"),
            Route(address = "173.0.0.0", maskWidth = 8, lowAddress = "173.0.0.0", highAddress = "173.255.255.255"),
            Route(address = "174.0.0.0", maskWidth = 9, lowAddress = "174.0.0.0", highAddress = "174.127.255.255"),
            Route(address = "174.128.0.0", maskWidth = 10, lowAddress = "174.128.0.0", highAddress = "174.191.255.255"),
            // Excluded range: 174.192.0.0 -> 174.255.255.255
            Route(address = "175.0.0.0", maskWidth = 8, lowAddress = "175.0.0.0", highAddress = "175.255.255.255"),
            Route(address = "176.0.0.0", maskWidth = 4, lowAddress = "176.0.0.0", highAddress = "191.255.255.255"),
            Route(address = "192.0.0.0", maskWidth = 9, lowAddress = "192.0.0.0", highAddress = "192.127.255.255"),
            Route(address = "192.128.0.0", maskWidth = 11, lowAddress = "192.128.0.0", highAddress = "192.159.255.255"),
            Route(address = "192.160.0.0", maskWidth = 13, lowAddress = "192.160.0.0", highAddress = "192.167.255.255"),
            // Excluded range: 192.168.0.0 -> 192.168.255.255
            Route(address = "192.169.0.0", maskWidth = 16, lowAddress = "192.169.0.0", highAddress = "192.169.255.255"),
            Route(address = "192.170.0.0", maskWidth = 15, lowAddress = "192.170.0.0", highAddress = "192.171.255.255"),
            Route(address = "192.172.0.0", maskWidth = 14, lowAddress = "192.172.0.0", highAddress = "192.175.255.255"),
            Route(address = "192.176.0.0", maskWidth = 12, lowAddress = "192.176.0.0", highAddress = "192.191.255.255"),
            Route(address = "192.192.0.0", maskWidth = 10, lowAddress = "192.192.0.0", highAddress = "192.255.255.255"),
            Route(address = "193.0.0.0", maskWidth = 8, lowAddress = "193.0.0.0", highAddress = "193.255.255.255"),
            Route(address = "194.0.0.0", maskWidth = 7, lowAddress = "194.0.0.0", highAddress = "195.255.255.255"),
            Route(address = "196.0.0.0", maskWidth = 6, lowAddress = "196.0.0.0", highAddress = "199.255.255.255"),
            Route(address = "200.0.0.0", maskWidth = 5, lowAddress = "200.0.0.0", highAddress = "207.255.255.255"),
            Route(address = "208.0.0.0", maskWidth = 11, lowAddress = "208.0.0.0", highAddress = "208.31.255.255"),
            Route(address = "208.32.0.0", maskWidth = 12, lowAddress = "208.32.0.0", highAddress = "208.47.255.255"),
            Route(address = "208.48.0.0", maskWidth = 14, lowAddress = "208.48.0.0", highAddress = "208.51.255.255"),
            Route(address = "208.52.0.0", maskWidth = 15, lowAddress = "208.52.0.0", highAddress = "208.53.255.255"),
            // Excluded range: 208.54.0.0 -> 208.54.255.255
            Route(address = "208.55.0.0", maskWidth = 16, lowAddress = "208.55.0.0", highAddress = "208.55.255.255"),
            Route(address = "208.56.0.0", maskWidth = 13, lowAddress = "208.56.0.0", highAddress = "208.63.255.255"),
            Route(address = "208.64.0.0", maskWidth = 10, lowAddress = "208.64.0.0", highAddress = "208.127.255.255"),
            Route(address = "208.128.0.0", maskWidth = 9, lowAddress = "208.128.0.0", highAddress = "208.255.255.255"),
            Route(address = "209.0.0.0", maskWidth = 8, lowAddress = "209.0.0.0", highAddress = "209.255.255.255"),
            Route(address = "210.0.0.0", maskWidth = 7, lowAddress = "210.0.0.0", highAddress = "211.255.255.255"),
            Route(address = "212.0.0.0", maskWidth = 6, lowAddress = "212.0.0.0", highAddress = "215.255.255.255"),
            Route(address = "216.0.0.0", maskWidth = 5, lowAddress = "216.0.0.0", highAddress = "223.255.255.255"),
            // Excluded range: 224.0.0.0 -> 239.255.255.255
            Route(address = "240.0.0.0", maskWidth = 5, lowAddress = "240.0.0.0", highAddress = "247.255.255.255"),
            Route(address = "248.0.0.0", maskWidth = 6, lowAddress = "248.0.0.0", highAddress = "251.255.255.255"),
            Route(address = "252.0.0.0", maskWidth = 7, lowAddress = "252.0.0.0", highAddress = "253.255.255.255"),
            Route(address = "254.0.0.0", maskWidth = 8, lowAddress = "254.0.0.0", highAddress = "254.255.255.255"),
            Route(address = "255.0.0.0", maskWidth = 9, lowAddress = "255.0.0.0", highAddress = "255.127.255.255"),
            Route(address = "255.128.0.0", maskWidth = 10, lowAddress = "255.128.0.0", highAddress = "255.191.255.255"),
            Route(address = "255.192.0.0", maskWidth = 11, lowAddress = "255.192.0.0", highAddress = "255.223.255.255"),
            Route(address = "255.224.0.0", maskWidth = 12, lowAddress = "255.224.0.0", highAddress = "255.239.255.255"),
            Route(address = "255.240.0.0", maskWidth = 13, lowAddress = "255.240.0.0", highAddress = "255.247.255.255"),
            Route(address = "255.248.0.0", maskWidth = 14, lowAddress = "255.248.0.0", highAddress = "255.251.255.255"),
            Route(address = "255.252.0.0", maskWidth = 15, lowAddress = "255.252.0.0", highAddress = "255.253.255.255"),
            Route(address = "255.254.0.0", maskWidth = 16, lowAddress = "255.254.0.0", highAddress = "255.254.255.255"),
            Route(address = "255.255.0.0", maskWidth = 17, lowAddress = "255.255.0.0", highAddress = "255.255.127.255"),
            Route(address = "255.255.128.0", maskWidth = 18, lowAddress = "255.255.128.0", highAddress = "255.255.191.255"),
            Route(address = "255.255.192.0", maskWidth = 19, lowAddress = "255.255.192.0", highAddress = "255.255.223.255"),
            Route(address = "255.255.224.0", maskWidth = 20, lowAddress = "255.255.224.0", highAddress = "255.255.239.255"),
            Route(address = "255.255.240.0", maskWidth = 21, lowAddress = "255.255.240.0", highAddress = "255.255.247.255"),
            Route(address = "255.255.248.0", maskWidth = 22, lowAddress = "255.255.248.0", highAddress = "255.255.251.255"),
            Route(address = "255.255.252.0", maskWidth = 23, lowAddress = "255.255.252.0", highAddress = "255.255.253.255"),
            Route(address = "255.255.254.0", maskWidth = 24, lowAddress = "255.255.254.0", highAddress = "255.255.254.255"),
            Route(address = "255.255.255.0", maskWidth = 25, lowAddress = "255.255.255.0", highAddress = "255.255.255.127"),
            Route(address = "255.255.255.128", maskWidth = 26, lowAddress = "255.255.255.128", highAddress = "255.255.255.191"),
            Route(address = "255.255.255.192", maskWidth = 27, lowAddress = "255.255.255.192", highAddress = "255.255.255.223"),
            Route(address = "255.255.255.224", maskWidth = 28, lowAddress = "255.255.255.224", highAddress = "255.255.255.239"),
            Route(address = "255.255.255.240", maskWidth = 29, lowAddress = "255.255.255.240", highAddress = "255.255.255.247"),
            Route(address = "255.255.255.248", maskWidth = 30, lowAddress = "255.255.255.248", highAddress = "255.255.255.251"),
            Route(address = "255.255.255.252", maskWidth = 31, lowAddress = "255.255.255.252", highAddress = "255.255.255.253"),
            Route(address = "255.255.255.254", maskWidth = 32, lowAddress = "255.255.255.254", highAddress = "255.255.255.254"),
            // Excluded range: 255.255.255.255 -> 255.255.255.255
        )
    }
}

data class Route(
    val address: String,
    val maskWidth: Int,
    val lowAddress: String,
    val highAddress: String
)
