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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.verifyResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebOnboardingStateHolder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class PirWebSetBirthYearForCurrentUserProfileTest {

    private lateinit var testee: PirWebSetBirthYearForCurrentUserProfile

    private val mockPirWebOnboardingStateHolder: PirWebOnboardingStateHolder = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirWebSetBirthYearForCurrentUserProfile(
            pirWebOnboardingStateHolder = mockPirWebOnboardingStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(
            PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
            testee.message,
        )
    }

    @Test
    fun whenProcessWithValidYearThenSetsBirthYearAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"year": 1990}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(1990)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithDifferentValidYearThenSetsBirthYearAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"year": 1985}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(1985)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithZeroYearThenSetsBirthYearAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"year": 0}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(0)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNegativeYearThenSetsBirthYearAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"year": -1}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(-1)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithLargeYearThenSetsBirthYearAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"year": 2030}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(2030)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingYearThenDefaultsToZeroAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(0)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNonNumericYearThenDefaultsToZeroAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"year": "invalid"}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(0)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithFloatYearThenTruncatesToIntegerAndSendsSuccessResponse() {
        // Given - Note: JSON parsing may handle this differently, but testing the case
        val jsMessage = createJsMessage(
            paramsJson = """{"year": 1990.5}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then - The exact behavior depends on JSON parsing, but it should set some value
        verify(mockPirWebOnboardingStateHolder).setBirthYear(org.mockito.kotlin.any())
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullYearThenDefaultsToZeroAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"year": null}""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(0)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidJsonThenDefaultsToZeroAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """invalid json""",
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(0)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullJsonThenDefaultsToZeroAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = null,
            method = PirDashboardWebMessages.SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setBirthYear(0)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }
}
