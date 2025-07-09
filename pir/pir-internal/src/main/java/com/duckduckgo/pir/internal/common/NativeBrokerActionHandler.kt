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

package com.duckduckgo.pir.internal.common

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.internal.common.CaptchaResolver.CaptchaResolverError
import com.duckduckgo.pir.internal.common.CaptchaResolver.CaptchaResolverResult
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.GetCaptchaSolutionStatus
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.GetEmail
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.GetEmailStatus
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.SubmitCaptchaInfo
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Failure
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaSolutionStatus
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData.CaptchaTransactionIdReceived
import com.duckduckgo.pir.internal.service.DbpService.CaptchaSolutionMeta
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus.Ready
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus.Unknown
import kotlinx.coroutines.withContext
import logcat.logcat

interface NativeBrokerActionHandler {
    suspend fun pushAction(nativeAction: NativeAction): NativeActionResult

    sealed class NativeAction(open val actionId: String) {
        data class GetEmail(
            override val actionId: String,
            val brokerName: String,
        ) : NativeAction(actionId)

        data class GetEmailStatus(
            override val actionId: String,
            val brokerName: String,
            val email: String,
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
                    val status: ConfirmationStatus,
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
    override suspend fun pushAction(nativeAction: NativeAction): NativeActionResult = withContext(dispatcherProvider.io()) {
        when (nativeAction) {
            is GetEmailStatus -> handleAwaitConfirmation(nativeAction)
            is GetEmail -> handleGetEmail(nativeAction)
            is SubmitCaptchaInfo -> handleSolveCaptcha(nativeAction)
            is GetCaptchaSolutionStatus -> handleGetCaptchaSolutionStatus(nativeAction)
        }
    }

    private suspend fun handleAwaitConfirmation(action: GetEmailStatus): NativeActionResult {
        return kotlin.runCatching {
            // https://dub.duckduckgo.com/duckduckgo/dbp-api?tab=readme-ov-file#get-dbpemv0linkseemail_address
            val result: Pair<ConfirmationStatus, String?> = repository.getEmailConfirmation(action.email)
            logcat { "PIR-EMAIL: $result" }
            return when (result.first) {
                is Ready -> NativeActionResult.Success(
                    data = NativeSuccessData.EmailConfirmation(
                        email = action.email,
                        link = result.second!!,
                        status = result.first,
                    ),
                )

                is Unknown -> Failure(
                    actionId = action.actionId,
                    message = "Unable to confirm email: ${action.email} as email doesn't exist in the backend",
                    retryNativeAction = false,
                )

                else -> Failure(
                    actionId = action.actionId,
                    message = "Timeout reached to confirm email: ${action.email}. Link is still pending",
                    retryNativeAction = true,
                )
            }
        }.getOrElse { error ->
            logcat { "PIR-EMAIL: $error" }
            Failure(
                actionId = action.actionId,
                message = "Unknown error while getting email : ${error.message}",
                retryNativeAction = false,
            )
        }
    }

    private suspend fun handleGetEmail(action: GetEmail): NativeActionResult {
        return kotlin.runCatching {
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
    }

    private suspend fun handleSolveCaptcha(nativeAction: SubmitCaptchaInfo): NativeActionResult {
        return captchaResolver.submitCaptchaInformation(
            siteKey = nativeAction.siteKey,
            url = nativeAction.url,
            type = nativeAction.type,
        ).run {
            when (this) {
                is CaptchaResolverResult.CaptchaSubmitSuccess -> NativeActionResult.Success(
                    CaptchaTransactionIdReceived(
                        this.transactionID,
                    ),
                )

                is CaptchaResolverResult.CaptchaFailure -> if (this.type == CaptchaResolverError.TransientFailure) {
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

                else -> Failure(
                    actionId = nativeAction.actionId,
                    message = "Invalid scenario",
                    retryNativeAction = false,
                )
            }
        }
    }

    private suspend fun handleGetCaptchaSolutionStatus(nativeAction: GetCaptchaSolutionStatus): NativeActionResult {
        return captchaResolver.getCaptchaSolution(
            transactionID = nativeAction.transactionID,
        ).run {
            when (this) {
                is CaptchaResolverResult.SolveCaptchaSuccess -> NativeActionResult.Success(
                    data = CaptchaSolutionStatus(
                        status = CaptchaSolutionStatus.CaptchaStatus.Ready(
                            token = this.token,
                            meta = this.meta,
                        ),
                    ),
                )

                is CaptchaResolverResult.CaptchaFailure -> if (this.type == CaptchaResolverError.SolutionNotReady) {
                    NativeActionResult.Success(
                        data = CaptchaSolutionStatus(
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

                else -> Failure(
                    actionId = nativeAction.actionId,
                    message = "Invalid scenario",
                    retryNativeAction = false,
                )
            }
        }
    }
}
