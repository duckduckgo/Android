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

package com.duckduckgo.savedsites.impl.sync

import com.duckduckgo.savedsites.store.FavoritesViewMode
import com.duckduckgo.savedsites.store.FavoritesViewMode.NATIVE
import com.duckduckgo.savedsites.store.SavedSitesSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class FakeSavedSitesSettingsStore(
    private val coroutineScope: CoroutineScope,
) : SavedSitesSettingsStore {
    val flow = MutableStateFlow(NATIVE)
    override var favoritesDisplayMode: FavoritesViewMode
        get() = flow.value
        set(value) {
            coroutineScope.launch {
                flow.emit(value)
            }
        }
    override fun viewModeFlow(): Flow<FavoritesViewMode> = flow
}
