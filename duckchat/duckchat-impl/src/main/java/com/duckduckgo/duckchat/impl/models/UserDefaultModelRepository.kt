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

package com.duckduckgo.duckchat.impl.models

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.squareup.moshi.JsonReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okio.Buffer
import javax.inject.Inject

data class UserDefaultModel(val modelId: String?)

class UserDefaultModelRepository @Inject constructor(
    private val storageProvider: Lazy<BrowserModeDataProvider<DuckAiBridgeStorage>>,
    private val duckChatFeature: DuckChatFeature,
    private val duckChat: Lazy<DuckChatInternal>,
) {
    fun isUserDefaultEnabled(): Boolean = duckChatFeature.resetModelForNewChats().isEnabled() &&
        duckChatFeature.userDefaultModel().isEnabled() &&
        duckChat.value.isNativeStorageEnabled()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<UserDefaultModel?> =
        combine(
            duckChatFeature.resetModelForNewChats().enabled(),
            duckChatFeature.userDefaultModel().enabled(),
        ) { resetEnabled, userDefaultEnabled -> resetEnabled && userDefaultEnabled }
            .distinctUntilChanged()
            .flatMapLatest { if (it) duckChat.value.observeNativeStorageEnabled() else flowOf(false) }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    storageProvider.value.forMode(BrowserMode.REGULAR).settings
                        .observe(DEFAULT_MODEL_KEY)
                        .distinctUntilChanged()
                        .map { entry -> entry?.let { UserDefaultModel(decode(it.value)) } }
                        .distinctUntilChanged()
                } else {
                    flowOf(null)
                }
            }

    private fun decode(value: String): String? {
        if (!JSON_STRING.matches(value)) return null
        return JsonReader.of(Buffer().writeUtf8(value)).use { it.nextString() }.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val DEFAULT_MODEL_KEY = "defaultDuckaiModel"

        // Moshi's reader also accepts unescaped control characters and certain non-JSON escapes in strings.
        val JSON_STRING = Regex("""[ \t\r\n]*"(?:[^"\\\x00-\x1F]|\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))*+"[ \t\r\n]*""")
    }
}
