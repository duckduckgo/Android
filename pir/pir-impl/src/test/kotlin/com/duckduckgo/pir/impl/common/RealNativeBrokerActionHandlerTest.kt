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
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.ClientFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.CriticalFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.InvalidRequest
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.SolutionNotReady
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.TransientFailure
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverError.UnableToSolveCaptcha
import com.duckduckgo.pir.impl.common.CaptchaResolver.CaptchaResolverResult
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeAction
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Failure
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success
import com.duckduckgo.pir.impl.common.NativeBrokerActionHandler.NativeActionResult.Success.NativeSuccessData
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaServiceError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaSolutionFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.ClientError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.EmailError
import com.duckduckgo.pir.impl.service.DbpService.CaptchaSolutionMeta
import com.duckduckgo.pir.impl.service.ResponseError
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirRepository.GeneratedEmailData
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

class RealNativeBrokerActionHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealNativeBrokerActionHandler

    private val mockRepository: PirRepository = mock()
    private val mockCaptchaResolver: CaptchaResolver = mock()
    private val moshi = Moshi.Builder().build()

    private val testActionId = "action-123"
    private val testBrokerName = "test-broker"
    private val testSiteKey = "test-site-key"
    private val testUrl = "https://test-url.com"
    private val testType = "recaptcha"
    private val testTransactionId = "transaction-123"
    private val testEmailAddress = "test@example.com"
    private val testPattern = "test-pattern"
    private val testCaptchaToken = "captcha-token-xyz"

    @Before
    fun setUp() {
        testee = RealNativeBrokerActionHandler(
            repository = mockRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            captchaResolver = mockCaptchaResolver,
            moshi = moshi,
        )
    }

    @Test
    fun whenGetEmailSucceedsThenReturnsSuccessWithEmailData() = runTest {
        val generatedEmailData = GeneratedEmailData(testEmailAddress, testPattern)
        val getEmailAction = NativeAction.GetEmail(testActionId, testBrokerName)
        whenever(mockRepository.getEmailForBroker(testBrokerName)).thenReturn(generatedEmailData)

        val result = testee.pushAction(getEmailAction)

        assertTrue(result is Success)
        val successResult = result as Success
        assertTrue(successResult.data is NativeSuccessData.Email)
        val emailData = successResult.data as NativeSuccessData.Email
        assertEquals(generatedEmailData, emailData.generatedEmailData)
        assertEquals(testEmailAddress, emailData.generatedEmailData.emailAddress)
        assertEquals(testPattern, emailData.generatedEmailData.pattern)
    }

    @Test
    fun whenGetEmailThrowsHttpExceptionThenReturnsEmailError() = runTest {
        val errorCode = 404
        val errorMessage = "Not found"
        val responseError = ResponseError(errorMessage)
        val errorJson = moshi.adapter(ResponseError::class.java).toJson(responseError)
        val responseBody = errorJson.toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<Any>(errorCode, responseBody))

        val getEmailAction = NativeAction.GetEmail(testActionId, testBrokerName)
        whenever(mockRepository.getEmailForBroker(testBrokerName)).thenThrow(httpException)

        val result = testee.pushAction(getEmailAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is EmailError)
        val emailError = failureResult.error as EmailError
        assertEquals(testActionId, emailError.actionID)
        assertEquals(errorCode, emailError.errorCode)
        assertTrue(emailError.error.contains("Error email generation:"))
        assertTrue(emailError.error.contains(errorCode.toString()))
        assertTrue(emailError.error.contains(errorMessage))
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenGetEmailThrowsHttpExceptionWithEmptyBodyThenReturnsEmailErrorWithCode() = runTest {
        val errorCode = 500
        val responseBody = "".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(Response.error<Any>(errorCode, responseBody))

        val getEmailAction = NativeAction.GetEmail(testActionId, testBrokerName)
        whenever(mockRepository.getEmailForBroker(testBrokerName)).thenThrow(httpException)

        val result = testee.pushAction(getEmailAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is EmailError)
        val emailError = failureResult.error as EmailError
        assertEquals(testActionId, emailError.actionID)
        assertEquals(errorCode, emailError.errorCode)
        assertTrue(emailError.error.contains("Error email generation:"))
        assertTrue(emailError.error.contains(errorCode.toString()))
    }

    @Test
    fun whenGetEmailThrowsGenericExceptionThenReturnsClientError() = runTest {
        val exceptionMessage = "Network error"
        val exception = RuntimeException(exceptionMessage)
        val getEmailAction = NativeAction.GetEmail(testActionId, testBrokerName)
        whenever(mockRepository.getEmailForBroker(testBrokerName)).thenThrow(exception)

        val result = testee.pushAction(getEmailAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is ClientError)
        val clientError = failureResult.error as ClientError
        assertEquals(testActionId, clientError.actionID)
        assertTrue(clientError.message.contains("Error email generation:"))
        assertTrue(clientError.message.contains(exceptionMessage))
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenSubmitCaptchaInfoSucceedsThenReturnsTransactionId() = runTest {
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val captchaSubmitSuccess = CaptchaResolverResult.CaptchaSubmitSuccess(testTransactionId)
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(captchaSubmitSuccess)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Success)
        val successResult = result as Success
        assertTrue(successResult.data is NativeSuccessData.CaptchaTransactionIdReceived)
        val transactionIdData = successResult.data as NativeSuccessData.CaptchaTransactionIdReceived
        assertEquals(testTransactionId, transactionIdData.transactionID)
    }

    @Test
    fun whenSubmitCaptchaInfoFailsWithSolutionNotReadyThenReturnsInProgress() = runTest {
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = 0,
            type = SolutionNotReady,
            message = "Solution not ready",
        )
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(captchaFailure)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Success)
        val successResult = result as Success
        assertTrue(successResult.data is NativeSuccessData.CaptchaSolutionStatus)
        val statusData = successResult.data as NativeSuccessData.CaptchaSolutionStatus
        assertTrue(statusData.status is NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.InProgress)
    }

    @Test
    fun whenSubmitCaptchaInfoFailsWithClientFailureThenReturnsClientError() = runTest {
        val errorMessage = "Client failure message"
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = 0,
            type = ClientFailure,
            message = errorMessage,
        )
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(captchaFailure)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is ClientError)
        val clientError = failureResult.error as ClientError
        assertEquals(testActionId, clientError.actionID)
        assertEquals(errorMessage, clientError.message)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenSubmitCaptchaInfoFailsWithCriticalFailureThenReturnsCaptchaServiceError() = runTest {
        val errorCode = 500
        val errorMessage = "Critical failure"
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = errorCode,
            type = CriticalFailure,
            message = errorMessage,
        )
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(captchaFailure)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaServiceError)
        val serviceError = failureResult.error as CaptchaServiceError
        assertEquals(testActionId, serviceError.actionID)
        assertEquals(errorCode, serviceError.errorCode)
        assertEquals(errorMessage, serviceError.errorDetails)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenSubmitCaptchaInfoFailsWithInvalidRequestThenReturnsCaptchaServiceError() = runTest {
        val errorCode = 400
        val errorMessage = "Invalid request"
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = errorCode,
            type = InvalidRequest,
            message = errorMessage,
        )
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(captchaFailure)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaServiceError)
        val serviceError = failureResult.error as CaptchaServiceError
        assertEquals(testActionId, serviceError.actionID)
        assertEquals(errorCode, serviceError.errorCode)
        assertEquals(errorMessage, serviceError.errorDetails)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenSubmitCaptchaInfoFailsWithTransientFailureThenReturnsCaptchaServiceErrorWithRetry() = runTest {
        val errorCode = 503
        val errorMessage = "Transient failure"
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = errorCode,
            type = TransientFailure,
            message = errorMessage,
        )
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(captchaFailure)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaServiceError)
        val serviceError = failureResult.error as CaptchaServiceError
        assertEquals(testActionId, serviceError.actionID)
        assertEquals(errorCode, serviceError.errorCode)
        assertEquals(errorMessage, serviceError.errorDetails)
        assertTrue(failureResult.retryNativeAction)
    }

    @Test
    fun whenSubmitCaptchaInfoFailsWithUnableToSolveCaptchaThenReturnsCaptchaSolutionFailed() = runTest {
        val errorMessage = "Unable to solve captcha"
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = 0,
            type = UnableToSolveCaptcha,
            message = errorMessage,
        )
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(captchaFailure)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaSolutionFailed)
        val solutionFailedError = failureResult.error as CaptchaSolutionFailed
        assertEquals(testActionId, solutionFailedError.actionID)
        assertEquals(errorMessage, solutionFailedError.message)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenSubmitCaptchaInfoReturnsInvalidScenarioThenReturnsClientError() = runTest {
        val submitCaptchaAction = NativeAction.SubmitCaptchaInfo(
            actionId = testActionId,
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val invalidResult = CaptchaResolverResult.SolveCaptchaSuccess(
            token = testCaptchaToken,
            meta = CaptchaSolutionMeta(
                backends = emptyMap(),
                type = "recaptcha",
                lastUpdated = 10.0f,
                lastBackend = "test-provider",
                timeToSolution = 10.0f,
            ),
        )
        whenever(
            mockCaptchaResolver.submitCaptchaInformation(
                siteKey = testSiteKey,
                url = testUrl,
                type = testType,
            ),
        ).thenReturn(invalidResult)

        val result = testee.pushAction(submitCaptchaAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is ClientError)
        val clientError = failureResult.error as ClientError
        assertEquals(testActionId, clientError.actionID)
        assertEquals("Invalid scenario", clientError.message)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenGetCaptchaSolutionStatusSucceedsThenReturnsReadyWithToken() = runTest {
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val meta = CaptchaSolutionMeta(
            backends = emptyMap(),
            type = "recaptcha",
            lastUpdated = 1.5f,
            lastBackend = "test-backend",
            timeToSolution = 30.0f,
        )
        val captchaSolutionSuccess = CaptchaResolverResult.SolveCaptchaSuccess(
            token = testCaptchaToken,
            meta = meta,
        )
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(captchaSolutionSuccess)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Success)
        val successResult = result as Success
        assertTrue(successResult.data is NativeSuccessData.CaptchaSolutionStatus)
        val statusData = successResult.data as NativeSuccessData.CaptchaSolutionStatus
        assertTrue(statusData.status is NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.Ready)
        val readyStatus = statusData.status as NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.Ready
        assertEquals(testCaptchaToken, readyStatus.token)
        assertEquals(meta, readyStatus.meta)
        assertEquals(30.0f, readyStatus.meta.timeToSolution)
        assertEquals("test-backend", readyStatus.meta.lastBackend)
    }

    @Test
    fun whenGetCaptchaSolutionStatusReturnsNotReadyThenReturnsInProgress() = runTest {
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = 0,
            type = SolutionNotReady,
            message = "Solution not ready",
        )
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(captchaFailure)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Success)
        val successResult = result as Success
        assertTrue(successResult.data is NativeSuccessData.CaptchaSolutionStatus)
        val statusData = successResult.data as NativeSuccessData.CaptchaSolutionStatus
        assertTrue(statusData.status is NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.InProgress)
    }

    @Test
    fun whenGetCaptchaSolutionStatusFailsWithClientFailureThenReturnsClientError() = runTest {
        val errorMessage = "Client failure"
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = 0,
            type = ClientFailure,
            message = errorMessage,
        )
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(captchaFailure)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is ClientError)
        val clientError = failureResult.error as ClientError
        assertEquals(testActionId, clientError.actionID)
        assertEquals(errorMessage, clientError.message)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenGetCaptchaSolutionStatusFailsWithCriticalFailureThenReturnsCaptchaServiceError() = runTest {
        val errorCode = 500
        val errorMessage = "Critical failure"
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = errorCode,
            type = CriticalFailure,
            message = errorMessage,
        )
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(captchaFailure)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaServiceError)
        val serviceError = failureResult.error as CaptchaServiceError
        assertEquals(testActionId, serviceError.actionID)
        assertEquals(errorCode, serviceError.errorCode)
        assertEquals(errorMessage, serviceError.errorDetails)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenGetCaptchaSolutionStatusFailsWithInvalidRequestThenReturnsCaptchaServiceError() = runTest {
        val errorCode = 400
        val errorMessage = "Invalid request"
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = errorCode,
            type = InvalidRequest,
            message = errorMessage,
        )
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(captchaFailure)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaServiceError)
        val serviceError = failureResult.error as CaptchaServiceError
        assertEquals(testActionId, serviceError.actionID)
        assertEquals(errorCode, serviceError.errorCode)
        assertEquals(errorMessage, serviceError.errorDetails)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenGetCaptchaSolutionStatusFailsWithTransientFailureThenReturnsCaptchaServiceErrorWithRetry() = runTest {
        val errorCode = 503
        val errorMessage = "Transient failure"
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = errorCode,
            type = TransientFailure,
            message = errorMessage,
        )
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(captchaFailure)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaServiceError)
        val serviceError = failureResult.error as CaptchaServiceError
        assertEquals(testActionId, serviceError.actionID)
        assertEquals(errorCode, serviceError.errorCode)
        assertEquals(errorMessage, serviceError.errorDetails)
        assertTrue(failureResult.retryNativeAction)
    }

    @Test
    fun whenGetCaptchaSolutionStatusFailsWithUnableToSolveCaptchaThenReturnsCaptchaSolutionFailed() = runTest {
        val errorMessage = "Unable to solve captcha"
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val captchaFailure = CaptchaResolverResult.CaptchaFailure(
            code = 0,
            type = UnableToSolveCaptcha,
            message = errorMessage,
        )
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(captchaFailure)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is CaptchaSolutionFailed)
        val solutionFailedError = failureResult.error as CaptchaSolutionFailed
        assertEquals(testActionId, solutionFailedError.actionID)
        assertEquals(errorMessage, solutionFailedError.message)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenGetCaptchaSolutionStatusReturnsInvalidScenarioThenReturnsClientError() = runTest {
        val solutionStatusAction = NativeAction.GetCaptchaSolutionStatus(
            actionId = testActionId,
            transactionID = testTransactionId,
        )
        val invalidResult = CaptchaResolverResult.CaptchaSubmitSuccess(testTransactionId)
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId))
            .thenReturn(invalidResult)

        val result = testee.pushAction(solutionStatusAction)

        assertTrue(result is Failure)
        val failureResult = result as Failure
        assertTrue(failureResult.error is ClientError)
        val clientError = failureResult.error as ClientError
        assertEquals(testActionId, clientError.actionID)
        assertEquals("Invalid scenario", clientError.message)
        assertFalse(failureResult.retryNativeAction)
    }

    @Test
    fun whenPushActionWithDifferentActionTypesThenRoutesToCorrectHandler() = runTest {
        val emailAction = NativeAction.GetEmail("email-action", testBrokerName)
        val captchaSubmitAction = NativeAction.SubmitCaptchaInfo("captcha-submit", testSiteKey, testUrl, testType)
        val captchaStatusAction = NativeAction.GetCaptchaSolutionStatus("captcha-status", testTransactionId)

        val emailData = GeneratedEmailData(testEmailAddress, testPattern)
        val submitSuccess = CaptchaResolverResult.CaptchaSubmitSuccess(testTransactionId)
        val solutionSuccess = CaptchaResolverResult.SolveCaptchaSuccess(
            testCaptchaToken,
            CaptchaSolutionMeta(
                backends = emptyMap(),
                type = "recaptcha",
                lastUpdated = 1.0f,
                lastBackend = "backend",
                timeToSolution = 10.0f,
            ),
        )

        whenever(mockRepository.getEmailForBroker(testBrokerName)).thenReturn(emailData)
        whenever(mockCaptchaResolver.submitCaptchaInformation(testSiteKey, testUrl, testType)).thenReturn(submitSuccess)
        whenever(mockCaptchaResolver.getCaptchaSolution(testTransactionId)).thenReturn(solutionSuccess)

        val emailResult = testee.pushAction(emailAction)
        val captchaSubmitResult = testee.pushAction(captchaSubmitAction)
        val captchaStatusResult = testee.pushAction(captchaStatusAction)

        assertTrue(emailResult is Success)
        assertTrue((emailResult as Success).data is NativeSuccessData.Email)

        assertTrue(captchaSubmitResult is Success)
        assertTrue((captchaSubmitResult as Success).data is NativeSuccessData.CaptchaTransactionIdReceived)

        assertTrue(captchaStatusResult is Success)
        assertTrue((captchaStatusResult as Success).data is NativeSuccessData.CaptchaSolutionStatus)
    }

    @Test
    fun whenMultipleGetEmailCallsWithDifferentBrokersThenHandlesIndependently() = runTest {
        val broker1 = "broker-1"
        val broker2 = "broker-2"
        val email1 = GeneratedEmailData("email1@example.com", "pattern1")
        val email2 = GeneratedEmailData("email2@example.com", "pattern2")

        val action1 = NativeAction.GetEmail("action-1", broker1)
        val action2 = NativeAction.GetEmail("action-2", broker2)

        whenever(mockRepository.getEmailForBroker(broker1)).thenReturn(email1)
        whenever(mockRepository.getEmailForBroker(broker2)).thenReturn(email2)

        val result1 = testee.pushAction(action1)
        val result2 = testee.pushAction(action2)

        assertTrue(result1 is Success)
        val emailData1 = ((result1 as Success).data as NativeSuccessData.Email).generatedEmailData
        assertEquals("email1@example.com", emailData1.emailAddress)

        assertTrue(result2 is Success)
        val emailData2 = ((result2 as Success).data as NativeSuccessData.Email).generatedEmailData
        assertEquals("email2@example.com", emailData2.emailAddress)
    }

    @Test
    fun whenCaptchaWorkflowFromSubmitToCompletionThenReturnsCorrectSequence() = runTest {
        val submitAction = NativeAction.SubmitCaptchaInfo(
            actionId = "submit-action",
            siteKey = testSiteKey,
            url = testUrl,
            type = testType,
        )
        val submitSuccess = CaptchaResolverResult.CaptchaSubmitSuccess(testTransactionId)
        whenever(mockCaptchaResolver.submitCaptchaInformation(testSiteKey, testUrl, testType))
            .thenReturn(submitSuccess)

        val submitResult = testee.pushAction(submitAction)

        assertTrue(submitResult is Success)
        val transactionId = ((submitResult as Success).data as NativeSuccessData.CaptchaTransactionIdReceived).transactionID
        assertEquals(testTransactionId, transactionId)

        val statusAction1 = NativeAction.GetCaptchaSolutionStatus("status-action-1", transactionId)
        val notReadyFailure = CaptchaResolverResult.CaptchaFailure(0, SolutionNotReady, "Not ready")
        whenever(mockCaptchaResolver.getCaptchaSolution(transactionId)).thenReturn(notReadyFailure)

        val statusResult1 = testee.pushAction(statusAction1)

        assertTrue(statusResult1 is Success)
        val status1 = ((statusResult1 as Success).data as NativeSuccessData.CaptchaSolutionStatus).status
        assertTrue(status1 is NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.InProgress)

        val statusAction2 = NativeAction.GetCaptchaSolutionStatus("status-action-2", transactionId)
        val meta = CaptchaSolutionMeta(
            backends = emptyMap(),
            type = "recaptcha",
            lastUpdated = 2.0f,
            lastBackend = "backend",
            timeToSolution = 15.0f,
        )
        val readySuccess = CaptchaResolverResult.SolveCaptchaSuccess(testCaptchaToken, meta)
        whenever(mockCaptchaResolver.getCaptchaSolution(transactionId)).thenReturn(readySuccess)

        val statusResult2 = testee.pushAction(statusAction2)

        assertTrue(statusResult2 is Success)
        val status2 = ((statusResult2 as Success).data as NativeSuccessData.CaptchaSolutionStatus).status
        assertTrue(status2 is NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.Ready)
        val readyStatus = status2 as NativeSuccessData.CaptchaSolutionStatus.CaptchaStatus.Ready
        assertEquals(testCaptchaToken, readyStatus.token)
        assertEquals(meta, readyStatus.meta)
    }
}
