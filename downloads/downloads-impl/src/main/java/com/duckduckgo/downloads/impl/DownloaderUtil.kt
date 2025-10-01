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

package com.duckduckgo.downloads.impl

import android.net.Uri
import android.webkit.MimeTypeMap
import logcat.logcat
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.jvm.Throws

object DownloaderUtil {

    private const val FILENAME_PATTERN = "\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|[^;]*)\\s*"

    private const val INLINE_ATTACHMENT_PATTERN = "(inline|attachment)\\s*;"

    // Slightly different pattern than the one used in URLUtil (also contains `inline`).
    private val CONTENT_DISPOSITION_PATTERN =
        Pattern.compile(
            "${INLINE_ATTACHMENT_PATTERN}\\s*filename[*]?\\s*=$FILENAME_PATTERN",
            Pattern.CASE_INSENSITIVE,
        )

    private val ENCODED_FILENAME_CONTENT_DISPOSITION_PATTERN = Pattern.compile(
        "${INLINE_ATTACHMENT_PATTERN}\\s*filename\\*\\s*=\\s*([a-z0-9\\-]+)'\\s*'$FILENAME_PATTERN",
        Pattern.CASE_INSENSITIVE,
    )

    private val ENCODED_FILENAME_REGEX =
        Pattern.compile(
            "\\s*;?\\s*filename\\*\\s*=\\s*([a-z0-9\\-]+)'\\s*'$FILENAME_PATTERN",
            Pattern.CASE_INSENSITIVE,
        ).toRegex()

    private val PLAIN_FILENAME_REGEX =
        Pattern.compile(
            "\\s*;?\\s*filename\\s*=\\s*$FILENAME_PATTERN",
            Pattern.CASE_INSENSITIVE,
        ).toRegex()

    // Generic values for the Content-Type header used to enforce the decision to take the file extension from the URL if possible.
    private val GENERIC_CONTENT_TYPES = setOf(
        "application/octet-stream",
        "application/unknown",
        "binary/octet-stream",
    )

    // Inspired from UrlUtil.guessFileName and adapted to fix some scenarios highlighted in tests.
    fun guessFileName(url: String?, contentDisposition: String?, mimeType: String?): String {
        var filename: String? = null

        if (contentDisposition != null) {
            filename = fileNameFromContentDisposition(contentDisposition)
        }

        if (filename.isNullOrEmpty()) {
            filename = fileNameFromUrl(url)
        }

        // Dummy name if we couldn't extract it.
        if (filename.isNullOrEmpty()) {
            filename = "downloadfile"
        }

        return filenameWithExtension(filename, mimeType)
    }

    private fun filenameWithExtension(initialFilename: String, mimeType: String?): String {
        var filename = initialFilename
        val extension: String?

        val dotIndex = filename.lastIndexOf('.')
        if (dotIndex < 0) {
            // The filename has no extension. Try to extract the extension from mime type, if possible.
            extension = getExtensionFromMimeType(mimeType)
        } else {
            // The filename has an extension. Check it against mime type.
            val fileExtension = if (!filename.endsWith('.')) filename.substring(dotIndex + 1) else ""
            extension = getExtensionFromMimeTypeAndFileExtension(mimeType, fileExtension)
            filename = filename.substring(0, dotIndex)
        }

        return filename.sanitizeFileName() + extension
    }

    private fun getExtensionFromMimeType(mimeType: String?): String {
        if (mimeType == null) {
            return ".bin"
        }

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) {
            return ".$extension"
        }

        if (mimeType.lowercase(Locale.ROOT).startsWith("text/")) {
            return if (mimeType.equals("text/html", ignoreCase = true)) {
                ".html"
            } else {
                ".txt"
            }
        }

        return ".bin"
    }

    private fun getExtensionFromMimeTypeAndFileExtension(mimeType: String?, fileExtension: String): String {
        if (mimeType == null) {
            return ".$fileExtension"
        }

        // We have a mime type, check if the extension matches it.
        val extensionFromMimeType = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        val mimeTypeFromFileExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)

        if (mimeTypeFromFileExtension != null) {
            logcat { "File extension $fileExtension matched mime type $mimeTypeFromFileExtension." }
            return ".$fileExtension"
        }

        return if (fileExtension == extensionFromMimeType || extensionFromMimeType == null || GENERIC_CONTENT_TYPES.contains(mimeType)) {
            ".$fileExtension"
        } else {
            ".$extensionFromMimeType"
        }
    }

    private fun fileNameFromUrl(url: String?): String? {
        var filename: String? = null
        var decodedUrl = Uri.decode(url)

        if (decodedUrl == null) {
            return filename
        }

        val queryIndex = decodedUrl.indexOf('?')
        if (queryIndex > 0) {
            decodedUrl = decodedUrl.substring(0, queryIndex)
        }

        if (!decodedUrl.endsWith("/")) {
            filename = decodedUrl.substringAfterLast('/')
        }

        return filename
    }

    fun fileNameFromContentDisposition(contentDisposition: String): String? {
        val filename = try {
            parseEncodedFilenameContentDisposition(removePlainFilename(contentDisposition))
                ?: parseContentDisposition(contentDisposition) ?: return null
        } catch (ex: UnsupportedEncodingException) {
            parseContentDisposition(removeEncodedFilename(contentDisposition)) ?: return null
        }

        return if (!filename.endsWith("/")) {
            filename.substringAfterLast('/')
        } else {
            null
        }
    }

    private fun parseContentDisposition(contentDisposition: String): String? {
        try {
            val m: Matcher = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition)
            if (m.find()) {
                return m.group(2)?.replace("\"", "")
            }
        } catch (ex: IllegalStateException) {
            // This function is defined as returning null when it can't parse the header
        }
        return null
    }

    @Throws(UnsupportedEncodingException::class)
    private fun parseEncodedFilenameContentDisposition(contentDisposition: String): String? {
        try {
            val m: Matcher = ENCODED_FILENAME_CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition)
            if (m.find()) {
                val encoding = m.group(2)
                return URLDecoder.decode(m.group(3), encoding).replace("\"", "")
            }
        } catch (ex: IllegalStateException) {
            // This function is defined as returning null when it can't parse the header
        }
        return null
    }

    private fun removeEncodedFilename(contentDisposition: String): String {
        return contentDisposition.replace(ENCODED_FILENAME_REGEX, "")
    }

    private fun removePlainFilename(contentDisposition: String): String {
        return contentDisposition.replace(PLAIN_FILENAME_REGEX, "")
    }

    private fun String.sanitizeFileName(): String {
        return this.replace('*', '_').replace(" ", "_")
    }
}
