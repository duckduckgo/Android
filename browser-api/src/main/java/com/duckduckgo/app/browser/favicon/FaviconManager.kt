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

package com.duckduckgo.app.browser.favicon

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import java.io.File

interface FaviconManager {
    suspend fun storeFavicon(
        tabId: String,
        faviconSource: FaviconSource,
    ): File?

    suspend fun tryFetchFaviconForUrl(
        tabId: String,
        url: String,
    ): File?

    suspend fun tryFetchFaviconForUrl(
        url: String,
    ): Bitmap?

    suspend fun persistCachedFavicon(
        tabId: String,
        url: String,
    )

    suspend fun loadToViewMaybeFromRemoteWithPlaceholder(
        url: String,
        view: ImageView,
        placeholder: String? = null,
    )

    suspend fun loadToViewFromLocalWithPlaceholder(
        tabId: String? = null,
        url: String,
        view: ImageView,
        placeholder: String? = null,
    )

    suspend fun loadFromDisk(
        tabId: String?,
        url: String,
    ): Bitmap?

    suspend fun loadFromDiskWithParams(
        tabId: String? = null,
        url: String,
        cornerRadius: Int,
        width: Int,
        height: Int,
    ): Bitmap?

    suspend fun deletePersistedFavicon(url: String)
    suspend fun deleteOldTempFavicon(
        tabId: String,
        path: String?,
    )

    suspend fun deleteAllTemp()

    /**
     * Generates a drawable which can be used as a placeholder for a favicon when a real one cannot be found
     * @param placeholder the placeholder text to be used. if null, the placeholder letter will be extracted from the domain
     * @param domain the domain of the site for which the favicon is being generated, used to generate background color
     */
    fun generateDefaultFavicon(
        placeholder: String?,
        domain: String,
    ): Drawable

    suspend fun persistFavicon(
        favicon: Bitmap,
        url: String
    )
}

sealed class FaviconSource {
    data class ImageFavicon(
        val icon: Bitmap,
        val url: String,
    ) : FaviconSource()

    data class UrlFavicon(
        val faviconUrl: String,
        val url: String,
    ) : FaviconSource()
}
