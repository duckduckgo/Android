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

package com.duckduckgo.privacy.config.internal

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import javax.inject.Inject

/** Shared forced-load path for Developer Settings and adb commands. */
@SingleInstanceIn(AppScope::class)
class PrivacyConfigInternalLoader @Inject constructor(
    private val downloader: PrivacyConfigDownloader,
    private val store: DevPrivacyConfigSettingsDataStore,
) {
    private val mutex = Mutex()

    suspend fun load(): PrivacyConfigDownloader.ConfigDownloadResult = mutex.withLock { forceLoad() }

    suspend fun handleCommand(action: String?, url: String?): Boolean = mutex.withLock {
        when (action) {
            ACTION_SET_URL -> {
                if (!isValidUrl(url)) return@withLock false
                store.remotePrivacyConfigUrl = url
                store.useCustomPrivacyConfigUrl = true
                store.canUrlBeChanged = true
            }
            ACTION_RESET -> {
                store.useCustomPrivacyConfigUrl = false
                store.remotePrivacyConfigUrl = null
                store.canUrlBeChanged = false
            }
            else -> return@withLock false
        }
        forceLoad() is PrivacyConfigDownloader.ConfigDownloadResult.Success
    }

    private suspend fun forceLoad(): PrivacyConfigDownloader.ConfigDownloadResult {
        // The downloader resets version and ETag under its shared download lock.
        return downloader.download(force = true)
    }

    companion object {
        const val ACTION_SET_URL = "com.duckduckgo.privacy.config.internal.SET_URL"
        const val ACTION_RESET = "com.duckduckgo.privacy.config.internal.RESET"
        const val EXTRA_URL = "url"

        fun isValidUrl(url: String?): Boolean = runCatching {
            val uri = URI(url ?: return false)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrEmpty() &&
                uri.port in -1..65535 && url.none { it.isWhitespace() }
        }.getOrDefault(false)
    }
}
