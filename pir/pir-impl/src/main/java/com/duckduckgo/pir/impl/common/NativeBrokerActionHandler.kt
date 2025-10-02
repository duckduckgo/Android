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
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.GetCaptchaSolutionStatus
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.GetEmail
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction.SubmitCaptchaInfo
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Failure
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaTransactionIdReceived
import com.duckduckgo.pir.impl.service.DbpService.CaptchaSolutionMeta
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirRepository.EmailConfirmationLinkFetchStatus
import kotlinx.coroutines.withContext

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
                    val email: String,
                ) : NativeSuccessData()

                data class EmailConfirmation(
                    val email: String,
                    val link: String,
                    val status: EmailConfirmationLinkFetchStatus,
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
            val message: String,
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
                    NativeActionResult.Success(
                        data = NativeSuccessData.Email(this),
                    )
                }
            }.getOrElse { error ->
                Failure(
                    actionId = action.actionId,
                    message = "Unknown error while getting email : ${error.message}",
                )
            }

    private suspend fun handleSolveCaptcha(nativeAction: SubmitCaptchaInfo): NativeActionResult =
        captchaResolver
            .submitCaptchaInformation(
                siteKey = nativeAction.siteKey,
                url = nativeAction.url,
                type = nativeAction.type,
            ).run {
                when (this) {
                    is CaptchaResolverResult.CaptchaSubmitSuccess ->
                        NativeActionResult.Success(
                            CaptchaTransactionIdReceived(
                                this.transactionID,
                            ),
                        )

                    is CaptchaResolverResult.CaptchaFailure ->
                        if (this.type == CaptchaResolverError.TransientFailure) {
                            // Transient failures mean that client should retry after a minute
                            Failure(
                                actionId = nativeAction.actionId,
                                message = this.message,
                                retryNativeAction = true,
                            )
                        } else {
                            Failure(
                                actionId = nativeAction.actionId,
                                message = this.message,
                                retryNativeAction = false,
                            )
                        }

                    else ->
                        Failure(
                            actionId = nativeAction.actionId,
                            message = "Invalid scenario",
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
                        NativeActionResult.Success(
                            data =
                            CaptchaSolutionStatus(
                                status =
                                CaptchaSolutionStatus.CaptchaStatus.Ready(
                                    token = this.token,
                                    meta = this.meta,
                                ),
                            ),
                        )

                    is CaptchaResolverResult.CaptchaFailure ->
                        if (this.type == CaptchaResolverError.SolutionNotReady) {
                            NativeActionResult.Success(
                                data =
                                CaptchaSolutionStatus(
                                    status = CaptchaSolutionStatus.CaptchaStatus.InProgress,
                                ),
                            )
                        } else {
                            Failure(
                                actionId = nativeAction.actionId,
                                message = "Failed to resolve captcha",
                                retryNativeAction = false,
                            )
                        }

                    else ->
                        Failure(
                            actionId = nativeAction.actionId,
                            message = "Invalid scenario",
                            retryNativeAction = false,
                        )
                }
            }
}
