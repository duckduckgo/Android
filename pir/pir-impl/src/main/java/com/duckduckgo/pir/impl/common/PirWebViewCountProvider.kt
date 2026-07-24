/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.common.PirJobConstants.DEFAULT_MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.impl.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT_CEILING
import com.duckduckgo.pir.impl.common.PirJobConstants.MIN_DETACHED_WEBVIEW_COUNT
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

interface PirWebViewCountProvider {
    /**
     * Resolves the maximum number of detached WebViews (runners) to use for the next run.
     */
    suspend fun getMaxWebViewCount(): Int
}

@ContributesBinding(AppScope::class)
class RealPirWebViewCountProvider @Inject constructor(
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val dispatcherProvider: DispatcherProvider,
) : PirWebViewCountProvider {

    override suspend fun getMaxWebViewCount(): Int = withContext(dispatcherProvider.io()) {
        runCatching {
            val settings = pirRemoteFeatures.self().getSettings() ?: return@runCatching DEFAULT_MAX_DETACHED_WEBVIEW_COUNT
            val json = JSONObject(settings)
            if (!json.has(SETTINGS_KEY_WEBVIEW_COUNT)) {
                return@runCatching DEFAULT_MAX_DETACHED_WEBVIEW_COUNT
            }
            json.getInt(SETTINGS_KEY_WEBVIEW_COUNT)
                .coerceIn(MIN_DETACHED_WEBVIEW_COUNT, MAX_DETACHED_WEBVIEW_COUNT_CEILING)
        }.getOrDefault(DEFAULT_MAX_DETACHED_WEBVIEW_COUNT)
    }

    companion object {
        private const val SETTINGS_KEY_WEBVIEW_COUNT = "detachedWebViewCount"
    }
}
