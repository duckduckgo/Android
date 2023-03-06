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

package com.duckduckgo.sync.api.parser

interface SyncCrypter {
    suspend fun generateAllData(): SyncDataBookmarks
    fun store(entries: List<SyncEntry>): Boolean
}

data class SyncBookmarkPage(val url: String)
data class SyncFolderChildren(val children: List<String>)

data class SyncEntry(
    val id: String,
    val title: String,
    val page: SyncBookmarkPage?,
    val folder: SyncFolderChildren?,
    val deleted: String?
) {
    companion object {
        fun asBookmark(
            id: String,
            title: String,
            url: String,
            deleted: String?
        ): SyncEntry {
            return SyncEntry(id, title, SyncBookmarkPage(url), null, deleted)
        }

        fun asFolder(
            id: String,
            title: String,
            children: List<String>,
            deleted: String?
        ): SyncEntry {
            return SyncEntry(id, title, null, SyncFolderChildren(children), deleted)
        }
    }
}

fun SyncEntry.isFolder(): Boolean = this.folder != null
fun SyncEntry.isBookmark(): Boolean = this.page != null

class SyncDataBookmarks(val bookmarks: SyncDataUpdates)
class SyncDataUpdates(val updates: List<SyncEntry>)
