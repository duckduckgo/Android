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

package com.duckduckgo.app.bookmarks.service

import android.net.Uri

data class Bookmark(val title: String, val url: String)

interface BookmarksManager {
    suspend fun import(uri: Uri): ImportBookmarksResult
    suspend fun export(uri: Uri): ExportBookmarksResult
}

class RealBookmarksManager constructor(
    private val bookmarksImporter: BookmarksImporter,
    private val bookmarksExporter: BookmarksExporter,
) : BookmarksManager {

    override suspend fun export(uri: Uri): ExportBookmarksResult {
        return bookmarksExporter.export(uri)
    }

    override suspend fun import(uri: Uri): ImportBookmarksResult {
        return bookmarksImporter.import(uri)
    }
}
