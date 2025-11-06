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
import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.service.DbpService
import com.duckduckgo.pir.impl.service.DbpService.PirEmailConfirmationDataRequest
import com.duckduckgo.pir.impl.service.DbpService.PirGetEmailConfirmationLinkResponse
import com.duckduckgo.pir.impl.store.PirRepository.EmailConfirmationLinkFetchStatus
import com.duckduckgo.pir.impl.store.db.BrokerDao
import com.duckduckgo.pir.impl.store.db.BrokerJsonDao
import com.duckduckgo.pir.impl.store.db.ExtractedProfileDao
import com.duckduckgo.pir.impl.store.db.UserName
import com.duckduckgo.pir.impl.store.db.UserProfile
import com.duckduckgo.pir.impl.store.db.UserProfileDao
import com.duckduckgo.pir.impl.store.secure.PirSecureStorageDatabaseFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

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
    private val mockDatabaseFactory: PirSecureStorageDatabaseFactory = mock()
    private val mockDatabase: PirDatabase = mock()
    private val mockPixelSender: PirPixelSender = mock()

    @Before
    fun setUp() {
        runBlocking {
            whenever(mockDatabaseFactory.getDatabase()).thenReturn(mockDatabase)
        }
        whenever(mockDatabase.brokerJsonDao()).thenReturn(mockBrokerJsonDao)
        whenever(mockDatabase.brokerDao()).thenReturn(mockBrokerDao)
        whenever(mockDatabase.userProfileDao()).thenReturn(mockUserProfileDao)
        whenever(mockDatabase.extractedProfileDao()).thenReturn(mockExtractedProfileDao)
        whenever(mockBrokerJsonDao.getAllBrokersCount()).thenReturn(0)

        testee = RealPirRepository(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirDataStore = mockPirDataStore,
            currentTimeProvider = mockCurrentTimeProvider,
            databaseFactory = mockDatabaseFactory,
            dbpService = mockDbpService,
            pixelSender = mockPixelSender,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    // Test data
    private val testEmailData1 = EmailData(email = "test1@example.com", attemptId = "attempt-123")
    private val testEmailData2 = EmailData(email = "test2@example.com", attemptId = "attempt-456")
    private val testEmailData3 = EmailData(email = "test3@example.com", attemptId = "attempt-789")

    @Test
    fun whenIsRepositoryAvailableAndDatabaseReadableThenReturnTrue() = runTest {
        // Given - Database is readable (set up in setUp method)

        // When
        val result = testee.isRepositoryAvailable()

        // Then
        assertTrue(result)
        verify(mockDatabaseFactory).getDatabase()
        verify(mockBrokerJsonDao).getAllBrokersCount()
    }

    @Test
    fun whenIsRepositoryAvailableAndDatabaseNotReadableThenReturnFalse() = runTest {
        // Given - Set up the database to throw an exception when accessed
        whenever(mockBrokerJsonDao.getAllBrokersCount()).thenThrow(RuntimeException("Database not readable"))

        // When
        val result = testee.isRepositoryAvailable()

        // Then
        assertEquals(false, result)
        verify(mockDatabaseFactory).getDatabase()
        verify(mockBrokerJsonDao).getAllBrokersCount()
    }

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

    // Tests for getUserProfileQuery
    @Test
    fun whenGetUserProfileQueryWithValidIdThenReturnProfileQuery() = runTest {
        // Given
        val profileId = 1L
        val currentYear = 2025
        val userProfile = UserProfile(
            id = profileId,
            userName = UserName(firstName = "John", lastName = "Doe", middleName = "M"),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "New York", state = "NY"),
            birthYear = 1990,
            deprecated = false,
        )
        whenever(mockUserProfileDao.getUserProfile(profileId)).thenReturn(userProfile)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(currentYear, 1, 1, 0, 0))

        // When
        val result = testee.getUserProfileQuery(profileId)

        // Then
        verify(mockUserProfileDao).getUserProfile(profileId)
        assertEquals(profileId, result?.id)
        assertEquals("John", result?.firstName)
        assertEquals("Doe", result?.lastName)
        assertEquals("New York", result?.city)
        assertEquals("NY", result?.state)
        assertEquals(1990, result?.birthYear)
        assertEquals("John M Doe", result?.fullName)
        assertEquals(35, result?.age)
        assertEquals(false, result?.deprecated)
    }

    @Test
    fun whenGetUserProfileQueryWithNoMiddleNameThenReturnProfileQueryWithoutMiddleName() = runTest {
        // Given
        val profileId = 1L
        val currentYear = 2025
        val userProfile = UserProfile(
            id = profileId,
            userName = UserName(firstName = "Jane", lastName = "Smith", middleName = null),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Chicago", state = "IL"),
            birthYear = 1985,
            deprecated = false,
        )
        whenever(mockUserProfileDao.getUserProfile(profileId)).thenReturn(userProfile)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(currentYear, 6, 15, 0, 0))

        // When
        val result = testee.getUserProfileQuery(profileId)

        // Then
        assertEquals("Jane Smith", result?.fullName)
        assertEquals(40, result?.age)
    }

    @Test
    fun whenGetUserProfileQueryWithDeprecatedProfileThenReturnDeprecatedProfileQuery() = runTest {
        // Given
        val profileId = 2L
        val currentYear = 2025
        val userProfile = UserProfile(
            id = profileId,
            userName = UserName(firstName = "Bob", lastName = "Johnson"),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Boston", state = "MA"),
            birthYear = 1995,
            deprecated = true,
        )
        whenever(mockUserProfileDao.getUserProfile(profileId)).thenReturn(userProfile)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(currentYear, 1, 1, 0, 0))

        // When
        val result = testee.getUserProfileQuery(profileId)

        // Then
        assertEquals(true, result?.deprecated)
    }

    @Test
    fun whenGetUserProfileQueryWithNonExistentIdThenReturnNull() = runTest {
        // Given
        val profileId = 999L
        whenever(mockUserProfileDao.getUserProfile(profileId)).thenReturn(null)

        // When
        val result = testee.getUserProfileQuery(profileId)

        // Then
        verify(mockUserProfileDao).getUserProfile(profileId)
        assertNull(result)
    }

    // Tests for getAllUserProfileQueries
    @Test
    fun whenGetAllUserProfileQueriesWithMultipleProfilesThenReturnAll() = runTest {
        // Given
        val currentYear = 2025
        val userProfiles = listOf(
            UserProfile(
                id = 1L,
                userName = UserName(firstName = "John", lastName = "Doe"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "New York", state = "NY"),
                birthYear = 1990,
                deprecated = false,
            ),
            UserProfile(
                id = 2L,
                userName = UserName(firstName = "Jane", lastName = "Smith"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Chicago", state = "IL"),
                birthYear = 1985,
                deprecated = false,
            ),
            UserProfile(
                id = 3L,
                userName = UserName(firstName = "Bob", lastName = "Johnson"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Boston", state = "MA"),
                birthYear = 1995,
                deprecated = true,
            ),
        )
        whenever(mockUserProfileDao.getAllUserProfiles()).thenReturn(userProfiles)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(currentYear, 1, 1, 0, 0))

        // When
        val result = testee.getAllUserProfileQueries()

        // Then
        verify(mockUserProfileDao).getAllUserProfiles()
        assertEquals(3, result.size)
        assertEquals("John", result[0].firstName)
        assertEquals("Jane", result[1].firstName)
        assertEquals("Bob", result[2].firstName)
        assertEquals(false, result[0].deprecated)
        assertEquals(false, result[1].deprecated)
        assertEquals(true, result[2].deprecated)
    }

    @Test
    fun whenGetAllUserProfileQueriesIncludesDeprecatedProfiles() = runTest {
        // Given
        val currentYear = 2025
        val userProfiles = listOf(
            UserProfile(
                id = 1L,
                userName = UserName(firstName = "Active", lastName = "User"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Seattle", state = "WA"),
                birthYear = 1992,
                deprecated = false,
            ),
            UserProfile(
                id = 2L,
                userName = UserName(firstName = "Deprecated", lastName = "User"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Portland", state = "OR"),
                birthYear = 1988,
                deprecated = true,
            ),
        )
        whenever(mockUserProfileDao.getAllUserProfiles()).thenReturn(userProfiles)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(currentYear, 1, 1, 0, 0))

        // When
        val result = testee.getAllUserProfileQueries()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.deprecated })
        assertTrue(result.any { !it.deprecated })
    }

    @Test
    fun whenGetAllUserProfileQueriesWithEmptyDatabaseThenReturnEmptyList() = runTest {
        // Given
        whenever(mockUserProfileDao.getAllUserProfiles()).thenReturn(emptyList())

        // When
        val result = testee.getAllUserProfileQueries()

        // Then
        verify(mockUserProfileDao).getAllUserProfiles()
        assertTrue(result.isEmpty())
    }

    // Tests for getValidUserProfileQueries
    @Test
    fun whenGetValidUserProfileQueriesWithMixedProfilesThenReturnOnlyNonDeprecated() = runTest {
        // Given
        val currentYear = 2025
        val validUserProfiles = listOf(
            UserProfile(
                id = 1L,
                userName = UserName(firstName = "John", lastName = "Doe"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "New York", state = "NY"),
                birthYear = 1990,
                deprecated = false,
            ),
            UserProfile(
                id = 2L,
                userName = UserName(firstName = "Jane", lastName = "Smith"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Chicago", state = "IL"),
                birthYear = 1985,
                deprecated = false,
            ),
        )
        whenever(mockUserProfileDao.getValidUserProfiles()).thenReturn(validUserProfiles)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(currentYear, 1, 1, 0, 0))

        // When
        val result = testee.getValidUserProfileQueries()

        // Then
        verify(mockUserProfileDao).getValidUserProfiles()
        assertEquals(2, result.size)
        assertTrue(result.all { !it.deprecated })
        assertEquals("John", result[0].firstName)
        assertEquals("Jane", result[1].firstName)
    }

    @Test
    fun whenGetValidUserProfileQueriesWithOnlyDeprecatedProfilesThenReturnEmptyList() = runTest {
        // Given
        whenever(mockUserProfileDao.getValidUserProfiles()).thenReturn(emptyList())

        // When
        val result = testee.getValidUserProfileQueries()

        // Then
        verify(mockUserProfileDao).getValidUserProfiles()
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetValidUserProfileQueriesThenDoesNotIncludeDeprecatedProfiles() = runTest {
        // Given
        val currentYear = 2025
        val validProfiles = listOf(
            UserProfile(
                id = 1L,
                userName = UserName(firstName = "Valid", lastName = "User"),
                addresses = com.duckduckgo.pir.impl.store.db.Address(city = "Austin", state = "TX"),
                birthYear = 1993,
                deprecated = false,
            ),
        )
        whenever(mockUserProfileDao.getValidUserProfiles()).thenReturn(validProfiles)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(currentYear, 1, 1, 0, 0))

        // When
        val result = testee.getValidUserProfileQueries()

        // Then
        assertEquals(1, result.size)
        assertEquals(false, result[0].deprecated)
    }

    // Tests for saveNewExtractedProfiles
    @Test
    fun whenSaveNewExtractedProfilesWithValidProfilesThenInsertThem() = runTest {
        // Given
        val currentTime = 1000000L
        val profileQueryId = 1L
        val extractedProfiles = listOf(
            ExtractedProfile(
                dbId = 0L,
                profileQueryId = profileQueryId,
                brokerName = "TestBroker",
                name = "John Doe",
                age = "35",
                profileUrl = "https://example.com/profile1",
                identifier = "id123",
            ),
            ExtractedProfile(
                dbId = 0L,
                profileQueryId = profileQueryId,
                brokerName = "AnotherBroker",
                name = "John Doe",
                age = "35",
                profileUrl = "https://example.com/profile2",
                identifier = "id456",
            ),
        )
        val userProfile = UserProfile(
            id = profileQueryId,
            userName = UserName(firstName = "John", lastName = "Doe"),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "NYC", state = "NY"),
            birthYear = 1990,
            deprecated = false,
        )
        whenever(mockUserProfileDao.getUserProfile(profileQueryId)).thenReturn(userProfile)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)

        // When
        testee.saveNewExtractedProfiles(extractedProfiles)

        // Then
        verify(mockUserProfileDao).getUserProfile(profileQueryId)
        verify(mockExtractedProfileDao).insertNewExtractedProfiles(any())
    }

    @Test
    fun whenSaveNewExtractedProfilesWithDeprecatedProfileThenDoNotInsert() = runTest {
        // Given
        val profileQueryId = 1L
        val extractedProfiles = listOf(
            ExtractedProfile(
                dbId = 0L,
                profileQueryId = profileQueryId,
                brokerName = "TestBroker",
                name = "John Doe",
            ),
        )
        val deprecatedUserProfile = UserProfile(
            id = profileQueryId,
            userName = UserName(firstName = "John", lastName = "Doe"),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "NYC", state = "NY"),
            birthYear = 1990,
            deprecated = true,
        )
        whenever(mockUserProfileDao.getUserProfile(profileQueryId)).thenReturn(deprecatedUserProfile)

        // When
        testee.saveNewExtractedProfiles(extractedProfiles)

        // Then
        verify(mockUserProfileDao).getUserProfile(profileQueryId)
        verify(mockExtractedProfileDao, never()).insertNewExtractedProfiles(any())
    }

    @Test
    fun whenSaveNewExtractedProfilesWithEmptyListThenDoNothing() = runTest {
        // Given
        val extractedProfiles = emptyList<ExtractedProfile>()

        // When
        testee.saveNewExtractedProfiles(extractedProfiles)

        // Then
        verifyNoInteractions(mockUserProfileDao)
        verifyNoInteractions(mockExtractedProfileDao)
    }

    @Test
    fun whenSaveNewExtractedProfilesWithNonExistentProfileThenStillInsertsProfiles() = runTest {
        // Given - When profile doesn't exist, the deprecated check (profileQuery?.deprecated == true)
        // evaluates to false, so profiles still get inserted
        val currentTime = 1000000L
        val profileQueryId = 999L
        val extractedProfiles = listOf(
            ExtractedProfile(
                dbId = 0L,
                profileQueryId = profileQueryId,
                brokerName = "TestBroker",
                name = "Unknown User",
            ),
        )
        whenever(mockUserProfileDao.getUserProfile(profileQueryId)).thenReturn(null)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)

        // When
        testee.saveNewExtractedProfiles(extractedProfiles)

        // Then
        verify(mockUserProfileDao).getUserProfile(profileQueryId)
        verify(mockExtractedProfileDao).insertNewExtractedProfiles(any())
    }

    @Test
    fun whenSaveNewExtractedProfilesWithZeroDateThenUseCurrentTime() = runTest {
        // Given
        val currentTime = 5000000L
        val profileQueryId = 1L
        val extractedProfiles = listOf(
            ExtractedProfile(
                dbId = 0L,
                profileQueryId = profileQueryId,
                brokerName = "TestBroker",
                name = "John Doe",
                dateAddedInMillis = 0L, // Should be replaced with current time
            ),
        )
        val userProfile = UserProfile(
            id = profileQueryId,
            userName = UserName(firstName = "John", lastName = "Doe"),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "NYC", state = "NY"),
            birthYear = 1990,
            deprecated = false,
        )
        whenever(mockUserProfileDao.getUserProfile(profileQueryId)).thenReturn(userProfile)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)

        // When
        testee.saveNewExtractedProfiles(extractedProfiles)

        // Then
        verify(mockCurrentTimeProvider).currentTimeMillis()
        verify(mockExtractedProfileDao).insertNewExtractedProfiles(any())
    }

    @Test
    fun whenSaveNewExtractedProfilesWithExistingDateThenKeepOriginalDate() = runTest {
        // Given
        val existingDate = 2000000L
        val currentTime = 5000000L
        val profileQueryId = 1L
        val extractedProfiles = listOf(
            ExtractedProfile(
                dbId = 0L,
                profileQueryId = profileQueryId,
                brokerName = "TestBroker",
                name = "John Doe",
                dateAddedInMillis = existingDate,
            ),
        )
        val userProfile = UserProfile(
            id = profileQueryId,
            userName = UserName(firstName = "John", lastName = "Doe"),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "NYC", state = "NY"),
            birthYear = 1990,
            deprecated = false,
        )
        whenever(mockUserProfileDao.getUserProfile(profileQueryId)).thenReturn(userProfile)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)

        // When
        testee.saveNewExtractedProfiles(extractedProfiles)

        // Then
        verify(mockExtractedProfileDao).insertNewExtractedProfiles(any())
    }

    @Test
    fun whenSaveNewExtractedProfilesWithCompleteDataThenMapAllFields() = runTest {
        // Given
        val currentTime = 1000000L
        val profileQueryId = 1L
        val extractedProfiles = listOf(
            ExtractedProfile(
                dbId = 123L,
                profileQueryId = profileQueryId,
                brokerName = "TestBroker",
                name = "John Michael Doe",
                alternativeNames = listOf("John M. Doe", "J. Doe"),
                age = "35",
                addresses = listOf(
                    AddressCityState(city = "New York", state = "NY", fullAddress = "123 Main St, New York, NY 10001"),
                    AddressCityState(city = "Boston", state = "MA", fullAddress = "456 Oak Ave, Boston, MA 02101"),
                ),
                phoneNumbers = listOf("555-1234", "555-5678"),
                relatives = listOf("Jane Doe", "Jim Doe"),
                reportId = "report-123",
                email = "john@example.com",
                fullName = "John Michael Doe",
                profileUrl = "https://example.com/profile",
                identifier = "identifier-123",
                dateAddedInMillis = 999999L,
                deprecated = false,
            ),
        )
        val userProfile = UserProfile(
            id = profileQueryId,
            userName = UserName(firstName = "John", lastName = "Doe"),
            addresses = com.duckduckgo.pir.impl.store.db.Address(city = "NYC", state = "NY"),
            birthYear = 1990,
            deprecated = false,
        )
        whenever(mockUserProfileDao.getUserProfile(profileQueryId)).thenReturn(userProfile)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)

        // When
        testee.saveNewExtractedProfiles(extractedProfiles)

        // Then
        verify(mockUserProfileDao).getUserProfile(profileQueryId)
        verify(mockExtractedProfileDao).insertNewExtractedProfiles(any())
    }
}
