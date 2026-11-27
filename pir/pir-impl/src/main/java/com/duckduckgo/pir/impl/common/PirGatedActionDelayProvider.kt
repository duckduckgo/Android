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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.common.PirJobConstants.DEFAULT_GATED_ACTION_PUSH_DELAY_MS
import com.duckduckgo.pir.impl.common.PirJobConstants.MAX_GATED_ACTION_PUSH_DELAY_MS
import com.duckduckgo.pir.impl.common.PirJobConstants.MIN_GATED_ACTION_PUSH_DELAY_MS
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

interface PirGatedActionDelayProvider {
    /**
     * Resolves how long to wait before pushing a Click or Expectation action to the page.
     */
    suspend fun getGatedActionDelayMs(): Long
}

@ContributesBinding(AppScope::class)
class RealPirGatedActionDelayProvider @Inject constructor(
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val dispatcherProvider: DispatcherProvider,
) : PirGatedActionDelayProvider {

    override suspend fun getGatedActionDelayMs(): Long = withContext(dispatcherProvider.io()) {
        runCatching {
            val settings = pirRemoteFeatures.self().getSettings() ?: return@runCatching DEFAULT_GATED_ACTION_PUSH_DELAY_MS
            val json = JSONObject(settings)
            if (!json.has(SETTINGS_KEY_GATED_ACTION_DELAY_MS)) {
                return@runCatching DEFAULT_GATED_ACTION_PUSH_DELAY_MS
            }
            json.getLong(SETTINGS_KEY_GATED_ACTION_DELAY_MS)
                .coerceIn(MIN_GATED_ACTION_PUSH_DELAY_MS, MAX_GATED_ACTION_PUSH_DELAY_MS)
        }.getOrDefault(DEFAULT_GATED_ACTION_PUSH_DELAY_MS)
    }

    companion object {
        private const val SETTINGS_KEY_GATED_ACTION_DELAY_MS = "gatedActionDelayMs"
    }
}
