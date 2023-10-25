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

package com.duckduckgo.privacy.config.api

/** Public interface for download remote privacy config */
interface PrivacyConfigDownloader {
    /**
     * This method will download remote config and returns the [ConfigDownloadResult]
     * @return [ConfigDownloadResult.Success] if remote config has been downloaded correctly or
     * [ConfigDownloadResult.Error] otherwise.
     */
    suspend fun download(): ConfigDownloadResult
}

sealed class ConfigDownloadResult {
    object Success : ConfigDownloadResult()
    data class Error(val error: String?) : ConfigDownloadResult()
}
