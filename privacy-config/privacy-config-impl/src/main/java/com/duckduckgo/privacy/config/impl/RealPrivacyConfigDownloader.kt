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
import com.duckduckgo.app.global.extensions.extractETag
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult.Error
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult.Success
import com.duckduckgo.privacy.config.impl.network.PrivacyConfigService
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

/** Public interface for download remote privacy config */
interface PrivacyConfigDownloader {
    /**
     * This method will download remote config and returns the [ConfigDownloadResult]
     * @return [ConfigDownloadResult.Success] if remote config has been downloaded correctly or
     * [ConfigDownloadResult.Error] otherwise.
     */
    suspend fun download(): ConfigDownloadResult

    sealed class ConfigDownloadResult {
        object Success : ConfigDownloadResult()
        data class Error(val error: String?) : ConfigDownloadResult()
    }
}

@WorkerThread
@ContributesBinding(AppScope::class)
class RealPrivacyConfigDownloader @Inject constructor(
    private val privacyConfigService: PrivacyConfigService,
    private val privacyConfigPersister: PrivacyConfigPersister,
    private val privacyConfigCallbacks: PluginPoint<PrivacyConfigCallbackPlugin>,
) : PrivacyConfigDownloader {

    override suspend fun download(): PrivacyConfigDownloader.ConfigDownloadResult {
        Timber.d("Downloading privacy config")
        val response = runCatching {
            privacyConfigService.privacyConfig()
        }.onSuccess { response ->
            val eTag = response.headers().extractETag()
            response.body()?.let {
                runCatching {
                    privacyConfigPersister.persistPrivacyConfig(it, eTag)
                    privacyConfigCallbacks.getPlugins().forEach { callback -> callback.onPrivacyConfigDownloaded() }
                }.onFailure {
                    return Error(it.localizedMessage)
                }
            }
        }.onFailure {
            Timber.w(it.localizedMessage)
        }

        return if (response.isFailure) {
            Error(response.exceptionOrNull()?.localizedMessage)
        } else {
            Success
        }
    }
}
