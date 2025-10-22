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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages.SAVE_PROFILE
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.verifyResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebProfileStateHolder
import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.scan.PirForegroundScanService
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class PirWebSaveProfileMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebSaveProfileMessageHandler

    private val mockPirWebProfileStateHolder: PirWebProfileStateHolder = mock()
    private val mockRepository: PirRepository = mock()
    private val mockContext: Context = mock()
    private val mockScanScheduler: PirScanScheduler = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val mockJobRecordUpdater: JobRecordUpdater = mock()
    private val testScope = TestScope()

    @Before
    fun setUp() {
        testee = PirWebSaveProfileMessageHandler(
            pirWebProfileStateHolder = mockPirWebProfileStateHolder,
            repository = mockRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            context = mockContext,
            scanScheduler = mockScanScheduler,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = testScope,
            jobRecordUpdater = mockJobRecordUpdater,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(SAVE_PROFILE, testee.message)
    }

    @Test
    fun whenProcessWithIncompleteProfileThenSendsErrorResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).isProfileComplete
        verify(mockRepository, never()).updateProfileQueries(any(), any(), any())
        verify(mockContext, never()).startForegroundService(any())
        verify(mockScanScheduler, never()).scheduleScans()
        verify(mockPirWebProfileStateHolder, never()).clear()
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithCompleteProfileButSaveFailsThenSendsErrorResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueries = listOf(createProfileQuery())

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(
            profileQueries,
        )
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(emptyList())
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).isProfileComplete
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = profileQueries,
            profileQueriesToUpdate = emptyList(),
            profileQueryIdsToDelete = emptyList(),
        )
        verify(mockContext, never()).startForegroundService(any())
        verify(mockScanScheduler, never()).scheduleScans()
        verify(mockPirWebProfileStateHolder, never()).clear()
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithCompleteProfileAndSaveSucceedsThenSendsSuccessResponseAndStartsScan() =
        runTest {
            // Given
            val jsMessage = createJsMessage("""""", SAVE_PROFILE)
            val currentYear = 2025
            val currentDateTime = LocalDateTime.of(currentYear, 6, 15, 10, 30)
            val profileQueries = listOf(createProfileQuery())

            whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
            whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
            whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(
                profileQueries,
            )
            whenever(mockRepository.getValidUserProfileQueries()).thenReturn(emptyList())
            whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())
            whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

            // When
            testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

            // Then
            verify(mockPirWebProfileStateHolder).isProfileComplete
            verify(mockRepository).updateProfileQueries(
                profileQueriesToAdd = profileQueries,
                profileQueriesToUpdate = emptyList(),
                profileQueryIdsToDelete = emptyList(),
            )
            verifyResponse(jsMessage, true, mockJsMessaging)
            verifyStartAndScheduleInitialScan()
            verify(mockPirWebProfileStateHolder).clear()
        }

    @Test
    fun whenProcessWithMultipleProfileQueriesThenSavesAllQueries() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 12, 31, 23, 59)
        val profileQuery1 = createProfileQuery(firstName = "John", lastName = "Doe")
        val profileQuery2 = createProfileQuery(firstName = "Jane", lastName = "Smith")
        val profileQueries = listOf(profileQuery1, profileQuery2)

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(
            profileQueries,
        )
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(emptyList())
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).isProfileComplete
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = profileQueries,
            profileQueriesToUpdate = emptyList(),
            profileQueryIdsToDelete = emptyList(),
        )
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebProfileStateHolder).clear()
    }

    @Test
    fun whenProcessWithDifferentCurrentYearsThenUsesCorrectYear() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2030 // Different year
        val currentDateTime = LocalDateTime.of(currentYear, 3, 15, 8, 45)
        val profileQueries = listOf(createProfileQuery())

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(
            profileQueries,
        )
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(emptyList())
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = profileQueries,
            profileQueriesToUpdate = emptyList(),
            profileQueryIdsToDelete = emptyList(),
        )
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebProfileStateHolder).clear()
    }

    @Test
    fun whenProcessWithNullCallbackThenStillProcessesCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueries = listOf(createProfileQuery())

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(
            profileQueries,
        )
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(emptyList())
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, null)

        // Then
        verify(mockPirWebProfileStateHolder).isProfileComplete
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = profileQueries,
            profileQueriesToUpdate = emptyList(),
            profileQueryIdsToDelete = emptyList(),
        )
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebProfileStateHolder).clear()
    }

    @Test
    fun whenProcessWithExistingProfileQueriesAndNoChangesThenNoUpdate() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val existingProfileQuery = createProfileQuery(id = 1)
        val newProfileQuery = createProfileQuery(id = 0) // Same content but id=0 as expected from state holder

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(listOf(newProfileQuery))
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(existingProfileQuery))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNewProfileQueriesThenAddsThemToExisting() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val existingProfileQuery = createProfileQuery(id = 1, firstName = "Existing", lastName = "User")
        val newProfileQuery1 = createProfileQuery(id = 0, firstName = "New", lastName = "User1")
        val newProfileQuery2 = createProfileQuery(id = 0, firstName = "New", lastName = "User2")

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(
            listOf(existingProfileQuery.copy(id = 0), newProfileQuery1, newProfileQuery2),
        )
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(existingProfileQuery))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = listOf(newProfileQuery1, newProfileQuery2),
            profileQueriesToUpdate = emptyList(),
            profileQueryIdsToDelete = emptyList(),
        )
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithProfileQueryToDeleteButHasExtractedProfilesThenDeprecatesInsteadOfDeleting() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueryToDeprecate = createProfileQuery(id = 1, firstName = "ToDeprecate", lastName = "User")
        val extractedProfile = ExtractedProfile(
            dbId = 1L,
            profileQueryId = 1L,
            brokerName = "TestBroker",
            name = "ToDeprecate User",
        )

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(emptyList()) // No new profiles
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQueryToDeprecate))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(listOf(extractedProfile))
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = emptyList(),
            profileQueriesToUpdate = listOf(profileQueryToDeprecate.copy(deprecated = true)),
            profileQueryIdsToDelete = emptyList(),
        )
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithProfileQueryToDeleteAndNoExtractedProfilesThenDeletesQuery() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueryToDelete = createProfileQuery(id = 1, firstName = "ToDelete", lastName = "User")

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(emptyList()) // No new profiles
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQueryToDelete))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList()) // No extracted profiles
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = emptyList(),
            profileQueriesToUpdate = emptyList(),
            profileQueryIdsToDelete = listOf(1L),
        )
        verifyResponse(jsMessage, true, mockJsMessaging)
        verify(mockJobRecordUpdater).removeAllJobRecordsForProfiles(listOf(1L))
    }

    @Test
    fun whenProcessWithDeprecatedProfileQueryThenRemovesJobRecordsExceptForBrokersWithExtractedProfiles() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueryToDeprecate = createProfileQuery(id = 2, firstName = "ToDeprecate", lastName = "User")
        val extractedProfile1 = ExtractedProfile(
            dbId = 1L,
            profileQueryId = 2L,
            brokerName = "Broker1",
            name = "ToDeprecate User",
        )
        val extractedProfile2 = ExtractedProfile(
            dbId = 2L,
            profileQueryId = 2L,
            brokerName = "Broker2",
            name = "ToDeprecate User",
        )

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(emptyList())
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQueryToDeprecate))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(listOf(extractedProfile1, extractedProfile2))
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = emptyList(),
            profileQueriesToUpdate = listOf(profileQueryToDeprecate.copy(deprecated = true)),
            profileQueryIdsToDelete = emptyList(),
        )
        verify(mockJobRecordUpdater).removeScanJobRecordsWithNoMatchesForProfiles(listOf(2L))
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMultipleProfileQueriesToDeleteThenRemovesAllJobRecords() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueryToDelete1 = createProfileQuery(id = 1, firstName = "Delete1", lastName = "User")
        val profileQueryToDelete2 = createProfileQuery(id = 2, firstName = "Delete2", lastName = "User")

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(emptyList())
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQueryToDelete1, profileQueryToDelete2))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = emptyList(),
            profileQueriesToUpdate = emptyList(),
            profileQueryIdsToDelete = listOf(1L, 2L),
        )
        verify(mockJobRecordUpdater).removeAllJobRecordsForProfiles(listOf(1L, 2L))
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMultipleDeprecatedProfileQueriesThenRemovesJobRecordsForEach() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQuery1 = createProfileQuery(id = 1, firstName = "Deprecated1", lastName = "User")
        val profileQuery2 = createProfileQuery(id = 2, firstName = "Deprecated2", lastName = "User")

        val extractedProfile1 = ExtractedProfile(
            dbId = 1L,
            profileQueryId = 1L,
            brokerName = "Broker1",
            name = "Deprecated1 User",
        )
        val extractedProfile2 = ExtractedProfile(
            dbId = 2L,
            profileQueryId = 2L,
            brokerName = "Broker2",
            name = "Deprecated2 User",
        )
        val extractedProfile3 = ExtractedProfile(
            dbId = 3L,
            profileQueryId = 2L,
            brokerName = "Broker3",
            name = "Deprecated2 User",
        )

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(emptyList())
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQuery1, profileQuery2))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(listOf(extractedProfile1, extractedProfile2, extractedProfile3))
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = emptyList(),
            profileQueriesToUpdate = listOf(
                profileQuery1.copy(deprecated = true),
                profileQuery2.copy(deprecated = true),
            ),
            profileQueryIdsToDelete = emptyList(),
        )
        verify(mockJobRecordUpdater).removeScanJobRecordsWithNoMatchesForProfiles(listOf(1L, 2L))
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMixOfDeletedAndDeprecatedProfileQueriesThenHandlesJobRecordsCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueryToDelete = createProfileQuery(id = 1, firstName = "Delete", lastName = "User")
        val profileQueryToDeprecate = createProfileQuery(id = 2, firstName = "Deprecate", lastName = "User")

        val extractedProfile = ExtractedProfile(
            dbId = 1L,
            profileQueryId = 2L,
            brokerName = "TestBroker",
            name = "Deprecate User",
        )

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(emptyList())
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQueryToDelete, profileQueryToDeprecate))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(listOf(extractedProfile))
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = emptyList(),
            profileQueriesToUpdate = listOf(profileQueryToDeprecate.copy(deprecated = true)),
            profileQueryIdsToDelete = listOf(1L),
        )
        verify(mockJobRecordUpdater).removeAllJobRecordsForProfiles(listOf(1L))
        verify(mockJobRecordUpdater).removeScanJobRecordsWithNoMatchesForProfiles(listOf(2L))
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithDeprecatedProfileQueryWithDuplicateBrokerNamesThenDeduplicatesBrokerList() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueryToDeprecate = createProfileQuery(id = 1, firstName = "Deprecate", lastName = "User")

        val extractedProfile1 = ExtractedProfile(
            dbId = 1L,
            profileQueryId = 1L,
            brokerName = "SameBroker",
            name = "Deprecate User",
        )
        val extractedProfile2 = ExtractedProfile(
            dbId = 2L,
            profileQueryId = 1L,
            brokerName = "SameBroker",
            name = "Deprecate User",
        )

        whenever(mockPirWebProfileStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebProfileStateHolder.toProfileQueries(currentYear)).thenReturn(emptyList())
        whenever(mockRepository.getValidUserProfileQueries()).thenReturn(listOf(profileQueryToDeprecate))
        whenever(mockRepository.getAllExtractedProfiles()).thenReturn(listOf(extractedProfile1, extractedProfile2))
        whenever(mockRepository.updateProfileQueries(any(), any(), any())).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockRepository).updateProfileQueries(
            profileQueriesToAdd = emptyList(),
            profileQueriesToUpdate = listOf(profileQueryToDeprecate.copy(deprecated = true)),
            profileQueryIdsToDelete = emptyList(),
        )
        verify(mockJobRecordUpdater).removeScanJobRecordsWithNoMatchesForProfiles(listOf(1L))
        verify(mockJobRecordUpdater, never()).removeAllJobRecordsForProfiles(any())
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    private fun createProfileQuery(
        id: Long = 1,
        firstName: String = "Test",
        lastName: String = "User",
    ): ProfileQuery {
        return ProfileQuery(
            id = id,
            firstName = firstName,
            middleName = null,
            lastName = lastName,
            city = "Test City",
            state = "TS",
            addresses = listOf(Address("Test City", "TS")),
            birthYear = 1990,
            fullName = "$firstName $lastName",
            age = 35,
            deprecated = false,
        )
    }

    private fun verifyStartAndScheduleInitialScan() {
        val intentCaptor = argumentCaptor<Intent>()
        verify(mockContext).startForegroundService(intentCaptor.capture())

        val capturedIntent = intentCaptor.firstValue
        assertEquals(PirForegroundScanService::class.java.name, capturedIntent.component?.className)

        verify(mockScanScheduler).scheduleScans()
    }
}
