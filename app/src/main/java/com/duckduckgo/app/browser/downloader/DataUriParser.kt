/*
 * Copyright (c) 2018 DuckDuckGo
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
import com.duckduckgo.app.browser.downloader.DataUriParser.ParseResult.ParsedDataUri
import okio.ByteString.Companion.decodeBase64
import java.nio.charset.StandardCharsets
import java.util.*
import javax.inject.Inject

class DataUriParser @Inject constructor() {

    fun generate(url: String): ParseResult {
        val result = MIME_TYPE_REGEX.find(url)
        if (result == null || result.groupValues.size < 5) {
            return ParseResult.Invalid
        }

        val mimeType = result.groupValues[REGEX_GROUP_MIMETYPE]
        val fileTypeGeneral = result.groupValues[REGEX_GROUP_FILE_TYPE_GENERAL]
        val fileTypeSpecific = result.groupValues[REGEX_GROUP_FILE_TYPE_SPECIFIC]
        val data = result.groupValues[REGEX_GROUP_DATA]

        val suffix = parseSuffix(mimeType, url, data)
        val filename = UUID.randomUUID().toString()
        val generatedFilename = GeneratedFilename(name = filename, fileType = suffix)

        return ParsedDataUri(fileTypeGeneral, fileTypeSpecific, data, mimeType, generatedFilename)
    }

    private fun parseSuffix(mimeType: String, url: String, data: String): String {
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
        private val MIME_TYPE_REGEX = Regex("data:(((.+)/(.+))?;.*)?,(.+)")

        private const val REGEX_GROUP_MIMETYPE = 2
        private const val REGEX_GROUP_FILE_TYPE_GENERAL = 3
        private const val REGEX_GROUP_FILE_TYPE_SPECIFIC = 4
        private const val REGEX_GROUP_DATA = 5

        private const val MAX_LENGTH_FOR_MIME_TYPE_DETECTION = 20
    }

    sealed class ParseResult {

        object Invalid : ParseResult()

        data class ParsedDataUri(
            val fileTypeGeneral: String,
            val fileTypeSpecific: String,
            val data: String,
            val mimeType: String,
            val filename: GeneratedFilename
        ) : ParseResult()
    }

    data class GeneratedFilename(
        val name: String,
        val fileType: String = ""
    ) {

        override fun toString(): String {
            if (fileType.isBlank()) {
                return name
            }
            return "$name.$fileType"
        }
    }
}
