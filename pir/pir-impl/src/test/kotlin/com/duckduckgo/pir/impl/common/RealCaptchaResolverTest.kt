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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.CaptchaSolutionBackend
import com.duckduckgo.pir.impl.service.DbpService.CaptchaSolutionMeta
import com.duckduckgo.pir.impl.service.DbpService.PirGetCaptchaSolutionResponse
import com.duckduckgo.pir.impl.service.DbpService.PirStartCaptchaSolutionBody
import com.duckduckgo.pir.impl.service.DbpService.PirStartCaptchaSolutionResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

class RealCaptchaResolverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealCaptchaResolver

    private val mockDbpService: DbpService = mock()
    private val moshi = Moshi.Builder().build()

    private val testSiteKey = "test-site-key-123"
    private val testUrl = "https://test-broker.com/captcha"
    private val testType = "recaptcha"
    private val testAttemptId = "attempt-123"
    private val testTransactionId = "transaction-456"
    private val testToken = "captcha-solution-token"

    private val testCaptchaMeta = CaptchaSolutionMeta(
        backends = mapOf(
            "backend1" to CaptchaSolutionBackend(
                solveAttempts = 1,
                pollAttempts = 2,
                error = 0,
            ),
        ),
        type = "recaptcha",
        lastUpdated = 123.45f,
        lastBackend = "backend1",
        timeToSolution = 5.6f,
    )

    @Before
    fun setUp() {
        testee = RealCaptchaResolver(
            dbpService = mockDbpService,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            moshi = moshi,
        )
    }

    @Test
    fun whenSubmitCaptchaInformationSucceedsThenReturnsSubmitSuccess() = runTest {
        val expectedResponse = PirStartCaptchaSolutionResponse(
            message = "SUCCESS",
            transactionId = testTransactionId,
        )
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaSubmitSuccess)
        assertEquals(testTransactionId, (result as CaptchaResolverResult.CaptchaSubmitSuccess).transactionID)
        verify(mockDbpService).submitCaptchaInformation(
            eq(
                PirStartCaptchaSolutionBody(
                    siteKey = testSiteKey,
                    url = testUrl,
                    type = testType,
                    backend = null,
                ),
            ),
            eq(null),
        )
    }

    @Test
    fun whenSubmitCaptchaInformationSucceedsWithAttemptIdThenReturnsSubmitSuccess() = runTest {
        val expectedResponse = PirStartCaptchaSolutionResponse(
            message = "SUCCESS",
            transactionId = testTransactionId,
        )
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(testAttemptId),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
            attemptId = testAttemptId,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaSubmitSuccess)
        assertEquals(testTransactionId, (result as CaptchaResolverResult.CaptchaSubmitSuccess).transactionID)
        verify(mockDbpService).submitCaptchaInformation(
            any(),
            eq(testAttemptId),
        )
    }

    @Test
    fun whenSubmitCaptchaInformationFailsWithInvalidRequestThenReturnsInvalidRequestFailure() = runTest {
        val errorBody = """{"message":"INVALID_REQUEST: Missing required field"}""".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(
            Response.error<PirStartCaptchaSolutionResponse>(
                400,
                errorBody,
            ),
        )
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenThrow(httpException)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(400, failure.code)
        assertEquals(CaptchaResolverError.InvalidRequest, failure.type)
        assertTrue(failure.message.startsWith("Submit captcha error: 400"))
        assertTrue(failure.message.contains("INVALID_REQUEST"))
    }

    @Test
    fun whenSubmitCaptchaInformationFailsWithTransientFailureThenReturnsTransientFailure() = runTest {
        val errorBody = """{"message":"FAILURE_TRANSIENT: Temporary service unavailable"}""".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(
            Response.error<PirStartCaptchaSolutionResponse>(
                503,
                errorBody,
            ),
        )
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenThrow(httpException)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(503, failure.code)
        assertEquals(CaptchaResolverError.TransientFailure, failure.type)
        assertTrue(failure.message.startsWith("Submit captcha error: 503"))
        assertTrue(failure.message.contains("FAILURE_TRANSIENT"))
    }

    @Test
    fun whenSubmitCaptchaInformationFailsWithOtherHttpErrorThenReturnsCriticalFailure() = runTest {
        val errorBody = """{"message":"INTERNAL_ERROR: Something went wrong"}""".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(
            Response.error<PirStartCaptchaSolutionResponse>(
                500,
                errorBody,
            ),
        )
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenThrow(httpException)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(500, failure.code)
        assertEquals(CaptchaResolverError.CriticalFailure, failure.type)
        assertTrue(failure.message.startsWith("Submit captcha error: 500"))
    }

    @Test
    fun whenSubmitCaptchaInformationFailsWithEmptyErrorBodyThenReturnsCriticalFailure() = runTest {
        val errorBody = "".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(
            Response.error<PirStartCaptchaSolutionResponse>(
                500,
                errorBody,
            ),
        )
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenThrow(httpException)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(500, failure.code)
        assertEquals(CaptchaResolverError.CriticalFailure, failure.type)
        assertEquals("Submit captcha error: 500 ", failure.message)
    }

    @Test
    fun whenSubmitCaptchaInformationFailsWithNonHttpExceptionThenReturnsClientFailure() = runTest {
        val exception = RuntimeException("Network connection failed")
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenThrow(exception)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(0, failure.code)
        assertEquals(CaptchaResolverError.ClientFailure, failure.type)
        assertEquals("Submit captcha error: Network connection failed", failure.message)
    }

    @Test
    fun whenSubmitCaptchaInformationFailsWithNonHttpExceptionAndNoMessageThenReturnsClientFailureWithUnknownError() = runTest {
        val exception = RuntimeException()
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenThrow(exception)

        val result = testee.submitCaptchaInformation(
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(0, failure.code)
        assertEquals(CaptchaResolverError.ClientFailure, failure.type)
        assertEquals("Submit captcha error: Unknown error", failure.message)
    }

    @Test
    fun whenGetCaptchaSolutionSucceedsThenReturnsSolveSuccess() = runTest {
        val expectedResponse = PirGetCaptchaSolutionResponse(
            message = "SUCCESS",
            data = testToken,
            meta = testCaptchaMeta,
        )
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(null),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
        )

        assertTrue(result is CaptchaResolverResult.SolveCaptchaSuccess)
        val success = result as CaptchaResolverResult.SolveCaptchaSuccess
        assertEquals(testToken, success.token)
        assertEquals(testCaptchaMeta, success.meta)
        verify(mockDbpService).getCaptchaSolution(
            eq(testTransactionId),
            eq(null),
        )
    }

    @Test
    fun whenGetCaptchaSolutionSucceedsWithAttemptIdThenReturnsSolveSuccess() = runTest {
        val expectedResponse = PirGetCaptchaSolutionResponse(
            message = "COMPLETE",
            data = testToken,
            meta = testCaptchaMeta,
        )
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(testAttemptId),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
            attemptId = testAttemptId,
        )

        assertTrue(result is CaptchaResolverResult.SolveCaptchaSuccess)
        val success = result as CaptchaResolverResult.SolveCaptchaSuccess
        assertEquals(testToken, success.token)
        assertEquals(testCaptchaMeta, success.meta)
        verify(mockDbpService).getCaptchaSolution(
            eq(testTransactionId),
            eq(testAttemptId),
        )
    }

    @Test
    fun whenGetCaptchaSolutionReturnsNotReadyThenReturnsSolutionNotReadyFailure() = runTest {
        val expectedResponse = PirGetCaptchaSolutionResponse(
            message = "SOLUTION_NOT_READY",
            data = "",
            meta = testCaptchaMeta,
        )
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(null),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(0, failure.code)
        assertEquals(CaptchaResolverError.SolutionNotReady, failure.type)
        assertEquals("SOLUTION_NOT_READY", failure.message)
    }

    @Test
    fun whenGetCaptchaSolutionReturnsFailureThenReturnsUnableToSolveCaptchaFailure() = runTest {
        val expectedResponse = PirGetCaptchaSolutionResponse(
            message = "FAILURE",
            data = "",
            meta = testCaptchaMeta,
        )
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(null),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(0, failure.code)
        assertEquals(CaptchaResolverError.UnableToSolveCaptcha, failure.type)
        assertEquals("Solve captcha error: FAILURE", failure.message)
    }

    @Test
    fun whenGetCaptchaSolutionFailsWithHttpExceptionThenReturnsInvalidRequestFailure() = runTest {
        val errorBody = """{"message":"Invalid transaction ID"}""".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(
            Response.error<PirGetCaptchaSolutionResponse>(
                404,
                errorBody,
            ),
        )
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(null),
            ),
        ).thenThrow(httpException)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(404, failure.code)
        assertEquals(CaptchaResolverError.InvalidRequest, failure.type)
        assertTrue(failure.message.startsWith("Solve captcha error: 404"))
        assertTrue(failure.message.contains("Invalid transaction ID"))
    }

    @Test
    fun whenGetCaptchaSolutionFailsWithHttpExceptionAndEmptyBodyThenReturnsInvalidRequestFailure() = runTest {
        val errorBody = "".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(
            Response.error<PirGetCaptchaSolutionResponse>(
                401,
                errorBody,
            ),
        )
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(null),
            ),
        ).thenThrow(httpException)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(401, failure.code)
        assertEquals(CaptchaResolverError.InvalidRequest, failure.type)
        assertEquals("Solve captcha error: 401 ", failure.message)
    }

    @Test
    fun whenGetCaptchaSolutionFailsWithNonHttpExceptionThenReturnsClientFailure() = runTest {
        val exception = RuntimeException("Network timeout")
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(null),
            ),
        ).thenThrow(exception)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(0, failure.code)
        assertEquals(CaptchaResolverError.ClientFailure, failure.type)
        assertEquals("Solve captcha error: Network timeout", failure.message)
    }

    @Test
    fun whenGetCaptchaSolutionFailsWithNonHttpExceptionAndNoMessageThenReturnsClientFailureWithUnknownError() = runTest {
        val exception = RuntimeException()
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(testTransactionId),
                eq(null),
            ),
        ).thenThrow(exception)

        val result = testee.getCaptchaSolution(
            transactionID = testTransactionId,
        )

        assertTrue(result is CaptchaResolverResult.CaptchaFailure)
        val failure = result as CaptchaResolverResult.CaptchaFailure
        assertEquals(0, failure.code)
        assertEquals(CaptchaResolverError.ClientFailure, failure.type)
        assertEquals("Solve captcha error: Unknown error", failure.message)
    }

    @Test
    fun whenSubmitCaptchaInformationWithEmptyParametersThenStillCallsService() = runTest {
        val expectedResponse = PirStartCaptchaSolutionResponse(
            message = "SUCCESS",
            transactionId = testTransactionId,
        )
        whenever(
            mockDbpService.submitCaptchaInformation(
                any(),
                eq(null),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.submitCaptchaInformation(
            siteKey = "",
            url = "",
            type = "",
        )

        assertTrue(result is CaptchaResolverResult.CaptchaSubmitSuccess)
        verify(mockDbpService).submitCaptchaInformation(
            eq(
                PirStartCaptchaSolutionBody(
                    siteKey = "",
                    url = "",
                    type = "",
                    backend = null,
                ),
            ),
            eq(null),
        )
    }

    @Test
    fun whenGetCaptchaSolutionWithEmptyTransactionIdThenStillCallsService() = runTest {
        val expectedResponse = PirGetCaptchaSolutionResponse(
            message = "SUCCESS",
            data = testToken,
            meta = testCaptchaMeta,
        )
        whenever(
            mockDbpService.getCaptchaSolution(
                eq(""),
                eq(null),
            ),
        ).thenReturn(expectedResponse)

        val result = testee.getCaptchaSolution(
            transactionID = "",
        )

        assertTrue(result is CaptchaResolverResult.SolveCaptchaSuccess)
        verify(mockDbpService).getCaptchaSolution(
            eq(""),
            eq(null),
        )
    }
}
