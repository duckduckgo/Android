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

package com.duckduckgo.autofill.impl.importing.takeout.zip

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.zip.ZipInputStream
import javax.inject.Inject
import logcat.logcat

interface ZipEntryContentReader {

    sealed class ReadResult {
        data class Success(val content: String) : ReadResult()
        data class Error(val exception: Exception) : ReadResult()
    }

    fun readAndValidateContent(
        zipInputStream: ZipInputStream,
        entryName: String,
    ): ReadResult
}

@ContributesBinding(AppScope::class)
class BookmarkZipEntryContentReader @Inject constructor() : ZipEntryContentReader {

    override fun readAndValidateContent(
        zipInputStream: ZipInputStream,
        entryName: String,
    ): ZipEntryContentReader.ReadResult {
        logcat { "Reading content from ZIP entry: '$entryName'" }

        return try {
            val content = readContent(zipInputStream, entryName)

            if (isValidBookmarkContent(content)) {
                logcat { "Content validation passed for: '$entryName'" }
                ZipEntryContentReader.ReadResult.Success(content)
            } else {
                logcat { "Content validation failed for: '$entryName'" }
                ZipEntryContentReader.ReadResult.Error(
                    Exception("File content is not a valid bookmark file"),
                )
            }
        } catch (e: Exception) {
            logcat { "Error reading ZIP entry content: ${e.message}" }
            ZipEntryContentReader.ReadResult.Error(e)
        }
    }

    private fun readContent(zipInputStream: ZipInputStream, entryName: String): String {
        val content = zipInputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        logcat { "Read content from '$entryName', length: ${content.length}" }
        return content
    }

    private fun isValidBookmarkContent(content: String): Boolean {
        val hasNetscapeHeader = content.contains(NETSCAPE_HEADER, ignoreCase = true)
        val hasBookmarkTitle = content.contains(BOOKMARK_TITLE, ignoreCase = true)

        logcat { "Content validation: hasNetscapeHeader=$hasNetscapeHeader, hasBookmarkTitle=$hasBookmarkTitle" }

        return hasNetscapeHeader || hasBookmarkTitle
    }

    companion object {
        private const val NETSCAPE_HEADER = "<!DOCTYPE NETSCAPE-Bookmark-file"
        private const val BOOKMARK_TITLE = "<title>Bookmarks</title>"
    }
}
