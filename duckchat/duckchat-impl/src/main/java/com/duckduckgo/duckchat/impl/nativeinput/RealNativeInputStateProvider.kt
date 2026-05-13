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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.MutableNativeInputStateProvider
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = NativeInputStateProvider::class)
@ContributesBinding(AppScope::class, boundType = MutableNativeInputStateProvider::class)
class RealNativeInputStateProvider @Inject constructor(
    private val duckAiChatStore: DuckAiChatStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : NativeInputStateProvider,
    MutableNativeInputStateProvider {

    private val tabFlows = ConcurrentHashMap<String, MutableStateFlow<NativeInputState>>()
    private val tabLogJobs = ConcurrentHashMap<String, Job>()
    private val _displayedState = MutableStateFlow(NativeInputState.zero())
    override val displayedState: StateFlow<NativeInputState> = _displayedState.asStateFlow()

    @Volatile private var activeTabId: String? = null

    override fun stateForTab(tabId: String): StateFlow<NativeInputState> =
        flowFor(tabId).asStateFlow()

    override fun updateActiveTab(tabId: String, structural: NativeInputState) {
        activeTabId = tabId
        val flow = flowFor(tabId)
        val merged = flow.value.copy(
            inputMode = structural.inputMode,
            inputContext = structural.inputContext,
            inputPosition = structural.inputPosition,
        )
        flow.value = merged
        _displayedState.value = merged
    }

    override fun update(tabId: String, patch: NativeInputState.() -> NativeInputState) {
        val flow = flowFor(tabId)
        flow.update(patch)
        if (tabId == activeTabId) _displayedState.value = flow.value
    }

    override suspend fun updateFromChat(tabId: String, chatId: String) {
        val modelId = duckAiChatStore.getChat(chatId)?.model?.takeIf { it.isNotEmpty() }
        update(tabId) {
            copy(chatId = chatId, selectedModelId = modelId ?: selectedModelId)
        }
    }

    override fun clearTab(tabId: String) {
        tabFlows.remove(tabId)
        tabLogJobs.remove(tabId)?.cancel()
        if (activeTabId == tabId) {
            activeTabId = null
            _displayedState.value = NativeInputState.zero()
        }
        logcat { "NativeInputState[$tabId] cleared" }
    }

    override fun clearAll() {
        tabFlows.clear()
        tabLogJobs.values.forEach { it.cancel() }
        tabLogJobs.clear()
        activeTabId = null
        _displayedState.value = NativeInputState.zero()
        logcat { "NativeInputState cleared all" }
    }

    private fun flowFor(tabId: String): MutableStateFlow<NativeInputState> =
        tabFlows.computeIfAbsent(tabId) {
            val flow = MutableStateFlow(NativeInputState.zero())
            tabLogJobs[tabId] = appCoroutineScope.launch {
                flow.collect { state ->
                    logcat { "NativeInputState[$tabId] -> $state" }
                }
            }
            flow
        }
}
