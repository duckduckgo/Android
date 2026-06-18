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

package com.duckduckgo.duckchat.impl.repository

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface AddressBarPickerAttributionRepository {

    /** Records that the user chose Search + Duck.ai on the new address bar picker. */
    suspend fun onPickerDuckAiSelected()

    /**
     * One-shot. Returns `true` when the next native-input open should be attributed to the picker — i.e.
     * a selection is pending and still within the attribution window. Consumes the pending marker either way.
     */
    fun consumeAttributionToPicker(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAddressBarPickerAttributionRepository @Inject constructor(
    private val duckChatDataStore: DuckChatDataStore,
    private val currentTimeProvider: CurrentTimeProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : AddressBarPickerAttributionRepository {

    // In-memory cache so consumeAttributionToPicker() can stay synchronous (the native-input URL is built
    // synchronously). Hydrated from disk at startup to survive a process restart within the window.
    @Volatile
    private var selectedAtMillis: Long? = null

    init {
        appCoroutineScope.launch(dispatchers.io()) {
            selectedAtMillis = duckChatDataStore.getAddressBarPickerSelectedAt()
        }
    }

    override suspend fun onPickerDuckAiSelected() {
        val now = currentTimeProvider.currentTimeMillis()
        selectedAtMillis = now
        duckChatDataStore.storeAddressBarPickerSelectedAt(now)
    }

    override fun consumeAttributionToPicker(): Boolean {
        val selectedAt = selectedAtMillis ?: return false
        selectedAtMillis = null
        appCoroutineScope.launch(dispatchers.io()) { duckChatDataStore.clearAddressBarPickerSelectedAt() }
        return (currentTimeProvider.currentTimeMillis() - selectedAt) in 0..ATTRIBUTION_WINDOW_MS
    }

    private companion object {
        private val ATTRIBUTION_WINDOW_MS = TimeUnit.HOURS.toMillis(24)
    }
}
