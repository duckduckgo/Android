/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

import java.nio.charset.StandardCharsets

interface HostnameHeaderExtractor {

    fun extract(payload: ByteArray): String?
}

class PlaintextHostHeaderExtractor : HostnameHeaderExtractor {

    override fun extract(payload: ByteArray): String? {
        val hostIdx = payload.indexOf(HOST_HEADER_PREFIX)
        if (hostIdx >= 0) {
            val hostEndIdx = payload.indexOf(CARRIAGE_RETURN, hostIdx)
            if (hostEndIdx > hostIdx) {
                val hostLen = hostEndIdx - hostIdx - CARRIAGE_RETURN.size
                val hostname = String(payload, hostIdx, hostLen, StandardCharsets.US_ASCII)
                if (hostname.isNotEmpty()) {
                    return hostname
                }
            }
        }

        return null
    }

    companion object {
        private val HOST_HEADER_PREFIX = "\nHost: ".toByteArray(StandardCharsets.US_ASCII)
        private val CARRIAGE_RETURN = "\r\n".toByteArray(StandardCharsets.US_ASCII)
    }
}
