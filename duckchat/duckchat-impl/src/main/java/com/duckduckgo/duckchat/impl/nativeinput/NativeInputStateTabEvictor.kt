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

package com.duckduckgo.duckchat.impl.nativeinput

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Bridges the tab-removal signals from [TabRepository] to [NativeInputStatePublisher], so the per-tab
 * native input state map drops entries whose tabs no longer exist. Decoupling the eviction from
 * `TabDataRepository` keeps the tab repository unaware of native-input as a specific consumer and
 * breaks the Dagger cycle that would otherwise exist between the publisher and the tab repository.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(scope = AppScope::class)
class NativeInputStateTabEvictor @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val tabRepository: TabRepository,
    private val publisher: NativeInputStatePublisher,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        tabRepository.deletedTabs
            .onEach { tabId -> publisher.clearTab(tabId) }
            .launchIn(appCoroutineScope)

        tabRepository.allTabsDeleted
            .onEach { publisher.clearAll() }
            .launchIn(appCoroutineScope)
    }
}
