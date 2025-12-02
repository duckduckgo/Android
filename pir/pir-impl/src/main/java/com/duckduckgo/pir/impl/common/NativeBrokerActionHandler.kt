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
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.ClientFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.CriticalFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.InvalidRequest
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.SolutionNotReady
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.TransientFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.UnableToSolveCaptcha
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.GetCaptchaSolutionStatus
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.GetEmail
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.SubmitCaptchaInfo
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Failure
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaTransactionIdReceived
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirError.CaptchaServiceError
import com.duckduckgo.pir.impl.scripts.models.PirError.CaptchaSolutionFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.ClientError
import com.duckduckgo.pir.impl.scripts.models.PirError.EmailError
import com.duckduckgo.pir.impl.service.DbpService.CaptchaSolutionMeta
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirRepository.GeneratedEmailData
import kotlinx.coroutines.withContext
import retrofit2.HttpException

interface NativeBrokerActionHandler {
    suspend fun pushAction(nativeAction: NativeAction): NativeActionResult

    sealed class NativeAction(
        open val actionId: String,
    ) {
        data class GetEmail(
            override val actionId: String,
            val brokerName: String,
        ) : NativeAction(actionId)

        data class SubmitCaptchaInfo(
            override val actionId: String,
            val siteKey: String,
            val url: String,
            val type: String,
        ) : NativeAction(actionId)

        data class GetCaptchaSolutionStatus(
            override val actionId: String,
            val transactionID: String,
        ) : NativeAction(actionId)
    }

    sealed class NativeActionResult {
        data class Success(
            val data: NativeSuccessData,
        ) : NativeActionResult() {
            sealed class NativeSuccessData {
                data class Email(
                    val generatedEmailData: GeneratedEmailData,
                ) : NativeSuccessData()

                data class CaptchaTransactionIdReceived(
                    val transactionID: String,
                ) : NativeSuccessData()

                data class CaptchaSolutionStatus(
                    val status: CaptchaStatus,
                ) : NativeSuccessData() {
                    sealed class CaptchaStatus {
                        data class Ready(
                            val token: String,
                            val meta: CaptchaSolutionMeta,
                        ) : CaptchaStatus()

                        data object InProgress : CaptchaStatus()
                    }
                }
            }
        }

        data class Failure(
            val actionId: String,
            val error: PirError,
            val retryNativeAction: Boolean = false,
        ) : NativeActionResult()
    }
}

class RealNativeBrokerActionHandler(
    private val repository: PirRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val captchaResolver: CaptchaResolver,
) : NativeBrokerActionHandler {
    override suspend fun pushAction(nativeAction: NativeAction): NativeActionResult =
        withContext(dispatcherProvider.io()) {
            when (nativeAction) {
                is GetEmail -> handleGetEmail(nativeAction)
                is SubmitCaptchaInfo -> handleSolveCaptcha(nativeAction)
                is GetCaptchaSolutionStatus -> handleGetCaptchaSolutionStatus(nativeAction)
            }
        }

    private suspend fun handleGetEmail(action: GetEmail): NativeActionResult =
        kotlin
            .runCatching {
                repository.getEmailForBroker(action.brokerName).run {
                    Success(
                        data = NativeSuccessData.Email(this),
                    )
                }
            }.getOrElse { error ->
                if (error is HttpException) {
                    val errorMessage = PREFIX_GEN_EMAIL_ERROR + (error.response()?.errorBody()?.string() ?: error.message ?: "Unknown error")
                    Failure(
                        actionId = action.actionId,
                        error = EmailError(
                            errorCode = error.code(),
                            error = errorMessage,
                        ),
                    )
                } else {
                    Failure(
                        actionId = action.actionId,
                        error = ClientError(
                            message = PREFIX_GEN_EMAIL_ERROR + error.message,
                        ),
                    )
                }
            }

    private suspend fun handleSolveCaptcha(nativeAction: SubmitCaptchaInfo): NativeActionResult =
        captchaResolver
            .submitCaptchaInformation(
                siteKey = nativeAction.siteKey,
                url = nativeAction.url,
                type = nativeAction.type,
            ).run {
                when (this) {
                    is CaptchaResolverResult.CaptchaSubmitSuccess -> Success(
                        CaptchaTransactionIdReceived(
                            this.transactionID,
                        ),
                    )

                    is CaptchaResolverResult.CaptchaFailure -> this.mapFailureToResult(nativeAction.actionId)

                    else ->
                        Failure(
                            actionId = nativeAction.actionId,
                            error = ClientError(
                                message = "Invalid scenario",
                            ),
                            retryNativeAction = false,
                        )
                }
            }

    private suspend fun handleGetCaptchaSolutionStatus(nativeAction: GetCaptchaSolutionStatus): NativeActionResult =
        captchaResolver
            .getCaptchaSolution(
                transactionID = nativeAction.transactionID,
            ).run {
                when (this) {
                    is CaptchaResolverResult.SolveCaptchaSuccess ->
                        Success(
                            data =
                            CaptchaSolutionStatus(
                                status =
                                CaptchaStatus.Ready(
                                    token = this.token,
                                    meta = this.meta,
                                ),
                            ),
                        )

                    is CaptchaResolverResult.CaptchaFailure -> this.mapFailureToResult(nativeAction.actionId)

                    else ->
                        Failure(
                            actionId = nativeAction.actionId,
                            error = ClientError(
                                message = "Invalid scenario",
                            ),
                            retryNativeAction = false,
                        )
                }
            }

    private fun CaptchaResolverResult.CaptchaFailure.mapFailureToResult(actionId: String): NativeActionResult {
        return when (this.type) {
            SolutionNotReady -> Success(
                data =
                CaptchaSolutionStatus(
                    status = CaptchaStatus.InProgress,
                ),
            )

            ClientFailure -> Failure(
                actionId = actionId,
                error = ClientError(
                    message = this.message,
                ),
                retryNativeAction = false,
            )

            CriticalFailure, InvalidRequest -> Failure(
                actionId = actionId,
                error = CaptchaServiceError(
                    errorCode = this.code,
                    errorDetails = this.message,
                ),
                retryNativeAction = false,
            )

            TransientFailure -> Failure(
                actionId = actionId,
                error = CaptchaServiceError(
                    errorCode = this.code,
                    errorDetails = this.message,
                ),
                retryNativeAction = true,
            )

            UnableToSolveCaptcha -> Failure(
                actionId = actionId,
                error = CaptchaSolutionFailed(
                    message = this.message,
                ),
                retryNativeAction = false,
            )
        }
    }

    companion object {
        private const val PREFIX_GEN_EMAIL_ERROR = "Error email generation: "
    }
}
