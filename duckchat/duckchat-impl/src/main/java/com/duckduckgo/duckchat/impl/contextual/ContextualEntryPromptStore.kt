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

package com.duckduckgo.duckchat.impl.contextual

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * A prompt composed in the contextual entry dialog, handed to the contextual sheet so it can open
 * straight into the chat (WEBVIEW) and auto-submit it, rather than reopening the initial input state.
 *
 * [serializedPageContext] is the page context the dialog had attached at submit time, so "Ask about
 * page" keeps its context across the hand-off. When it is null the dialog had no context attached
 * (never available, or the user removed it), and the sheet must not auto-attach one on hand-off.
 */
data class ContextualEntryPrompt(
    val tabId: String,
    val prompt: NativeInputPrompt,
    val serializedPageContext: String?,
)

/**
 * AppScope hand-off between the entry dialog (writer) and the sheet (reader): they are distinct
 * fragments with distinct ViewModels, so the pending submission is parked here in between.
 *
 * Keyed per tab so concurrent tabs don't clobber each other and so a pending prompt can be cleared
 * when its tab is deleted (see [clear]) rather than lingering until it is consumed.
 */
interface ContextualEntryPromptStore {
    fun store(entry: ContextualEntryPrompt)

    /** Returns and clears the pending prompt for [tabId], otherwise null. */
    fun consume(tabId: String): ContextualEntryPrompt?

    /** Drops any pending prompt for [tabId] (e.g. when the tab is deleted or cleared). */
    fun clear(tabId: String)

    /** Drops all pending prompts (e.g. when all tabs are cleared). */
    fun clearAll()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealContextualEntryPromptStore @Inject constructor() : ContextualEntryPromptStore {

    private val pending = ConcurrentHashMap<String, ContextualEntryPrompt>()

    override fun store(entry: ContextualEntryPrompt) {
        pending[entry.tabId] = entry
    }

    override fun consume(tabId: String): ContextualEntryPrompt? = pending.remove(tabId)

    override fun clear(tabId: String) {
        pending.remove(tabId)
    }

    override fun clearAll() {
        pending.clear()
    }
}
