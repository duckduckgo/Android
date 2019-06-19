/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.fire

import android.content.Context
import com.duckduckgo.app.global.api.NetworkApiCache
import com.duckduckgo.app.global.file.FileDeleter


interface AppCacheClearer {

    suspend fun clearCache()

}

class AndroidAppCacheClearer(private val context: Context, private val fileDeleter: FileDeleter) : AppCacheClearer {

    override suspend fun clearCache() {
        fileDeleter.deleteContents(context.cacheDir, FILENAMES_EXCLUDED_FROM_DELETION)
    }

    companion object {

        /*
         * Exclude this WebView cache directory, based on warning from Firefox Focus:
         *   "If the folder or its contents are deleted, WebView will stop using the disk cache entirely."
         */
        private const val WEBVIEW_CACHE_DIR = "org.chromium.android_webview"

        /*
         * Exclude the OkHttp networking cache from being deleted. This doesn't contain any sensitive information.
         * Deleting this would just cause large amounts of non-sensitive data to have be downloaded again when app next launches.
         */
        private const val NETWORK_CACHE_DIR = NetworkApiCache.FILE_NAME

        private val FILENAMES_EXCLUDED_FROM_DELETION = listOf(
            WEBVIEW_CACHE_DIR,
            NETWORK_CACHE_DIR
        )
    }

}