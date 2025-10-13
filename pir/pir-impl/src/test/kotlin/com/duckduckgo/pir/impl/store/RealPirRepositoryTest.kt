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

package com.duckduckgo.pir.impl.store

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest
import com.duckduckgo.pir.impl.service.DbpService.PirGetEmailConfirmationLinkResponse
import com.duckduckgo.pir.impl.store.PirRepository.EmailConfirmationLinkFetchStatus
import com.duckduckgo.pir.impl.store.db.BrokerDao
import com.duckduckgo.pir.impl.store.db.BrokerJsonDao
import com.duckduckgo.pir.impl.store.db.ExtractedProfileDao
import com.duckduckgo.pir.impl.store.db.UserProfileDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirRepositoryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirRepository

    private val mockPirDataStore: PirDataStore = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockBrokerJsonDao: BrokerJsonDao = mock()
    private val mockBrokerDao: BrokerDao = mock()
    private val mockUserProfileDao: UserProfileDao = mock()
    private val mockDbpService: DbpService = mock()
    private val mockExtractedProfileDao: ExtractedProfileDao = mock()

    @Before
    fun setUp() {
        testee = RealPirRepository(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirDataStore = mockPirDataStore,
            currentTimeProvider = mockCurrentTimeProvider,
            brokerJsonDao = mockBrokerJsonDao,
            brokerDao = mockBrokerDao,
            userProfileDao = mockUserProfileDao,
            dbpService = mockDbpService,
            extractedProfileDao = mockExtractedProfileDao,
        )
    }

    // Test data
    private val testEmailData1 = EmailData(email = "test1@example.com", attemptId = "attempt-123")
    private val testEmailData2 = EmailData(email = "test2@example.com", attemptId = "attempt-456")
    private val testEmailData3 = EmailData(email = "test3@example.com", attemptId = "attempt-789")

    @Test
    fun whenGetEmailConfirmationLinkStatusWithReadyStatusThenReturnReadyWithData() = runTest {
        // Given
        val emailDataList = listOf(testEmailData1)
        val responseData = listOf(
            PirGetEmailConfirmationLinkResponse.ResponseItemData(name = "link", value = "https://example.com/confirm"),
            PirGetEmailConfirmationLinkResponse.ResponseItemData(name = "token", value = "abc123"),
        )
        val mockResponse = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = testEmailData1.email,
                    attemptId = testEmailData1.attemptId,
                    status = "ready",
                    emailAddressCreatedAt = 1000L,
                    data = responseData,
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(any())).thenReturn(mockResponse)

        // When
        val result = testee.getEmailConfirmationLinkStatus(emailDataList)

        // Then
        assertEquals(1, result.size)
        val status = result[testEmailData1] as EmailConfirmationLinkFetchStatus.Ready
        assertEquals("https://example.com/confirm", status.data["link"])
        assertEquals("abc123", status.data["token"])
    }

    @Test
    fun whenGetEmailConfirmationLinkStatusWithPendingStatusThenReturnPending() = runTest {
        // Given
        val emailDataList = listOf(testEmailData1)
        val mockResponse = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = testEmailData1.email,
                    attemptId = testEmailData1.attemptId,
                    status = "pending",
                    emailAddressCreatedAt = 1000L,
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(any())).thenReturn(mockResponse)

        // When
        val result = testee.getEmailConfirmationLinkStatus(emailDataList)

        // Then
        assertEquals(1, result.size)
        assertEquals(EmailConfirmationLinkFetchStatus.Pending, result[testEmailData1])
    }

    @Test
    fun whenGetEmailConfirmationLinkStatusWithErrorStatusThenReturnError() = runTest {
        // Given
        val emailDataList = listOf(testEmailData1)
        val mockResponse = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = testEmailData1.email,
                    attemptId = testEmailData1.attemptId,
                    status = "error",
                    emailAddressCreatedAt = 1000L,
                    errorCode = "EMAIL_NOT_FOUND",
                    error = "Email not found in inbox",
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(any())).thenReturn(mockResponse)

        // When
        val result = testee.getEmailConfirmationLinkStatus(emailDataList)

        // Then
        assertEquals(1, result.size)
        val status = result[testEmailData1] as EmailConfirmationLinkFetchStatus.Error
        assertEquals("EMAIL_NOT_FOUND", status.errorCode)
        assertEquals("Email not found in inbox", status.error)
    }

    @Test
    fun whenGetEmailConfirmationLinkStatusWithUnknownStatusThenReturnUnknown() = runTest {
        // Given
        val emailDataList = listOf(testEmailData1)
        val mockResponse = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = testEmailData1.email,
                    attemptId = testEmailData1.attemptId,
                    status = "invalid_status",
                    emailAddressCreatedAt = 1000L,
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(any())).thenReturn(mockResponse)

        // When
        val result = testee.getEmailConfirmationLinkStatus(emailDataList)

        // Then
        assertEquals(1, result.size)
        assertEquals(EmailConfirmationLinkFetchStatus.Unknown(), result[testEmailData1])
    }

    @Test
    fun whenGetEmailConfirmationLinkStatusWithMultipleEmailsThenReturnMappedResults() = runTest {
        // Given
        val emailDataList = listOf(testEmailData1, testEmailData2, testEmailData3)
        val mockResponse = PirGetEmailConfirmationLinkResponse(
            items = listOf(
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = testEmailData1.email,
                    attemptId = testEmailData1.attemptId,
                    status = "ready",
                    emailAddressCreatedAt = 1000L,
                    data = listOf(PirGetEmailConfirmationLinkResponse.ResponseItemData("link", "https://example1.com")),
                ),
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = testEmailData2.email,
                    attemptId = testEmailData2.attemptId,
                    status = "pending",
                    emailAddressCreatedAt = 2000L,
                ),
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = testEmailData3.email,
                    attemptId = testEmailData3.attemptId,
                    status = "error",
                    emailAddressCreatedAt = 3000L,
                    errorCode = "TIMEOUT",
                    error = "Request timeout",
                ),
            ),
        )
        whenever(mockDbpService.getEmailConfirmationLinkStatus(any())).thenReturn(mockResponse)

        // When
        val result = testee.getEmailConfirmationLinkStatus(emailDataList)

        // Then
        assertEquals(3, result.size)
        assertEquals(EmailConfirmationLinkFetchStatus.Ready::class, result[testEmailData1]!!::class)
        assertEquals(EmailConfirmationLinkFetchStatus.Pending, result[testEmailData2])
        assertEquals(EmailConfirmationLinkFetchStatus.Error::class, result[testEmailData3]!!::class)
    }

    @Test
    fun whenGetEmailConfirmationLinkStatusWithLargeListThenBatchRequests() = runTest {
        // Given - Create 150 email data items to test batching (batch size is 100)
        val emailDataList = (1..150).map {
            EmailData(email = "test$it@example.com", attemptId = "attempt-$it")
        }

        val mockResponse1 = PirGetEmailConfirmationLinkResponse(
            items = (1..100).map {
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = "test$it@example.com",
                    attemptId = "attempt-$it",
                    status = "pending",
                    emailAddressCreatedAt = 1000L,
                )
            },
        )

        val mockResponse2 = PirGetEmailConfirmationLinkResponse(
            items = (101..150).map {
                PirGetEmailConfirmationLinkResponse.ResponseItem(
                    email = "test$it@example.com",
                    attemptId = "attempt-$it",
                    status = "pending",
                    emailAddressCreatedAt = 1000L,
                )
            },
        )

        whenever(mockDbpService.getEmailConfirmationLinkStatus(any()))
            .thenReturn(mockResponse1)
            .thenReturn(mockResponse2)

        // When
        val result = testee.getEmailConfirmationLinkStatus(emailDataList)

        // Then
        assertEquals(150, result.size)
        verify(mockDbpService).getEmailConfirmationLinkStatus(
            PirEmailConfirmationDataRequest(
                items = (1..100).map {
                    PirEmailConfirmationDataRequest.RequestEmailData("test$it@example.com", "attempt-$it")
                },
            ),
        )
        verify(mockDbpService).getEmailConfirmationLinkStatus(
            PirEmailConfirmationDataRequest(
                items = (101..150).map {
                    PirEmailConfirmationDataRequest.RequestEmailData("test$it@example.com", "attempt-$it")
                },
            ),
        )
    }

    @Test
    fun whenDeleteEmailDataWithSingleBatchThenCallServiceOnce() = runTest {
        // Given
        val emailDataList = listOf(testEmailData1, testEmailData2)

        // When
        testee.deleteEmailData(emailDataList)

        // Then
        verify(mockDbpService).deleteEmailData(
            PirEmailConfirmationDataRequest(
                items = listOf(
                    PirEmailConfirmationDataRequest.RequestEmailData(testEmailData1.email, testEmailData1.attemptId),
                    PirEmailConfirmationDataRequest.RequestEmailData(testEmailData2.email, testEmailData2.attemptId),
                ),
            ),
        )
    }

    @Test
    fun whenDeleteEmailDataWithLargeListThenBatchRequests() = runTest {
        // Given - Create 150 email data items to test batching (batch size is 100)
        val emailDataList = (1..150).map {
            EmailData(email = "test$it@example.com", attemptId = "attempt-$it")
        }

        // When
        testee.deleteEmailData(emailDataList)

        // Then
        verify(mockDbpService).deleteEmailData(
            PirEmailConfirmationDataRequest(
                items = (1..100).map {
                    PirEmailConfirmationDataRequest.RequestEmailData("test$it@example.com", "attempt-$it")
                },
            ),
        )
        verify(mockDbpService).deleteEmailData(
            PirEmailConfirmationDataRequest(
                items = (101..150).map {
                    PirEmailConfirmationDataRequest.RequestEmailData("test$it@example.com", "attempt-$it")
                },
            ),
        )
    }

    @Test
    fun whenDeleteEmailDataWithEmptyListThenNoServiceCall() = runTest {
        // Given
        val emailDataList = emptyList<EmailData>()

        // When
        testee.deleteEmailData(emailDataList)

        verifyNoInteractions(mockDbpService)
    }
}
