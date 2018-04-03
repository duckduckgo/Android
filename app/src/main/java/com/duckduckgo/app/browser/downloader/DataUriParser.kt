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

import com.duckduckgo.app.browser.downloader.DataUriParser.ParseResult.ParsedDataUri
import timber.log.Timber
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

        val suffix = determineSuffix(fileTypeGeneral, fileTypeSpecific)
        val filename = UUID.randomUUID().toString()
        val generatedFilename = GeneratedFilename(name = filename, fileType = suffix)

        return ParsedDataUri(fileTypeGeneral, fileTypeSpecific, data, mimeType, generatedFilename)
    }

    private fun determineSuffix(fileTypeGeneral: String, fileTypeSpecific: String): String {
        return when (fileTypeGeneral) {
            FILE_TYPE_IMAGE -> determineImageType(fileTypeSpecific)
            else -> {
                Timber.i("Cannot deduce suitable file type suffix")
                return ""
            }
        }

    }

    private fun determineImageType(imageType: String): String {
        if (imageType.isEmpty() || imageType == "*") {
            return "jpg"
        }

        return imageType
    }

    companion object {
        private val MIME_TYPE_REGEX = Regex("data:(((.+)/(.+))?;.*)?,(.+)")

        private const val REGEX_GROUP_MIMETYPE = 2
        private const val REGEX_GROUP_FILE_TYPE_GENERAL = 3
        private const val REGEX_GROUP_FILE_TYPE_SPECIFIC = 4
        private const val REGEX_GROUP_DATA = 5

        const val FILE_TYPE_IMAGE = "image"
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

    data class GeneratedFilename(val name: String, val fileType: String = "") {

        override fun toString(): String {
            if (fileType.isBlank()) {
                return name
            }
            return "$name.$fileType"
        }

    }
}
