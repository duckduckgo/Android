/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.CriticalFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.InvalidRequest
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.SolutionNotReady
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.TransientFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.UnableToSolveCaptcha
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult.CaptchaFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult.CaptchaSubmitSuccess
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult.SolveCaptchaSuccess
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.CaptchaSolutionMeta
import com.duckduckgo.pir.impl.service.DbpService.PirStartCaptchaSolutionBody
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import retrofit2.HttpException
import javax.inject.Inject

interface CaptchaResolver {
    /**
     * Submits captcha information to the backend to start solving it,
     *
     * @param siteKey - value from the response we get after submitting the GetCaptchaInfo to the js layer.
     * @param url - value from the response we get after submitting the GetCaptchaInfo to the js layer.
     * @param type - value from the response we get after submitting the GetCaptchaInfo to the js layer.
     * @param attemptId - Identifies the scan or the opt-out attempt
     */
    suspend fun submitCaptchaInformation(
        siteKey: String,
        url: String,
        type: String,
        attemptId: String? = null,
    ): CaptchaResolverResult

    /**
     * Obtains the status of the solution for the captcha submitted with the given [transactionID]
     *
     * @param transactionID - obtained after submitting the information needed to solve captcha
     * @param attemptId - Identifies the scan or the opt-out attempt
     */
    suspend fun getCaptchaSolution(
        transactionID: String,
        attemptId: String? = null,
    ): CaptchaResolverResult

    sealed class CaptchaResolverResult {
        data class CaptchaSubmitSuccess(
            val transactionID: String,
        ) : CaptchaResolverResult()

        data class SolveCaptchaSuccess(
            val token: String,
            val meta: CaptchaSolutionMeta,
        ) : CaptchaResolverResult()

        data class CaptchaFailure(
            val type: CaptchaResolverError,
            val message: String,
        ) : CaptchaResolverResult()
    }

    sealed class CaptchaResolverError {
        data object SolutionNotReady : CaptchaResolverError()
        data object UnableToSolveCaptcha : CaptchaResolverError()
        data object CriticalFailure : CaptchaResolverError()
        data object InvalidRequest : CaptchaResolverError()
        data object TransientFailure : CaptchaResolverError()
    }
}

@ContributesBinding(AppScope::class)
class RealCaptchaResolver @Inject constructor(
    private val dbpService: DbpService,
    private val dispatcherProvider: DispatcherProvider,
) : CaptchaResolver {
    override suspend fun submitCaptchaInformation(
        siteKey: String,
        url: String,
        type: String,
        attemptId: String?,
    ): CaptchaResolverResult = withContext(dispatcherProvider.io()) {
        // https://dub.duckduckgo.com/duckduckgo/dbp-api?tab=readme-ov-file#post-dbpcaptchav0submit
        runCatching {
            dbpService.submitCaptchaInformation(
                PirStartCaptchaSolutionBody(
                    siteKey = siteKey,
                    url = url,
                    type = type,
                ),
                attemptId = attemptId,
            ).run {
                CaptchaSubmitSuccess(
                    transactionID = this.transactionId,
                )
            }
        }.getOrElse { error ->
            if (error is HttpException) {
                val errorMessage = error.message()
                if (errorMessage.startsWith("INVALID_REQUEST")) {
                    CaptchaFailure(
                        type = InvalidRequest,
                        message = errorMessage,
                    )
                } else if (errorMessage.startsWith("FAILURE_TRANSIENT")) {
                    CaptchaFailure(
                        type = TransientFailure,
                        message = errorMessage,
                    )
                } else {
                    CaptchaFailure(
                        type = CriticalFailure,
                        message = errorMessage,
                    )
                }
            } else {
                CaptchaFailure(
                    type = CriticalFailure,
                    message = error.message ?: "Unknown error",
                )
            }
        }
    }

    override suspend fun getCaptchaSolution(
        transactionID: String,
        attemptId: String?,
    ): CaptchaResolverResult = withContext(dispatcherProvider.io()) {
        // https://dub.duckduckgo.com/duckduckgo/dbp-api?tab=readme-ov-file#get-dbpcaptchav0resulttransactionidtransaction_id
        runCatching {
            dbpService.getCaptchaSolution(transactionID, attemptId).run {
                logcat { "PIR-CAPTCHA: RESULT -> $this" }
                when (message) {
                    "SOLUTION_NOT_READY" -> CaptchaFailure(
                        type = SolutionNotReady,
                        message = message,
                    )

                    "FAILURE" -> CaptchaFailure(
                        type = UnableToSolveCaptcha,
                        message = message,
                    )

                    else -> SolveCaptchaSuccess(
                        token = this.data,
                        meta = this.meta,
                    )
                }
            }
        }.getOrElse {
            logcat { "PIR-CAPTCHA: Failure -> $it" }
            CaptchaFailure(
                type = InvalidRequest,
                message = it.message ?: "Unknown error",
            )
        }
    }
}
