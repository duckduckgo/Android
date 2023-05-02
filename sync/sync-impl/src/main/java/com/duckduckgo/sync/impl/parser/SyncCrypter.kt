/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.parser

import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter

interface SyncCrypter {
    fun generateAllData(): SyncDataRequest
    fun store(entries: List<SyncBookmarkEntry>): Boolean

    fun encrypt(text: String): String

    fun decrypt(data: String): String
}

data class SyncBookmarkPage(val url: String)
data class SyncFolderChildren(val children: List<String>)

data class SyncBookmarkEntry(
    val id: String,
    val title: String,
    val page: SyncBookmarkPage?,
    val folder: SyncFolderChildren?,
    val deleted: String?,
    val client_last_modified: String
) {
    companion object {
        fun asBookmark(
            id: String,
            title: String,
            url: String,
            deleted: String?,
            clientLastModified: String
        ): SyncBookmarkEntry {
            return SyncBookmarkEntry(id, title, SyncBookmarkPage(url), null, deleted, clientLastModified)
        }

        fun asFolder(
            id: String,
            title: String,
            children: List<String>,
            deleted: String?,
            clientLastModified: String
        ): SyncBookmarkEntry {
            return SyncBookmarkEntry(id, title, null, SyncFolderChildren(children), deleted, clientLastModified)
        }
    }
}

fun SyncBookmarkEntry.isFolder(): Boolean = this.folder != null
fun SyncBookmarkEntry.isBookmark(): Boolean = this.page != null

class SyncDataRequest(
    val client_timestamp: String = DatabaseDateFormatter.iso8601(),
    val bookmarks: SyncBookmarksRequest
)

class SyncRequest(val bookmarks: SyncBookmarksRequest)
class SyncBookmarksRequest(
    val updates: List<SyncBookmarkEntry>,
    val modified_since: String = "0"
)
