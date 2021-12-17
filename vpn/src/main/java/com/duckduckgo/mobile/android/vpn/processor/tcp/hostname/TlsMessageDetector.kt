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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

import javax.inject.Inject

class TlsMessageDetector @Inject constructor() {

    fun isTlsMessage(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false

        val contentType = bytes[0].toInt()
        return isKnownTlsContentType(contentType)
    }

    private fun isKnownTlsContentType(contentType: Int): Boolean {
        return when (contentType) {
            CONTENT_TYPE_CHANGE_CIPHER_SPEC,
            CONTENT_TYPE_ALERT,
            CONTENT_TYPE_HANDSHAKE,
            CONTENT_TYPE_APPLICATION,
            CONTENT_TYPE_HEARTBEAT -> true
            else -> false
        }
    }

    companion object {
        const val CONTENT_TYPE_CHANGE_CIPHER_SPEC = 20
        const val CONTENT_TYPE_ALERT = 21
        const val CONTENT_TYPE_HANDSHAKE = 22
        const val CONTENT_TYPE_APPLICATION = 23
        const val CONTENT_TYPE_HEARTBEAT = 24
    }
}
