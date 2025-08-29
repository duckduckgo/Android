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
class PirWebSetAddressAtIndexInCurrentUserProfileMessageHandlerTest {

    private lateinit var testee: PirWebSetAddressAtIndexInCurrentUserProfileMessageHandler

    private val mockPirWebOnboardingStateHolder: PirWebOnboardingStateHolder = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirWebSetAddressAtIndexInCurrentUserProfileMessageHandler(
            pirWebOnboardingStateHolder = mockPirWebOnboardingStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE, testee.message)
    }

    @Test
    fun whenProcessWithValidAddressAndIndexThenSetsAddressAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "New York", "state": "NY"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.setAddressAtIndex(0, "New York", "NY")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setAddressAtIndex(0, "New York", "NY")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidAddressWithWhitespaceThenTrimsAndSetsAddress() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 1, "address": {"city": "  Los Angeles  ", "state": "  CA  "}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.setAddressAtIndex(1, "Los Angeles", "CA")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setAddressAtIndex(1, "Los Angeles", "CA")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"address": {"city": "Chicago", "state": "IL"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.setAddressAtIndex(0, "Chicago", "IL")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankCityThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "", "state": "NY"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceCityThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "   ", "state": "NY"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "New York", "state": ""}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "New York", "state": "   "}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBothBlankCityAndStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "", "state": ""}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingCityThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"state": "NY"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingStateThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "New York"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingAddressObjectThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithDuplicateAddressThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "address": {"city": "New York", "state": "NY"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.setAddressAtIndex(0, "New York", "NY")).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setAddressAtIndex(0, "New York", "NY")
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 5, "address": {"city": "New York", "state": "NY"}}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.setAddressAtIndex(5, "New York", "NY")).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).setAddressAtIndex(5, "New York", "NY")
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """invalid json""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithEmptyJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = null,
            method = PirDashboardWebMessages.SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).setAddressAtIndex(any(), any(), any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }
}
