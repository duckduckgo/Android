/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.weblocalstorage

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class Domains(val list: List<String> = emptyList())
data class KeysToDelete(val list: List<String> = emptyList())
data class MatchingRegex(val list: List<String> = emptyList())

data class WebLocalStorageSettings(
    val domains: Domains = Domains(),
    val keysToDelete: KeysToDelete = KeysToDelete(),
    val matchingRegex: MatchingRegex = MatchingRegex(),
)

interface WebLocalStorageSettingsJsonParser {
    suspend fun parseJson(json: String?): WebLocalStorageSettings
}

@ContributesBinding(AppScope::class)
class WebLocalStorageSettingsJsonParserImpl @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) : WebLocalStorageSettingsJsonParser {

    private val jsonAdapter by lazy { buildJsonAdapter() }

    private fun buildJsonAdapter(): JsonAdapter<SettingsJson> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return moshi.adapter(SettingsJson::class.java)
    }

    override suspend fun parseJson(json: String?): WebLocalStorageSettings = withContext(dispatcherProvider.io()) {
        if (json == null) return@withContext WebLocalStorageSettings(Domains(), KeysToDelete(), MatchingRegex())

        kotlin.runCatching {
            val parsed = jsonAdapter.fromJson(json)
            val domains = parsed?.asDomains() ?: Domains()
            val keysToDelete = parsed?.asKeysToDelete() ?: KeysToDelete()
            val matchingRegex = parsed?.asMatchingRegex() ?: MatchingRegex()
            WebLocalStorageSettings(domains, keysToDelete, matchingRegex)
        }.getOrDefault(WebLocalStorageSettings(Domains(), KeysToDelete(), MatchingRegex()))
    }

    private fun SettingsJson.asDomains(): Domains {
        return Domains(domains ?: emptyList())
    }

    private fun SettingsJson.asKeysToDelete(): KeysToDelete {
        return KeysToDelete(keysToDelete ?: emptyList())
    }

    private fun SettingsJson.asMatchingRegex(): MatchingRegex {
        return MatchingRegex(matchingRegex ?: emptyList())
    }

    private data class SettingsJson(
        val domains: List<String>?,
        val keysToDelete: List<String>?,
        val matchingRegex: List<String>?,
    )
}
