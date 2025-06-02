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

package com.duckduckgo.sync.impl.engine

import androidx.lifecycle.*
import com.duckduckgo.app.di.*
import com.duckduckgo.app.lifecycle.*
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.store.*
import com.squareup.anvil.annotations.*
import javax.inject.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class SyncAccountDisabledObserver @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val syncStore: SyncStore,
    private val syncEngine: SyncEngine,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        syncStore.isSignedInFlow()
            .drop(1) // we only want to listen for changes here
            .distinctUntilChanged()
            .onEach { signedIn ->
                if (!signedIn) {
                    logcat(INFO) { "Sync disabled, notify engine" }
                    syncEngine.onSyncDisabled()
                }
            }
            .flowOn(dispatcherProvider.io())
            .launchIn(appCoroutineScope)
    }
}
