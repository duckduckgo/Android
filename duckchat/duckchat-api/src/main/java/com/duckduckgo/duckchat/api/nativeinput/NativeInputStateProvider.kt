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

package com.duckduckgo.duckchat.api.nativeinput

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only access to the per-tab native input state. Components that need to react to input state
 * changes (toggle visibility, model selection, etc.) observe [state], which follows the currently
 * selected browser tab and re-emits both when the tab changes and when the active tab's state is
 * republished.
 *
 * [stateForTab] is for callers that already know a specific tabId and need synchronous keyed access
 * — primarily the native input widget itself; plugins should observe [state].
 *
 * The writer-side counterpart is [NativeInputStatePublisher], which is reserved for the native input
 * widget; plugins must not depend on it.
 */
interface NativeInputStateProvider {
    val state: Flow<NativeInputState>
    fun stateForTab(tabId: String): StateFlow<NativeInputState>
}
