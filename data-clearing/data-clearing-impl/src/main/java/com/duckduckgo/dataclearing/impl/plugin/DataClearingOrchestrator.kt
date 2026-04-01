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

package com.duckduckgo.dataclearing.impl.plugin

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.dataclearing.api.plugin.ClearResult
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.dataclearing.api.plugin.DataClearingTrigger
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataClearingOrchestrator @Inject constructor(
    private val plugins: PluginPoint<DataClearingPlugin>,
) : DataClearingTrigger {

    override suspend fun clearData(types: Set<ClearableData>) {
        plugins.getPlugins().forEach { plugin ->
            val result = try {
                plugin.onClearData(types)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                ClearResult.Failure(e)
            }
            if (result is ClearResult.Failure) {
                logcat(ERROR) { "Plugin ${plugin::class.simpleName} failed: ${result.error.asLog()}" }
            }
        }
    }
}
