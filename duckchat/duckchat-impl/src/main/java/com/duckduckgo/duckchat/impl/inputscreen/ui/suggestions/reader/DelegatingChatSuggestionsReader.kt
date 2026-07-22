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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.RealChatSuggestionsStore
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(ActivityScope::class, replaces = [RealChatSuggestionsReader::class])
@SingleInstanceIn(ActivityScope::class)
class DelegatingChatSuggestionsReader @Inject constructor(
    private val nativeReader: ChatSuggestionsNativeReader,
    // Typed as RealChatSuggestionsReader (not ChatSuggestionsReader) to prevent Anvil
    // from treating this field as a competing binding for the ChatSuggestionsReader interface.
    private val webViewReader: RealChatSuggestionsReader,
    private val store: DuckAiChatStore,
    private val feature: DuckChatFeature,
    // Lazy to break potential DI cycles through DuckChatPixels → DuckChatTermsOfServiceHandler
    //   → DuckChatInternal path.
    private val pixels: Lazy<DuckChatPixels>,
    private val suggestionsStore: RealChatSuggestionsStore,
    private val dispatchers: DispatcherProvider,
) : ChatSuggestionsReader {

    private var activeReader: ChatSuggestionsReader? = null

    override suspend fun fetchSuggestions(query: String): List<ChatSuggestion> = withContext(dispatchers.io()) {
        val reader = if (store.hasMigrated() && feature.useNativeStorageChatData().isEnabled()) nativeReader else webViewReader
        if (activeReader != null && activeReader !== reader) tearDown()
        activeReader = reader
        logcat { "DuckAI chat suggestions: using ${if (reader === nativeReader) "native store" else "WebView"} reader" }
        pixels.get().reportNativeStorageReaderUsed(native = reader === nativeReader)
        val result = reader.fetchSuggestions(query)
        suggestionsStore.setHasChatSuggestions(result.isNotEmpty())
        return@withContext result
    }

    override fun tearDown() {
        activeReader?.let { reader ->
            reader.tearDown()
            suggestionsStore.clearCachedValue()
        }
        activeReader = null
    }
}
