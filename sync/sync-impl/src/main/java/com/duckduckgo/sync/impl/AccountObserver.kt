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

package com.duckduckgo.sync.impl

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.launch

// This class can be removed when we have real sync observer for data
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class AccountObserver @Inject constructor(
    val deviceSyncState: DeviceSyncState,
    val syncRepository: Lazy<SyncRepository>,
    val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        if (!deviceSyncState.isFeatureEnabled()) return

        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            syncRepository.get().getConnectedDevices()
        }
    }
}
