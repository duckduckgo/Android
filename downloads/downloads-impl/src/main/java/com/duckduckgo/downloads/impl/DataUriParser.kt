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

package com.duckduckgo.downloads.impl

import android.webkit.MimeTypeMap
import com.duckduckgo.downloads.impl.DataUriParser.ParseResult.ParsedDataUri
import java.nio.charset.StandardCharsets
import java.util.*
import javax.inject.Inject
import okio.ByteString.Companion.decodeBase64

class DataUriParser @Inject constructor() {

    fun generate(url: String, fileName: String? = null): ParseResult {
        val offset = url.indexOf(',')
        if (offset == -1) {
            return ParseResult.Invalid
        }

        val data = url.substring(offset + 1)
        if (data.isEmpty()) {
            return ParseResult.Invalid
        }

        val prefix = url.substring(0, offset)
        val result = MIME_TYPE_REGEX.find(prefix)
        if (result == null || result.groupValues.size < 5) {
            return ParseResult.Invalid
        }

        val mimeType = result.groupValues[REGEX_GROUP_MIMETYPE]
        val fileTypeGeneral = result.groupValues[REGEX_GROUP_FILE_TYPE_GENERAL]
        val fileTypeSpecific = result.groupValues[REGEX_GROUP_FILE_TYPE_SPECIFIC]

        val suffix = parseSuffix(mimeType, url, data)
        val filename = fileName ?: UUID.randomUUID().toString()
        val generatedFilename = GeneratedFilename(name = filename, fileType = suffix)

        return ParsedDataUri(fileTypeGeneral, fileTypeSpecific, data, mimeType, generatedFilename)
    }

    private fun parseSuffix(mimeType: String, url: String, data: String): String {
        val baseMimeType = mimeType.substringBefore(';')

        // MimeTypeMap returns the wrong value for "jpeg" types on some OS versions.
        if (baseMimeType == "image/jpeg") return "jpg"

        if ((baseMimeType == "text/plain" || baseMimeType == "application/octet-stream") && url.contains("base64", ignoreCase = true)) {
            val dataPart = data.take(if (data.length > MAX_LENGTH_FOR_MIME_TYPE_DETECTION) MAX_LENGTH_FOR_MIME_TYPE_DETECTION else data.length)
            val suffix = determineSuffixFromUrlPart(dataPart)
            if (suffix != null) {
                return suffix
            }
        }

        return MimeTypeMap.getSingleton().getExtensionFromMimeType(baseMimeType) ?: ""
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
        private val MIME_TYPE_REGEX = Regex("data:(((.+)/(.+))?;.*)?")

        private const val REGEX_GROUP_MIMETYPE = 2
        private const val REGEX_GROUP_FILE_TYPE_GENERAL = 3
        private const val REGEX_GROUP_FILE_TYPE_SPECIFIC = 4

        private const val MAX_LENGTH_FOR_MIME_TYPE_DETECTION = 20
    }

    sealed class ParseResult {

        data object Invalid : ParseResult()

        data class ParsedDataUri(
            val fileTypeGeneral: String,
            val fileTypeSpecific: String,
            val data: String,
            val mimeType: String,
            val filename: GeneratedFilename,
        ) : ParseResult()
    }

    data class GeneratedFilename(
        val name: String,
        val fileType: String = "",
    ) {

        override fun toString(): String {
            if (fileType.isBlank()) {
                return name
            }
            return "$name.$fileType"
        }
    }
}
