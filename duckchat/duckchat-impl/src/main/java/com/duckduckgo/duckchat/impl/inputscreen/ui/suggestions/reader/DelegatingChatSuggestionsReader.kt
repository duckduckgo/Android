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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.store.api.DuckAiChatStore
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class, replaces = [RealChatSuggestionsReader::class])
class DelegatingChatSuggestionsReader @Inject constructor(
    private val nativeReader: ChatSuggestionsNativeReader,
    // Typed as RealChatSuggestionsReader (not ChatSuggestionsReader) to prevent Anvil
    // from treating this field as a competing binding for the ChatSuggestionsReader interface.
    private val webViewReader: RealChatSuggestionsReader,
    private val store: DuckAiChatStore,
) : ChatSuggestionsReader {

    private var activeReader: ChatSuggestionsReader? = null

    override suspend fun fetchSuggestions(query: String): List<ChatSuggestion> {
        val reader = if (store.hasMigrated()) nativeReader else webViewReader
        if (activeReader != null && activeReader !== reader) activeReader?.tearDown()
        activeReader = reader
        logcat { "DuckAI chat suggestions: using ${if (reader === nativeReader) "native store" else "WebView"} reader" }
        return reader.fetchSuggestions(query)
    }

    override fun tearDown() {
        activeReader?.tearDown()
        activeReader = null
    }
}
