/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.trafficquality.remote

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

interface AndroidFeaturesHeaderProvider {
    fun provide(): String
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealAndroidFeaturesHeaderProvider @Inject constructor(
    private val fireDataStore: FireDataStore,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : AndroidFeaturesHeaderProvider {

    // Headers are built on the main thread for every search, so the auto-delete state is kept in memory
    // and only read synchronously if a search happens before the first value has loaded.
    private val autoDeleteEnabled: StateFlow<Boolean?> = fireDataStore.getAutomaticClearOptionsFlow()
        .map { it.isNotEmpty() }
        .stateIn(appCoroutineScope + dispatcherProvider.io(), SharingStarted.Eagerly, null)

    override fun provide(): String {
        val enabled = autoDeleteEnabled.value ?: runBlocking { fireDataStore.getAutomaticClearOptions().isNotEmpty() }
        return "$AUTO_DELETE_HEADER_KEY=$enabled"
    }

    companion object {
        private const val AUTO_DELETE_HEADER_KEY = "adel_enabled"
    }
}
