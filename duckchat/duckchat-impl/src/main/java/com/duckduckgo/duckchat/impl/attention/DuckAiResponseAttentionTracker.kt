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

package com.duckduckgo.duckchat.impl.attention

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Tracks which Duck.ai responses are still generating and whether the user is looking at them,
 * and reports it to every [ResponseAttentionListener]. Tracking is in memory only, so it ends with the process.
 */
interface DuckAiResponseAttentionTracker {
    fun onNativePromptSubmitted(tabId: String, browserMode: BrowserMode)
    fun onResponseState(tabId: String, status: String)
    fun onStopTapped(tabId: String)
}

/**
 * [id] is unique within the process and is the only stable key. [chatId] is null until the page puts it in the tab URL,
 * and [tabId] moves when the chat comes on screen in another tab.
 */
data class TrackedResponse(val id: Long, val tabId: String, val chatId: String?, val browserMode: BrowserMode)

sealed interface ResponseAttentionEvent {
    val response: TrackedResponse

    /** The user left while the response was still generating. */
    data class LeftWhileGenerating(override val response: TrackedResponse) : ResponseAttentionEvent

    /** The user is back on the response's chat after [LeftWhileGenerating]. */
    data class Returned(override val response: TrackedResponse) : ResponseAttentionEvent

    /** A raw page status for the response while the user is away. */
    data class StatusWhileAway(override val response: TrackedResponse, val status: String) : ResponseAttentionEvent

    /**
     * Tracking stopped while the user was away, either because the chat was deleted or feature flags disabled, and no further events follow for the response.
     */
    data class TrackingAborted(override val response: TrackedResponse) : ResponseAttentionEvent
}

interface ResponseAttentionListener {

    /**
     * Called on [kotlinx.coroutines.Dispatchers.IO] thread.
     */
    suspend fun onEvent(event: ResponseAttentionEvent)
}

@BindingContainer
@ContributesTo(AppScope::class)
abstract class ResponseAttentionListenerModule {
    @Multibinds(allowEmpty = true)
    abstract fun responseAttentionListeners(): Set<ResponseAttentionListener>
}
