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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirWebAddAddressToCurrentUserProfileMessageHandlerTest {

    private lateinit var testee: PirWebAddAddressToCurrentUserProfileMessageHandler

    private val mockPirWebOnboardingStateHolder: PirWebOnboardingStateHolder = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirWebAddAddressToCurrentUserProfileMessageHandler(
            pirWebOnboardingStateHolder = mockPirWebOnboardingStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE, testee.message)
    }

    @Test
    fun whenProcessWithValidAddressThenAddsAddressAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "New York", "state": "NY"}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.addAddress("New York", "NY")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addAddress("New York", "NY")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidAddressWithWhitespaceThenTrimsAndAddsAddress() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "  Los Angeles  ", "state": "  CA  "}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.addAddress("Los Angeles", "CA")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addAddress("Los Angeles", "CA")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankCityThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "", "state": "NY"}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceCityThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "   ", "state": "NY"}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "New York", "state": ""}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "New York", "state": "   "}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBothBlankCityAndStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "", "state": ""}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingCityThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"state": "NY"}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "New York"}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithDuplicateAddressThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"city": "New York", "state": "NY"}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.addAddress("New York", "NY")).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addAddress("New York", "NY")
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """invalid json""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithEmptyJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = null,
            method = PirDashboardWebMessages.ADD_ADDRESS_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addAddress(any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }
}
