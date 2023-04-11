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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.api.BrowserFeatureStateReporterPlugin
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.sync.api.SyncChanges
import com.duckduckgo.sync.api.SyncEngine
import com.duckduckgo.sync.api.SyncParser
import com.duckduckgo.sync.api.SyncablePlugin
import com.duckduckgo.sync.api.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(scope = AppScope::class, boundType = BrowserLifecycleObserver::class )
class SavedSitesDataObserver @Inject constructor(
    private val syncEngine: SyncEngine,
    private val savedSitesRepository: SavedSitesEntitiesDao,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : BrowserLifecycleObserver {

    override fun onOpen(isFreshLaunch: Boolean) {
        super.onOpen(isFreshLaunch)
        Timber.d("SavedSitesDataObserver onOpen")
        appCoroutineScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.lastModified().collectLatest {
                Timber.d("SavedSitesDataObserver changes to LastModified")
                syncEngine.notifyDataChanged()
            }
        }
    }

    override fun onForeground() {
        super.onForeground()
        Timber.d("SavedSitesDataObserver onForeground")
    }
}
