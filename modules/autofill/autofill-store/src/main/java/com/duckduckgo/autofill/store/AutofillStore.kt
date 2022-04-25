/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.store

import androidx.core.net.toUri
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import timber.log.Timber

interface AutofillStore {

    fun getCredentials(url: String): List<Credentials>

    fun saveCredentials(url: String, credentials: Credentials)
}

class MockAutofillStore : AutofillStore {

    private val data: MutableMap<String, List<Credentials>> = mutableMapOf()

    init {
        Timber.i("Initialising MockAutofillStore %s", this)
        saveCredentials("https://appassets.androidplatform.net", Credentials("foo", "bar"))
    }

    override fun getCredentials(rawUrl: String): List<Credentials> {
        val url = rawUrl.extractOriginFromUrl()
        return data[url] ?: emptyList()
    }

    override fun saveCredentials(rawUrl: String, credentials: Credentials) {
        val url = rawUrl.extractOriginFromUrl()
        val updatedList = generateList(url, credentials)
        data[url] = updatedList
    }

    private fun generateList(rawUrl: String, newCredentials: Credentials): List<Credentials> {
        val url = rawUrl.extractOriginFromUrl()
        val existingCredentials = data[url] ?: return listOf(newCredentials)

        return mutableListOf<Credentials>().also { list ->
            list.addAll(existingCredentials)
            list.add(newCredentials)
        }
    }

    private fun String.extractOriginFromUrl(): String {
        val url = this.toUri()
        val scheme = if (url.scheme != null) "${url.scheme}://" else ""

        return String.format("%s%s", scheme, url.host).also {
            Timber.i("Extracted origin from URL.\ninput=%s\noutput=%s\nhost=%s", this, it, url.host)
        }
    }
}

data class Credentials(
    val username: String,
    val password: String
)

@Module
@ContributesTo(AppScope::class)
class AutofillStoreModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun autofillStore(): AutofillStore {
        return MockAutofillStore()
    }
}
