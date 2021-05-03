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
import com.duckduckgo.app.feedback.api.FireAndForgetFeedbackSubmitter
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import java.util.*

data class Bookmark(val title: String, val url: String)

interface BookmarksManager {
    suspend fun import(uri: Uri): ImportBookmarksResult
    suspend fun export(uri: Uri): ExportBookmarksResult
}

class RealBookmarksManager constructor(
    private val bookmarksImporter: BookmarksImporter,
    private val bookmarksExporter: BookmarksExporter,
    private val pixel: Pixel
) : BookmarksManager {

    override suspend fun export(uri: Uri): ExportBookmarksResult {
        val result = bookmarksExporter.export(uri)
        when (result){
            is ExportBookmarksResult.Error -> {
                pixel.fire(AppPixelName.BOOKMARK_EXPORT_SUCCESS)
            }
            ExportBookmarksResult.NoBookmarksExported -> {}
            ExportBookmarksResult.Success -> {
                pixel.fire(AppPixelName.BOOKMARK_EXPORT_ERROR)
            }
        }
        return result
    }

    override suspend fun import(uri: Uri): ImportBookmarksResult {
        val result = bookmarksImporter.import(uri)
        when (result) {
            is ImportBookmarksResult.Error -> {
                pixel.fire(AppPixelName.BOOKMARK_IMPORT_ERROR)
            }
            is ImportBookmarksResult.Success -> {
                val pixelName = String.format(
                    Locale.US, AppPixelName.BOOKMARK_IMPORT_SUCCESS.pixelName,
                    result.bookmarks.size)
                pixel.fire(pixelName)
            }
        }
        return result
    }
}
