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

package com.duckduckgo.privacy.config.impl

import androidx.annotation.WorkerThread
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.extensions.extractETag
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult.Error
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult.Success
import com.duckduckgo.privacy.config.impl.RealPrivacyConfigDownloader.DownloadError.DOWNLOAD_ERROR
import com.duckduckgo.privacy.config.impl.RealPrivacyConfigDownloader.DownloadError.EMPTY_CONFIG_ERROR
import com.duckduckgo.privacy.config.impl.RealPrivacyConfigDownloader.DownloadError.STORE_ERROR
import com.duckduckgo.privacy.config.impl.network.PrivacyConfigService
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.WARN
import logcat.logcat
import retrofit2.HttpException
import javax.inject.Inject

/** Public interface for download remote privacy config */
interface PrivacyConfigDownloader {
    /**
     * This method will download remote config and returns the [ConfigDownloadResult]
     * @return [ConfigDownloadResult.Success] if remote config has been downloaded correctly or
     * [ConfigDownloadResult.Error] otherwise.
     */
    suspend fun download(): ConfigDownloadResult

    sealed class ConfigDownloadResult {
        data object Success : ConfigDownloadResult()
        data class Error(val error: String?) : ConfigDownloadResult()
    }
}

@WorkerThread
@ContributesBinding(AppScope::class)
class RealPrivacyConfigDownloader @Inject constructor(
    private val privacyConfigService: PrivacyConfigService,
    private val privacyConfigPersister: PrivacyConfigPersister,
    private val privacyConfigCallbacks: PluginPoint<PrivacyConfigCallbackPlugin>,
    private val pixel: Pixel,
) : PrivacyConfigDownloader {

    override suspend fun download(): PrivacyConfigDownloader.ConfigDownloadResult {
        logcat { "Downloading privacy config" }
        return Error(null) // Default to error state, only return success at the end of the method when everything has worked

        val response = runCatching {
            privacyConfigService.privacyConfig()
        }.onSuccess { response ->
            val eTag = response.headers().extractETag()
            response.body()?.let {
                runCatching {
                    privacyConfigPersister.persistPrivacyConfig(it, eTag)
                    privacyConfigCallbacks.getPlugins().forEach { callback -> callback.onPrivacyConfigDownloaded() }
                }.onFailure {
                    // error parsing remote config
                    notifyErrorToCallbacks(STORE_ERROR)
                    return Error(it.localizedMessage)
                }
            } ?: run {
                // empty response
                notifyErrorToCallbacks(EMPTY_CONFIG_ERROR)
                return Error(null)
            }
        }.onFailure {
            // error downloading remote config
            val code = if (it is HttpException) {
                it.code().toString()
            } else {
                null
            }
            val params = mapOf(
                "code" to (code ?: "unknown"),
                "message" to (it.localizedMessage ?: "unknown"),
            )
            logcat(WARN) { it.localizedMessage ?: "unknown" }
            notifyErrorToCallbacks(DOWNLOAD_ERROR, params)
        }

        return if (response.isFailure) {
            // error downloading remote config
            Error(response.exceptionOrNull()?.localizedMessage)
        } else {
            Success
        }
    }

    private fun notifyErrorToCallbacks(reason: DownloadError, params: Map<String, String> = emptyMap()) {
        pixel.fire(reason.pixelName, params)
    }

    private enum class DownloadError(val pixelName: String) {
        DOWNLOAD_ERROR("m_privacy_config_download_error"),
        STORE_ERROR("m_privacy_config_store_error"),
        EMPTY_CONFIG_ERROR("m_privacy_config_empty_error"),
    }
}
