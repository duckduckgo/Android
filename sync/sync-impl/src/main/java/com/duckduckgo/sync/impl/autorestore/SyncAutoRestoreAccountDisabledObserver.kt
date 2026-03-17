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

package com.duckduckgo.sync.impl.autorestore

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class SyncAutoRestoreAccountDisabledObserver @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val syncStore: SyncStore,
    private val syncAutoRestoreManager: SyncAutoRestoreManager,
    private val syncFeature: SyncFeature,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        syncStore.isSignedInFlow()
            // only interested in changes, not initial value
            .drop(1)
            .distinctUntilChanged()
            // only interested when user signs out
            .filter { isSignedIn -> !isSignedIn }
            .filter { syncFeature.syncAutoRestore().isEnabled() }
            .onEach {
                logcat { "Sync-Recovery: sync disabled, clearing auto-restore data" }
                syncAutoRestoreManager.clearAutoRestoreData()
            }
            .flowOn(dispatcherProvider.io())
            .launchIn(appCoroutineScope)
    }
}
