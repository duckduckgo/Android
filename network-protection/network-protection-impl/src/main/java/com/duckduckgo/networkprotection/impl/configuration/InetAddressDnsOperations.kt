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

package com.duckduckgo.networkprotection.impl.configuration

import java.net.InetAddress

/**
 * The block malware DNS IP address is a <<1 bit-wise operations on the last octet based on the default DNS
 * This method assumes the [InetAddress] passed in as parameter is the default DNS.
 *
 * You should only
 */
internal fun InetAddress.computeBlockMalwareDnsOrSame(): InetAddress {
    return kotlin.runCatching {
        // Perform <<1 operation on the last octet
        // Since byte is signed in Kotlin/Java, we mask it with 0xFF to treat it as unsigned
        val newLastOctet = (address.last().toInt() and 0xFF) shl 1

        val newIPAddress = address
        // Update the last octet in the byte array
        newIPAddress[newIPAddress.size - 1] = (newLastOctet and 0xFF).toByte() // Ensure it stays within byte range

        InetAddress.getByAddress(newIPAddress)
    }.getOrNull() ?: this
}
