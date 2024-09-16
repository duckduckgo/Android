/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.utils.network

import java.net.Inet4Address
import java.net.InetAddress

fun InetAddress.isCGNATed(): Boolean {
    if (this !is Inet4Address) return false

    // CGNAT is 100.64.0.0 -> 100.127.255.255
    val firstOctet = address[0].toUInt()
    val secondOctet = address[1].toUInt()

    return firstOctet == 100u && (secondOctet in 64u..127u)
}
