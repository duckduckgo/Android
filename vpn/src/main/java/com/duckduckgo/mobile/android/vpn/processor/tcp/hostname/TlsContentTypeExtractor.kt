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

import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.TlsMessageDetector.Companion.CONTENT_TYPE_APPLICATION
import timber.log.Timber
import javax.inject.Inject

interface ContentTypeExtractor {
    fun isTlsApplicationData(bytes: ByteArray): TlsContentType
}

class TlsContentTypeExtractor @Inject constructor(private val tlsMessageDetector: TlsMessageDetector) : ContentTypeExtractor {

    override fun isTlsApplicationData(bytes: ByteArray): TlsContentType {

        try {
            if (!tlsMessageDetector.isTlsMessage(bytes)) {
                return TlsContentType.Undetermined
            }

            return extractContentType(bytes)
        } catch (t: Throwable) {
            Timber.w(t, "Failed to extract TLS content type")
            return TlsContentType.Undetermined
        }

    }

    private fun extractContentType(packet: ByteArray): TlsContentType {
        return if (packet[0].toInt() == CONTENT_TYPE_APPLICATION) TlsContentType.TlsApplicationData else TlsContentType.NotApplicationData
    }
}

sealed class TlsContentType {
    object TlsApplicationData : TlsContentType()
    object NotApplicationData : TlsContentType()
    object Undetermined : TlsContentType()
}
