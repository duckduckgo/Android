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

package com.duckduckgo.pir.impl.integration.fakes

import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData
import com.duckduckgo.pir.impl.service.DbpService.CaptchaSolutionMeta
import com.duckduckgo.pir.impl.store.PirRepository.GeneratedEmailData

/**
 * A fake native action handler for integration tests that returns success for all native actions.
 */
class FakeNativeBrokerActionHandler : NativeBrokerActionHandler {

    override suspend fun pushAction(nativeAction: NativeAction): NativeActionResult {
        return when (nativeAction) {
            is NativeAction.GetEmail -> Success(
                data = NativeSuccessData.Email(
                    generatedEmailData = GeneratedEmailData(
                        emailAddress = "test@duck.com",
                        pattern = "test-pattern-123",
                    ),
                ),
            )
            is NativeAction.GetCaptchaSolutionStatus -> Success(
                data = NativeSuccessData.CaptchaSolutionStatus(
                    status = NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.Ready(
                        token = "fake-captcha-token",
                        meta = CaptchaSolutionMeta(
                            backends = emptyMap(),
                            type = "test",
                            lastUpdated = 0f,
                            lastBackend = "test",
                            timeToSolution = 0f,
                        ),
                    ),
                ),
            )
            is NativeAction.SubmitCaptchaInfo -> Success(
                data = NativeSuccessData.CaptchaTransactionIdReceived(
                    transactionID = "fake-transaction-id",
                ),
            )
        }
    }
}
