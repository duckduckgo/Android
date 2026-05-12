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

package com.duckduckgo.pir.impl.brokers

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okio.source
import javax.inject.Inject

interface BundledBrokerDataLoader {
    /**
     * Loads broker JSON data from bundled assets.
     * Only processes files whose version has changed or has not existed prior.
     */
    suspend fun loadBundledBrokerData()
}

@ContributesBinding(AppScope::class)
class RealBundledBrokerDataLoader @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val brokerJsonProcessor: BrokerJsonProcessor,
) : BundledBrokerDataLoader {

    override suspend fun loadBundledBrokerData() {
        withContext(dispatcherProvider.io()) {
            logcat { "PIR-update: Loading bundled broker data as fallback" }

            val bundledFileNames = runCatching { context.assets.list(BROKERS_ASSET_DIR)?.toList() }
                .getOrNull()
                .orEmpty()
                .filter { it.endsWith(".json") }

            if (bundledFileNames.isEmpty()) {
                logcat(ERROR) { "PIR-update: No bundled broker assets found" }
                return@withContext
            }

            for (fileName in bundledFileNames) {
                val source = runCatching { context.assets.open("$BROKERS_ASSET_DIR/$fileName").source() }
                    .onFailure { logcat(ERROR) { "PIR-update: Failed to read bundled asset $fileName: $it" } }
                    .getOrNull() ?: continue

                brokerJsonProcessor.processAndStoreBroker(fileName, source)
            }
        }
    }

    companion object {
        private const val BROKERS_ASSET_DIR = "brokers"
    }
}
