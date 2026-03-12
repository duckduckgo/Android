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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.sync.api.SyncAutoRestore
import com.duckduckgo.sync.impl.SyncFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSyncAutoRestore @Inject constructor(
    private val persistentStorage: PersistentStorage,
    private val syncFeature: SyncFeature,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SyncAutoRestore {

    override suspend fun canRestore(): Boolean {
        return withContext(dispatcherProvider.io()) {
            if (!syncFeature.syncAutoRestore().isEnabled()) return@withContext false
            persistentStorage.retrieve(SyncRecoveryPersistentStorageKey).getOrNull() != null
        }
    }

    override fun restoreSyncAccount() {
        appScope.launch(dispatcherProvider.io()) {
            logcat { "Sync-Recovery: restoreSyncAccount called, not yet implemented" }
        }
    }
}
