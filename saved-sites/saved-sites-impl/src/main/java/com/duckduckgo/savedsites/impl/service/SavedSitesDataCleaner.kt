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

package com.duckduckgo.savedsites.impl.service

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.DeviceSyncState
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class SavedSitesDataCleaner @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val deviceSyncState: DeviceSyncState,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        // https://app.asana.com/0/69071770703008/1204375817149200/f
        coroutineScope.launch(dispatcherProvider.io()) {
            if (!deviceSyncState.isUserSignedInOnDevice()) {
                savedSitesRepository.pruneDeleted()
            }
        }
    }
}
