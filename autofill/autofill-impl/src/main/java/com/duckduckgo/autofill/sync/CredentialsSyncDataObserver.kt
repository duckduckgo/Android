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

package com.duckduckgo.autofill.sync

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncState.FAILED
import com.duckduckgo.sync.api.SyncState.IN_PROGRESS
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncState.READY
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.DATA_CHANGE
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class CredentialsSyncDataObserver @Inject constructor(
    private val autofillStore: AutofillStore,
    private val syncEngine: SyncEngine,
    private val syncStateMonitor: SyncStateMonitor,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    private val dataObserverJob = ConflatedJob()

    override fun onCreate(owner: LifecycleOwner) {
        syncStateMonitor.syncState().onEach {
            when (it) {
                READY -> observeSavedSitesChanges()
                IN_PROGRESS -> cancelSavedSitesChanges()
                FAILED -> observeSavedSitesChanges()
                OFF -> cancelSavedSitesChanges()
            }
        }.launchIn(appCoroutineScope)
    }

    private fun observeSavedSitesChanges() {
        if (!dataObserverJob.isActive) {
            dataObserverJob += appCoroutineScope.launch(dispatchers.io()) {
                // drop first since we only want to observe changes
                autofillStore.getAllCredentials().drop(1).collect {
                    syncEngine.triggerSync(DATA_CHANGE)
                }
            }
        }
    }

    private fun cancelSavedSitesChanges() {
        dataObserverJob.cancel()
    }
}
