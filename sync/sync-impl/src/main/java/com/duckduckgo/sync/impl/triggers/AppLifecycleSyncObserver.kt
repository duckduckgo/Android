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

package com.duckduckgo.sync.impl.triggers

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.APP_OPEN
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class AppLifecycleSyncObserver @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val deviceSyncState: DeviceSyncState,
    private val syncEngine: SyncEngine,
) : MainProcessLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (deviceSyncState.isUserSignedInOnDevice()) {
                logcat { "Sync-Engine: App started, triggering sync" }
                syncEngine.triggerSync(APP_OPEN)
            }
        }
    }
}
