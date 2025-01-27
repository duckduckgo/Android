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

import com.duckduckgo.autofill.impl.jsbridge.request.AutofillJsonRequestParser.AutofillStoreFormDataCredentialsJsonRequest
import com.duckduckgo.autofill.impl.jsbridge.request.AutofillJsonRequestParser.AutofillStoreFormDataJsonRequest
import com.duckduckgo.autofill.impl.jsbridge.request.FormSubmissionTriggerType.UNKNOWN
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AutofillRequestParser {
    suspend fun parseAutofillDataRequest(request: String): Result<AutofillDataRequest>
    suspend fun parseStoreFormDataRequest(request: String): Result<AutofillStoreFormDataRequest>
}

@ContributesBinding(AppScope::class)
class AutofillJsonRequestParser @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : AutofillRequestParser {

    private val autofillDataRequestParser by lazy { moshi.adapter(AutofillDataRequest::class.java) }
    private val autofillStoreFormDataRequestParser by lazy { moshi.adapter(AutofillStoreFormDataJsonRequest::class.java) }

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(FormSubmissionTriggerType::class.java, EnumJsonAdapter.create(FormSubmissionTriggerType::class.java).withUnknownFallback(UNKNOWN))
            .build()
    }

    override suspend fun parseAutofillDataRequest(request: String): Result<AutofillDataRequest> {
        return withContext(dispatchers.io()) {
            val result = kotlin.runCatching {
                autofillDataRequestParser.fromJson(request)
            }.getOrNull()

            return@withContext if (result == null) {
                Result.failure(IllegalArgumentException("Failed to parse autofill JSON for AutofillDataRequest"))
            } else {
                Result.success(result)
            }
        }
    }

    override suspend fun parseStoreFormDataRequest(request: String): Result<AutofillStoreFormDataRequest> {
        return withContext(dispatchers.io()) {
            val result = kotlin.runCatching {
                autofillStoreFormDataRequestParser.fromJson(request)
            }
                .onFailure { Timber.w(it, "Failed to parse autofill JSON for AutofillStoreFormDataRequest") }
                .getOrNull()

            return@withContext if (result == null) {
                Result.failure(IllegalArgumentException("Failed to parse autofill JSON for AutofillStoreFormDataRequest"))
            } else {
                Result.success(result.mapToPublicType())
            }
        }
    }

    internal data class AutofillStoreFormDataJsonRequest(
        val credentials: AutofillStoreFormDataCredentialsJsonRequest?,
        val trigger: FormSubmissionTriggerType?,
    )

    internal data class AutofillStoreFormDataCredentialsJsonRequest(
        val username: String?,
        val password: String?,
        val autogenerated: Boolean = false,
    )
}

private fun AutofillStoreFormDataJsonRequest?.mapToPublicType(): AutofillStoreFormDataRequest {
    return AutofillStoreFormDataRequest(
        credentials = this?.credentials?.mapToPublicType(),
        trigger = this?.trigger ?: UNKNOWN,
    )
}

private fun AutofillStoreFormDataCredentialsJsonRequest?.mapToPublicType(): AutofillStoreFormDataCredentialsRequest {
    return AutofillStoreFormDataCredentialsRequest(
        username = this?.username,
        password = this?.password,
        autogenerated = this?.autogenerated ?: false,
    )
}
