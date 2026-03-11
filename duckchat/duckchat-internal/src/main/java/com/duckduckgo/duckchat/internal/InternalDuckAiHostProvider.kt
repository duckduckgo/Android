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

package com.duckduckgo.duckchat.internal

import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.internal.store.DuckAiInternalSettingsDataStore
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class InternalDuckAiHostProvider @Inject constructor(
    private val dataStore: DuckAiInternalSettingsDataStore,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : DuckAiHostProvider {

    @Volatile
    private var cachedHost: String? = null

    init {
        appScope.launch(dispatcherProvider.io()) {
            cachedHost = dataStore.customUrl?.extractHost()
        }
    }

    override fun getHost(): String = cachedHost ?: super.getHost()

    fun getCustomUrl(): String? = dataStore.customUrl

    fun setCustomUrl(url: String?) {
        cachedHost = url?.extractHost()
        appScope.launch(dispatcherProvider.io()) {
            dataStore.customUrl = url
        }
    }

    private fun String.extractHost(): String? {
        val withScheme = if ("://" in this) this else "https://$this"
        return withScheme.toUri().host
    }
}
