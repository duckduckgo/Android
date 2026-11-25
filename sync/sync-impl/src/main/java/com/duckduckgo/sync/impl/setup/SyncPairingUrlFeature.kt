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

package com.duckduckgo.sync.impl.setup

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncFeature
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface SyncPairingUrlFeature {
    /**
     * Whether a typed pairing URL opens Sync pairing and the pairing instructions show that URL.
     */
    val isEnabled: Boolean
}

@ContributesBinding(AppScope::class, binding = binding<SyncPairingUrlFeature>())
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
@SingleIn(AppScope::class)
class RealSyncPairingUrlFeature @Inject constructor(
    private val syncFeature: SyncFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SyncPairingUrlFeature, MainProcessLifecycleObserver {

    // We cache the flag because the address bar and the pairing screens read it on the main thread.
    @Volatile
    override var isEnabled = false
        private set

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch {
            syncFeature.syncPairingUrl().enabled().collect { isEnabled = it }
        }
    }
}
