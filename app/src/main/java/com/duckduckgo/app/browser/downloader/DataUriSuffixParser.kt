/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser.downloader

import android.webkit.MimeTypeMap
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import okio.ByteString.Companion.decodeBase64
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class DataUriSuffixParser @Inject constructor() {

    fun parseSuffix(mimeType: String, url: String, data: String): String {

        // MimeTypeMap returns the wrong value for "jpeg" types on some OS versions.
        if (mimeType == "image/jpeg") return "jpg"

        if (mimeType == "text/plain" && url.contains("base64", ignoreCase = true)) {
            val dataPart = data.take(if (data.length > MAX_LENGTH_FOR_MIME_TYPE_DETECTION) MAX_LENGTH_FOR_MIME_TYPE_DETECTION else data.length)
            val suffix = determineSuffixFromUrlPart(dataPart)
            if (suffix != null) {
                return suffix
            }
        }

        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: ""
    }

    private fun determineSuffixFromUrlPart(dataPart: String): String? {
        val decodedDataPart = dataPart.decodeBase64()?.string(StandardCharsets.UTF_8) ?: ""
        return when {
            decodedDataPart.contains("PNG") -> "png"
            decodedDataPart.contains("JFIF") -> "jpg"
            decodedDataPart.startsWith("<svg", true) -> "svg"
            decodedDataPart.startsWith("GIF") -> "gif"
            decodedDataPart.startsWith("%PDF") -> "pdf"
            decodedDataPart.startsWith("RIFF") && decodedDataPart.contains("WEBP") -> "webp"
            decodedDataPart.startsWith("BM") -> "bmp"
            else -> null
        }
    }

    companion object {
        const val MAX_LENGTH_FOR_MIME_TYPE_DETECTION = 20
    }
}
