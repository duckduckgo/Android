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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.GetEmail
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeAction.GetEmailStatus
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult
import com.duckduckgo.pir.internal.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus.Ready
import com.duckduckgo.pir.internal.store.PirRepository.ConfirmationStatus.Unknown
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
            val pollingIntervalSeconds: Float,
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
            }
        }

        data class Failure(
            val actionId: String,
            val message: String,
        ) : NativeActionResult()
    }
}

@ContributesBinding(AppScope::class)
class RealNativeBrokerActionHandler @Inject constructor(
    private val repository: PirRepository,
    private val dispatcherProvider: DispatcherProvider,
) : NativeBrokerActionHandler {
    override suspend fun pushAction(nativeAction: NativeAction): NativeActionResult = withContext(dispatcherProvider.io()) {
        when (nativeAction) {
            is GetEmailStatus -> handleAwaitConfirmation(nativeAction)
            is GetEmail -> handleGetEmail(nativeAction)
        }
    }

    private suspend fun handleAwaitConfirmation(action: GetEmailStatus): NativeActionResult {
        return kotlin.runCatching {
            var attempt = 0
            var result: Pair<ConfirmationStatus, String?> = Unknown to null
            while (attempt < 2) {
                result = repository.getEmailConfirmation(action.email)
                if (result.first is Ready) {
                    return NativeActionResult.Success(
                        data = NativeSuccessData.EmailConfirmation(
                            email = action.email,
                            link = result.second!!,
                            status = result.first,
                        ),
                    )
                } else if (result.first is Unknown) {
                    return NativeActionResult.Failure(
                        actionId = action.actionId,
                        message = "Unable to confirm email: ${action.email} as email doesn't exist in the backend",
                    )
                } else if (attempt == 1) {
                    return NativeActionResult.Failure(
                        actionId = action.actionId,
                        message = "Timeout reached to confirm email: ${action.email}. Link is still pending",
                    )
                } else {
                    delay(action.pollingIntervalSeconds.toLong() * 1000)
                    attempt++
                }
            }

            NativeActionResult.Failure(
                actionId = action.actionId,
                message = "Unable to confirm email: ${action.email}, last status: ${result.first} }",
            )
        }.getOrElse { error ->
            NativeActionResult.Failure(
                actionId = action.actionId,
                message = "Unknown error while getting email : ${error.message}",
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
            NativeActionResult.Failure(
                actionId = action.actionId,
                message = "Unknown error while getting email : ${error.message}",
            )
        }
    }
}
