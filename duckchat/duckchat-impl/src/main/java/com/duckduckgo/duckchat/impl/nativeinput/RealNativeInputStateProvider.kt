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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.ui.NativeInputState
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateDao
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateEntity
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = NativeInputStateProvider::class)
@ContributesBinding(AppScope::class, boundType = MutableNativeInputStateProvider::class)
class RealNativeInputStateProvider @Inject constructor(
    private val dao: NativeInputTabStateDao,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : NativeInputStateProvider, MutableNativeInputStateProvider {

    private val tabFlows = ConcurrentHashMap<String, MutableStateFlow<NativeInputState>>()
    private val _displayedState = MutableStateFlow(NativeInputState.zero())
    override val displayedState: StateFlow<NativeInputState> = _displayedState.asStateFlow()
    @Volatile private var activeTabId: String? = null

    override fun stateForTab(tabId: String): StateFlow<NativeInputState> =
        tabFlows.getOrPut(tabId) { MutableStateFlow(NativeInputState.zero()) }.asStateFlow()

    override fun setActiveTab(tabId: String, structural: NativeInputState) {
        appScope.launch(dispatchers.io()) {
            activeTabId = tabId
            val persisted = dao.getTab(tabId)
            val merged = structural.copy(selectedModelId = persisted?.selectedModelId)
            tabFlows.getOrPut(tabId) { MutableStateFlow(NativeInputState.zero()) }.value = merged
            _displayedState.value = merged
        }
    }

    override fun update(tabId: String, patch: NativeInputState.() -> NativeInputState) {
        val flow = tabFlows[tabId] ?: return
        val old = flow.value
        val new = old.patch()
        flow.value = new
        if (tabId == activeTabId) _displayedState.value = new
        if (old.selectedModelId != new.selectedModelId) {
            appScope.launch(dispatchers.io()) {
                dao.upsert(NativeInputTabStateEntity(tabId = tabId, selectedModelId = new.selectedModelId))
            }
        }
    }

    override fun clearTab(tabId: String) {
        tabFlows.remove(tabId)
        if (activeTabId == tabId) {
            activeTabId = null
            _displayedState.value = NativeInputState.zero()
        }
        appScope.launch(dispatchers.io()) {
            dao.delete(tabId)
        }
    }
}
