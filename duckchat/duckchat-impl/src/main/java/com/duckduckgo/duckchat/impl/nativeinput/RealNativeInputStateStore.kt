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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = NativeInputStateProvider::class)
@ContributesBinding(AppScope::class, boundType = NativeInputStatePublisher::class)
class RealNativeInputStateStore @Inject constructor() :
    NativeInputStateProvider,
    NativeInputStatePublisher {

    private val flows = ConcurrentHashMap<String, MutableStateFlow<NativeInputState>>()

    override fun stateForTab(tabId: String): StateFlow<NativeInputState> = flowFor(tabId)

    override fun publish(tabId: String, state: NativeInputState) {
        flowFor(tabId).value = state
    }

    override fun update(tabId: String, transform: (NativeInputState) -> NativeInputState) {
        flowFor(tabId).update(transform)
    }

    override fun clearTab(tabId: String) {
        flows.remove(tabId)
    }

    override fun clearAll() {
        flows.clear()
    }

    // Seed unpublished tabs with [NativeInputState.zero] only because StateFlow requires an initial
    // value. The native input widget overwrites it on configure, so observers should never see this
    // placeholder in practice.
    private fun flowFor(tabId: String): MutableStateFlow<NativeInputState> =
        flows.computeIfAbsent(tabId) { MutableStateFlow(NativeInputState.zero(tabId)) }
}
