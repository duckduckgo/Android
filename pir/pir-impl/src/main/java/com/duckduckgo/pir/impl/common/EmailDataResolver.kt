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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.EmailDataResolver.EmailDataResolverResult
import com.duckduckgo.pir.impl.common.EmailDataResolver.EmailDataResolverResult.Failure
import com.duckduckgo.pir.impl.common.EmailDataResolver.EmailDataResolverResult.Pending
import com.duckduckgo.pir.impl.common.EmailDataResolver.EmailDataResolverResult.Success
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest.RequestEmailData
import com.duckduckgo.pir.impl.service.ResponseError
import com.duckduckgo.pir.impl.service.parseError
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import logcat.logcat
import retrofit2.HttpException
import javax.inject.Inject

interface EmailDataResolver {
    /**
     * Polls the backend for email-extracted data (e.g. verification codes) for the given email/attempt pair.
     *
     * @param emailAddress - the generated email address whose inbox is being polled
     * @param attemptId - identifies the scan or opt-out attempt
     */
    suspend fun poll(
        emailAddress: String,
        attemptId: String,
    ): EmailDataResolverResult

    sealed class EmailDataResolverResult {
        /** Backend returned status "ready" with the extracted data map. */
        data class Success(
            val extractedData: Map<String, String>,
        ) : EmailDataResolverResult()

        /** Backend has not yet received the email — caller should poll again. */
        data object Pending : EmailDataResolverResult()

        data class Failure(
            val code: Int,
            val message: String,
        ) : EmailDataResolverResult()
    }
}

@ContributesBinding(AppScope::class)
class RealEmailDataResolver @Inject constructor(
    private val dbpService: DbpService,
    private val dispatcherProvider: DispatcherProvider,
    moshi: Moshi,
) : EmailDataResolver {
    private val adapter = moshi.adapter(ResponseError::class.java)

    override suspend fun poll(
        emailAddress: String,
        attemptId: String,
    ): EmailDataResolverResult = withContext(dispatcherProvider.io()) {
        runCatching {
            dbpService.getEmailConfirmationLinkStatus(
                PirEmailConfirmationDataRequest(
                    items = listOf(
                        RequestEmailData(
                            email = emailAddress,
                            attemptId = attemptId,
                        ),
                    ),
                ),
            ).run {
                logcat { "PIR-EMAIL-DATA: RESULT -> $this" }
                val item = items.firstOrNull()
                    ?: return@run Failure(
                        code = 0,
                        message = PREFIX_EMAIL_DATA_ERROR + "Empty response items",
                    )

                when (item.status.lowercase()) {
                    STATUS_READY -> Success(
                        extractedData = item.data.associate { it.name to it.value },
                    )

                    STATUS_PENDING -> Pending

                    else -> Failure(
                        code = 0,
                        message = "$PREFIX_EMAIL_DATA_ERROR${item.errorCode.orEmpty()} ${item.error ?: item.status}",
                    )
                }
            }
        }.getOrElse { error ->
            logcat { "PIR-EMAIL-DATA: Failure -> $error" }
            if (error is HttpException) {
                val errorMessage = adapter.parseError(error)?.message.orEmpty()
                Failure(
                    code = error.code(),
                    message = "$PREFIX_EMAIL_DATA_ERROR${error.code()} $errorMessage",
                )
            } else {
                Failure(
                    code = 0,
                    message = PREFIX_EMAIL_DATA_ERROR + (error.message ?: "Unknown error"),
                )
            }
        }
    }

    companion object {
        private const val PREFIX_EMAIL_DATA_ERROR = "Email data poll error: "
        private const val STATUS_READY = "ready"
        private const val STATUS_PENDING = "pending"
    }
}
