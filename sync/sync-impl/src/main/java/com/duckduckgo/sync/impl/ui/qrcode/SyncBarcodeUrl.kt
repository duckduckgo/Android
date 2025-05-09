/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.qrcode

import androidx.core.net.toUri

data class SyncBarcodeUrl(
    val webSafeB64EncodedCode: String,
    val urlEncodedDeviceName: String? = null,
) {

    fun asUrl(): String {
        val sb = StringBuilder(URL_BASE)
            .append("&")
            .append(CODE_PARAM).append("=").append(webSafeB64EncodedCode)

        if (urlEncodedDeviceName?.isNotBlank() == true) {
            sb.append("&")
            sb.append(DEVICE_NAME_PARAM).append("=").append(urlEncodedDeviceName)
        }

        return sb.toString()
    }

    companion object {
        const val URL_BASE = "https://duckduckgo.com/sync/pairing/#"
        private const val CODE_PARAM = "code"
        private const val DEVICE_NAME_PARAM = "deviceName"

        fun parseUrl(fullSyncUrl: String): SyncBarcodeUrl? {
            return kotlin.runCatching {
                if (!fullSyncUrl.startsWith(URL_BASE)) {
                    return null
                }

                val uri = fullSyncUrl.toUri()
                val fragment = uri.fragment ?: return null
                val fragmentParts = fragment.split("&")

                val code = fragmentParts
                    .find { it.startsWith("code=") }
                    ?.substringAfter("code=")
                    ?: return null

                val deviceName = fragmentParts
                    .find { it.startsWith("deviceName=") }
                    ?.substringAfter("deviceName=")

                SyncBarcodeUrl(webSafeB64EncodedCode = code, urlEncodedDeviceName = deviceName)
            }.getOrNull()
        }
    }
}
