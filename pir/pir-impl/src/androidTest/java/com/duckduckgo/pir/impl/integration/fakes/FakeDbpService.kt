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

import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest
import com.duckduckgo.pir.impl.service.DbpService.PirGetCaptchaSolutionResponse
import com.duckduckgo.pir.impl.service.DbpService.PirGetEmailConfirmationLinkResponse
import com.duckduckgo.pir.impl.service.DbpService.PirGetEmailResponse
import com.duckduckgo.pir.impl.service.DbpService.PirMainConfig
import com.duckduckgo.pir.impl.service.DbpService.PirStartCaptchaSolutionBody
import com.duckduckgo.pir.impl.service.DbpService.PirStartCaptchaSolutionResponse
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Fake implementation of DbpService for integration tests.
 * Provides configurable responses for API calls.
 */
class FakeDbpService : DbpService {

    // Configurable response for email confirmation link status
    var nextEmailConfirmationLinkResponse: PirGetEmailConfirmationLinkResponse =
        PirGetEmailConfirmationLinkResponse(items = emptyList())

    // Email counter for generating unique emails
    private var emailCounter = 0

    override suspend fun getMainConfig(etag: String?): Response<PirMainConfig> {
        // Not used in E2E tests - brokers are loaded directly
        throw NotImplementedError("getMainConfig is not used in E2E tests")
    }

    override suspend fun getBrokerJsonFiles(): ResponseBody {
        // Not used in E2E tests - brokers are loaded directly
        throw NotImplementedError("getBrokerJsonFiles is not used in E2E tests")
    }

    override suspend fun getEmail(dataBrokerUrl: String, attemptId: String?): PirGetEmailResponse {
        emailCounter++
        return PirGetEmailResponse(
            emailAddress = "test-$emailCounter@duck.com",
            pattern = "pattern-$emailCounter",
        )
    }

    override suspend fun submitCaptchaInformation(
        body: PirStartCaptchaSolutionBody,
        attemptId: String?,
    ): PirStartCaptchaSolutionResponse {
        return PirStartCaptchaSolutionResponse(
            message = "success",
            transactionId = "fake-transaction-id",
        )
    }

    override suspend fun getCaptchaSolution(
        transactionId: String,
        attemptId: String?,
    ): PirGetCaptchaSolutionResponse {
        return PirGetCaptchaSolutionResponse(
            message = "ready",
            data = "fake-captcha-solution",
            meta = DbpService.CaptchaSolutionMeta(
                backends = emptyMap(),
                type = "recaptcha",
                lastUpdated = 0f,
                lastBackend = "test",
                timeToSolution = 1.0f,
            ),
        )
    }

    override suspend fun getEmailConfirmationLinkStatus(
        request: PirEmailConfirmationDataRequest,
    ): PirGetEmailConfirmationLinkResponse {
        return nextEmailConfirmationLinkResponse
    }

    override suspend fun deleteEmailData(request: PirEmailConfirmationDataRequest) {
        // No-op in tests
    }

    /**
     * Helper to create a "ready" response with a link for a given email and attemptId.
     */
    fun setEmailConfirmationLinkReady(email: String, attemptId: String, link: String) {
        nextEmailConfirmationLinkResponse = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = email,
                    attemptId = attemptId,
                    status = "ready",
                    emailAddressCreatedAt = System.currentTimeMillis() / 1000,
                    emailReceivedAt = System.currentTimeMillis() / 1000,
                    data = listOf(
                        PirGetEmailConfirmationLinkResponse.ResponseItemData(
                            name = "link",
                            value = link,
                        ),
                    ),
                ),
            ),
        )
    }
}
