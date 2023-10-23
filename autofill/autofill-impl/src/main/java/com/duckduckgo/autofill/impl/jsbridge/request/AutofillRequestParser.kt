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

package com.duckduckgo.autofill.impl.jsbridge.request

import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface AutofillRequestParser {
    suspend fun parseAutofillDataRequest(request: String): AutofillDataRequest
    suspend fun parseStoreFormDataRequest(request: String): AutofillStoreFormDataRequest
}

@ContributesBinding(AppScope::class)
class AutofillJsonRequestParser @Inject constructor(
    val moshi: Moshi,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : AutofillRequestParser {

    private val autofillDataRequestParser by lazy { moshi.adapter(AutofillDataRequest::class.java) }
    private val autofillStoreFormDataRequestParser by lazy { moshi.adapter(AutofillStoreFormDataRequest::class.java) }

    override suspend fun parseAutofillDataRequest(request: String): AutofillDataRequest {
        return withContext(dispatchers.io()) {
            autofillDataRequestParser.fromJson(request) ?: throw IllegalArgumentException("Failed to parse autofill request")
        }
    }

    override suspend fun parseStoreFormDataRequest(request: String): AutofillStoreFormDataRequest {
        return withContext(dispatchers.io()) {
            autofillStoreFormDataRequestParser.fromJson(request) ?: throw IllegalArgumentException("Failed to parse autofill request")
        }
    }
}
