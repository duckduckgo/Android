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
import com.duckduckgo.pir.impl.dashboard.state.PirWebOnboardingStateHolder
import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.scan.PirForegroundScanService
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.pir.impl.store.PirRepository
import java.time.LocalDateTime
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

@RunWith(AndroidJUnit4::class)
class PirWebSaveProfileMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebSaveProfileMessageHandler

    private val mockPirWebOnboardingStateHolder: PirWebOnboardingStateHolder = mock()
    private val mockRepository: PirRepository = mock()
    private val mockContext: Context = mock()
    private val mockScanScheduler: PirScanScheduler = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val testScope = TestScope()

    @Before
    fun setUp() {
        testee = PirWebSaveProfileMessageHandler(
            pirWebOnboardingStateHolder = mockPirWebOnboardingStateHolder,
            repository = mockRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            context = mockContext,
            scanScheduler = mockScanScheduler,
            currentTimeProvider = mockCurrentTimeProvider,
            appCoroutineScope = testScope,
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
        whenever(mockPirWebOnboardingStateHolder.isProfileComplete).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).isProfileComplete
        verify(mockRepository, never()).saveProfileQueries(any())
        verify(mockContext, never()).startForegroundService(any())
        verify(mockScanScheduler, never()).scheduleScans()
        verify(mockPirWebOnboardingStateHolder, never()).clear()
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithCompleteProfileButSaveFailsThenSendsErrorResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueries = listOf(createProfileQuery())

        whenever(mockPirWebOnboardingStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebOnboardingStateHolder.toProfileQueries(currentYear)).thenReturn(profileQueries)
        whenever(mockRepository.saveProfileQueries(profileQueries)).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).isProfileComplete
        verify(mockCurrentTimeProvider).localDateTimeNow()
        verify(mockPirWebOnboardingStateHolder).toProfileQueries(currentYear)
        verify(mockRepository).saveProfileQueries(profileQueries)
        verify(mockContext, never()).startForegroundService(any())
        verify(mockScanScheduler, never()).scheduleScans()
        verify(mockPirWebOnboardingStateHolder, never()).clear()
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithCompleteProfileAndSaveSucceedsThenSendsSuccessResponseAndStartsScan() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 6, 15, 10, 30)
        val profileQueries = listOf(createProfileQuery())

        whenever(mockPirWebOnboardingStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebOnboardingStateHolder.toProfileQueries(currentYear)).thenReturn(profileQueries)
        whenever(mockRepository.saveProfileQueries(profileQueries)).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).isProfileComplete
        verify(mockCurrentTimeProvider).localDateTimeNow()
        verify(mockPirWebOnboardingStateHolder).toProfileQueries(currentYear)
        verify(mockRepository).saveProfileQueries(profileQueries)
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebOnboardingStateHolder).clear()
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

        whenever(mockPirWebOnboardingStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebOnboardingStateHolder.toProfileQueries(currentYear)).thenReturn(profileQueries)
        whenever(mockRepository.saveProfileQueries(profileQueries)).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).isProfileComplete
        verify(mockCurrentTimeProvider).localDateTimeNow()
        verify(mockPirWebOnboardingStateHolder).toProfileQueries(currentYear)
        verify(mockRepository).saveProfileQueries(profileQueries)
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebOnboardingStateHolder).clear()
    }

    @Test
    fun whenProcessWithEmptyProfileQueriesThenStillProceedsWithSave() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 7, 4, 12, 0)
        val emptyProfileQueries = emptyList<ProfileQuery>()

        whenever(mockPirWebOnboardingStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebOnboardingStateHolder.toProfileQueries(currentYear)).thenReturn(emptyProfileQueries)
        whenever(mockRepository.saveProfileQueries(emptyProfileQueries)).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).isProfileComplete
        verify(mockCurrentTimeProvider).localDateTimeNow()
        verify(mockPirWebOnboardingStateHolder).toProfileQueries(currentYear)
        verify(mockRepository).saveProfileQueries(emptyProfileQueries)
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebOnboardingStateHolder).clear()
    }

    @Test
    fun whenProcessWithDifferentCurrentYearsThenUsesCorrectYear() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2030 // Different year
        val currentDateTime = LocalDateTime.of(currentYear, 3, 15, 8, 45)
        val profileQueries = listOf(createProfileQuery())

        whenever(mockPirWebOnboardingStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebOnboardingStateHolder.toProfileQueries(currentYear)).thenReturn(profileQueries)
        whenever(mockRepository.saveProfileQueries(profileQueries)).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockCurrentTimeProvider).localDateTimeNow()
        verify(mockPirWebOnboardingStateHolder).toProfileQueries(currentYear) // Should use 2030
        verify(mockRepository).saveProfileQueries(profileQueries)
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebOnboardingStateHolder).clear()
    }

    @Test
    fun whenProcessWithNullCallbackThenStillProcessesCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", SAVE_PROFILE)
        val currentYear = 2025
        val currentDateTime = LocalDateTime.of(currentYear, 1, 1, 0, 0)
        val profileQueries = listOf(createProfileQuery())

        whenever(mockPirWebOnboardingStateHolder.isProfileComplete).thenReturn(true)
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(currentDateTime)
        whenever(mockPirWebOnboardingStateHolder.toProfileQueries(currentYear)).thenReturn(profileQueries)
        whenever(mockRepository.saveProfileQueries(profileQueries)).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, null)

        // Then
        verify(mockPirWebOnboardingStateHolder).isProfileComplete
        verify(mockRepository).saveProfileQueries(profileQueries)
        verifyResponse(jsMessage, true, mockJsMessaging)
        verifyStartAndScheduleInitialScan()
        verify(mockPirWebOnboardingStateHolder).clear()
    }

    private fun createProfileQuery(
        firstName: String = "Test",
        lastName: String = "User",
    ): ProfileQuery {
        return ProfileQuery(
            id = 1,
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
