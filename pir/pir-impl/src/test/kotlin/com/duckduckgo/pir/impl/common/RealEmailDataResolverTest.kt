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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.common.EmailDataResolver.EmailDataResolverResult
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest.RequestEmailData
import com.duckduckgo.pir.impl.service.DbpService.PirGetEmailConfirmationLinkResponse
import com.duckduckgo.pir.impl.service.DbpService.PirGetEmailConfirmationLinkResponse.ResponseItem
import com.duckduckgo.pir.impl.service.DbpService.PirGetEmailConfirmationLinkResponse.ResponseItemData
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response

class RealEmailDataResolverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealEmailDataResolver

    private val mockDbpService: DbpService = mock()
    private val moshi = Moshi.Builder().build()

    private val testEmail = "generated@example.com"
    private val testAttemptId = "attempt-123"

    private fun expectedRequest() = PirEmailConfirmationDataRequest(
        items = listOf(
            RequestEmailData(
                email = testEmail,
                attemptId = testAttemptId,
            ),
        ),
    )

    private fun responseItem(
        status: String,
        data: List<ResponseItemData> = emptyList(),
        errorCode: String? = null,
        error: String? = null,
    ) = ResponseItem(
        email = testEmail,
        attemptId = testAttemptId,
        status = status,
        emailAddressCreatedAt = 0L,
        emailReceivedAt = 0L,
        data = data,
        errorCode = errorCode,
        error = error,
    )

    @Before
    fun setUp() {
        testee = RealEmailDataResolver(
            dbpService = mockDbpService,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            moshi = moshi,
        )
    }

    @Test
    fun whenStatusIsReadyThenReturnsSuccessWithExtractedData() = runTest {
        val response = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                responseItem(
                    status = "ready",
                    data = listOf(
                        ResponseItemData(name = "verificationCode", value = "123456"),
                        ResponseItemData(name = "magicLink", value = "https://example.com/x"),
                    ),
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest()))).thenReturn(response)

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Success)
        val success = result as EmailDataResolverResult.Success
        assertEquals("123456", success.extractedData["verificationCode"])
        assertEquals("https://example.com/x", success.extractedData["magicLink"])
        verify(mockDbpService).getEmailConfirmationLinkStatus(eq(expectedRequest()))
    }

    @Test
    fun whenStatusIsReadyWithEmptyDataThenReturnsSuccessWithEmptyMap() = runTest {
        val response = PirGetEmailConfirmationLinkResponse(
            items = listOf(responseItem(status = "ready", data = emptyList())),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest()))).thenReturn(response)

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Success)
        assertTrue((result as EmailDataResolverResult.Success).extractedData.isEmpty())
    }

    @Test
    fun whenStatusIsReadyUppercaseThenStillReturnsSuccess() = runTest {
        val response = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                responseItem(
                    status = "READY",
                    data = listOf(ResponseItemData(name = "verificationCode", value = "999")),
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest()))).thenReturn(response)

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Success)
    }

    @Test
    fun whenStatusIsPendingThenReturnsPending() = runTest {
        val response = PirGetEmailConfirmationLinkResponse(
            items = listOf(responseItem(status = "pending")),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest()))).thenReturn(response)

        val result = testee.poll(testEmail, testAttemptId)

        assertEquals(EmailDataResolverResult.Pending, result)
    }

    @Test
    fun whenStatusIsUnknownThenReturnsFailure() = runTest {
        val response = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                responseItem(
                    status = "error",
                    errorCode = "boom",
                    error = "Something went wrong",
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest()))).thenReturn(response)

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Failure)
        val failure = result as EmailDataResolverResult.Failure
        assertEquals(0, failure.code)
        assertTrue(failure.message.contains("boom"))
        assertTrue(failure.message.contains("Something went wrong"))
    }

    @Test
    fun whenResponseHasNoItemsThenReturnsFailure() = runTest {
        val response = PirGetEmailConfirmationLinkResponse(items = emptyList())
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest()))).thenReturn(response)

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Failure)
        val failure = result as EmailDataResolverResult.Failure
        assertEquals(0, failure.code)
        assertTrue(failure.message.contains("Empty response items"))
    }

    @Test
    fun whenHttpExceptionThenReturnsFailureWithHttpCode() = runTest {
        val errorBody = """{"message":"Server error"}""".toResponseBody("application/json".toMediaType())
        val httpException = HttpException(
            Response.error<PirGetEmailConfirmationLinkResponse>(500, errorBody),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest()))).thenThrow(httpException)

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Failure)
        val failure = result as EmailDataResolverResult.Failure
        assertEquals(500, failure.code)
        assertTrue(failure.message.contains("500"))
        assertTrue(failure.message.contains("Server error"))
    }

    @Test
    fun whenNonHttpExceptionThenReturnsFailureWithZeroCode() = runTest {
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest())))
            .thenThrow(RuntimeException("Network timeout"))

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Failure)
        val failure = result as EmailDataResolverResult.Failure
        assertEquals(0, failure.code)
        assertTrue(failure.message.contains("Network timeout"))
    }

    @Test
    fun whenNonHttpExceptionWithNoMessageThenReturnsFailureWithUnknownError() = runTest {
        whenever(mockDbpService.getEmailConfirmationLinkStatus(eq(expectedRequest())))
            .thenThrow(RuntimeException())

        val result = testee.poll(testEmail, testAttemptId)

        assertTrue(result is EmailDataResolverResult.Failure)
        val failure = result as EmailDataResolverResult.Failure
        assertEquals(0, failure.code)
        assertTrue(failure.message.contains("Unknown error"))
    }
}
