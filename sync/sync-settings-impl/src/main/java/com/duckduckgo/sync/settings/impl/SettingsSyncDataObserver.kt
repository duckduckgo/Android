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

package com.duckduckgo.sync.settings.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class SettingsSyncDataObserver @Inject constructor(
    private val syncEngine: SyncEngine,
    private val syncStateMonitor: SyncStateMonitor,
    private val syncMetadata: SettingsSyncMetadataDao,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    private val syncTriggerJob = ConflatedJob()

    override fun onCreate(owner: LifecycleOwner) {
        syncStateMonitor.syncState().onEach {
            when (it) {
                READY -> {
                    syncTriggerObserver()
                }
                IN_PROGRESS -> {}
                FAILED -> {
                    syncTriggerObserver()
                }
                OFF -> {
                    cancelSyncTriggerObserver()
                }
            }
        }.flowOn(dispatchers.io()).launchIn(appCoroutineScope)
    }

    private fun syncTriggerObserver() {
        if (!syncTriggerJob.isActive) {
            syncTriggerJob += appCoroutineScope.launch(dispatchers.io()) {
                // drop first since we only want to observe changes
                syncMetadata.getAllObservable().drop(1).collect {
                    logcat(INFO) { "Sync-Settings: TRIGGER DATA_CHANGE $it" }
                    syncEngine.triggerSync(DATA_CHANGE)
                }
            }
        }
    }

    private fun cancelSyncTriggerObserver() {
        syncTriggerJob.cancel()
    }
}
