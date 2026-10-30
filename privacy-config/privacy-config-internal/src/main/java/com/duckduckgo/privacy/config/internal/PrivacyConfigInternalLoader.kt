/*
 * Copyright (c) 2026 DuckDuckGo
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
import com.duckduckgo.privacy.config.api.PRIVACY_REMOTE_CONFIG_URL
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import javax.inject.Inject

sealed interface OverrideCommand {
    data class SetUrl(val url: String?) : OverrideCommand
    data object Reset : OverrideCommand
}

/** Shared forced-load path for Developer Settings and adb commands. */
@SingleIn(AppScope::class)
class PrivacyConfigInternalLoader @Inject constructor(
    private val downloader: PrivacyConfigDownloader,
    private val store: DevPrivacyConfigSettingsDataStore,
    private val repository: PrivacyConfigRepository,
    private val marker: PrivacyConfigOverrideMarker,
) {
    // Keeps a command's settings change and its download together when Developer Settings loads at the same time.
    private val mutex = Mutex()

    suspend fun load(): ConfigDownloadResult = mutex.withLock { forceLoad() }

    suspend fun handleCommand(command: OverrideCommand) = mutex.withLock {
        val line = try {
            when (command) {
                is OverrideCommand.SetUrl -> setUrl(command.url)
                OverrideCommand.Reset -> reset()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when (command) {
                is OverrideCommand.SetUrl -> "$FAILED action=set_url reason=download_failed source=${command.url.orEmpty()}"
                OverrideCommand.Reset -> "$FAILED action=reset reason=download_failed source=$PRIVACY_REMOTE_CONFIG_URL"
            }
        }
        marker.emit(line)
    }

    private suspend fun setUrl(url: String?): String {
        if (!isValidUrl(url)) {
            return "$FAILED action=set_url reason=invalid_url source=${url.orEmpty().filterNot { it.isWhitespace() }}"
        }
        store.remotePrivacyConfigUrl = url
        store.useCustomPrivacyConfigUrl = true
        if (forceLoad() is ConfigDownloadResult.Error) {
            return "$FAILED action=set_url reason=download_failed source=$url"
        }
        return "$APPLIED ${storedConfig()} source=$url"
    }

    private suspend fun reset(): String {
        store.useCustomPrivacyConfigUrl = false
        store.remotePrivacyConfigUrl = null
        if (forceLoad() is ConfigDownloadResult.Error) {
            return "$FAILED action=reset reason=download_failed source=$PRIVACY_REMOTE_CONFIG_URL"
        }
        return "$RESET ${storedConfig()}"
    }

    private suspend fun forceLoad(): ConfigDownloadResult = downloader.download(force = true)

    private fun storedConfig(): String {
        val stored = repository.get()
        return "version=${stored?.version} etag=${stored?.eTag.orEmpty()}"
    }

    companion object {
        private const val APPLIED = "CONFIG_OVERRIDE_APPLIED"
        private const val RESET = "CONFIG_OVERRIDE_RESET"
        private const val FAILED = "CONFIG_OVERRIDE_FAILED"

        private fun isValidUrl(url: String?): Boolean = runCatching {
            // URI rejects whitespace anywhere in the string.
            val uri = URI(url ?: return false)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrEmpty() && uri.port in -1..65535
        }.getOrDefault(false)
    }
}
