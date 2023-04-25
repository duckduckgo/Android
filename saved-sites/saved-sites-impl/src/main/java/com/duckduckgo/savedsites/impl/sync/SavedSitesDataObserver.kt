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

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.engine.SyncEngine
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

interface SyncDataObserver {
    fun observeChanges()
}

@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncDataObserver::class)
class SavedSitesDataObserver @Inject constructor(
    private val syncEngine: SyncEngine,
    private val syncState: DeviceSyncState,
    private val savedSitesRepository: SavedSitesRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : SyncDataObserver, MainProcessLifecycleObserver {

    @SuppressLint("CheckResult")
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Timber.d("Sync: onCreate")
        observeChanges()
    }

    override fun observeChanges() {
        if (syncState.isUserSignedInOnDevice()) {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                savedSitesRepository.lastModified().collectLatest {
                    Timber.d("Sync: changes to LastModified")
                    syncEngine.notifyDataChanged()
                }
            }
        } else {
            Timber.d("Sync: user not signed in, no need to observer changes")
        }
    }
}
