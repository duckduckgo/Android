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
class PirWebAddNameToCurrentUserProfileMessageHandlerTest {

    private lateinit var testee: PirWebAddNameToCurrentUserProfileMessageHandler

    private val mockPirWebOnboardingStateHolder: PirWebOnboardingStateHolder = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirWebAddNameToCurrentUserProfileMessageHandler(
            pirWebOnboardingStateHolder = mockPirWebOnboardingStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE, testee.message)
    }

    @Test
    fun whenProcessWithValidNameWithMiddleNameThenAddsNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "John", "middle": "Michael", "last": "Doe"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.addName("John", "Michael", "Doe")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addName("John", "Michael", "Doe")
        PirMessageHandlerUtils.verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidNameWithoutMiddleNameThenAddsNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "Jane", "last": "Smith"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.addName("Jane", "", "Smith")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addName("Jane", "", "Smith")
        PirMessageHandlerUtils.verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidNameWithEmptyMiddleNameThenAddsNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "Bob", "middle": "", "last": "Johnson"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebOnboardingStateHolder.addName("Bob", "", "Johnson")).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addName("Bob", "", "Johnson")
        PirMessageHandlerUtils.verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidNameWithWhitespaceThenTrimsAndAddsName() {
        // Given
        val jsMessage =
            createJsMessage(
                paramsJson = """{"first": "  Alice  ", "middle": "  Marie  ", "last": "  Brown  "}""",
                method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
            )
        whenever(
            mockPirWebOnboardingStateHolder.addName(
                "Alice",
                "Marie",
                "Brown",
            ),
        ).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addName("Alice", "Marie", "Brown")
        PirMessageHandlerUtils.verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankFirstNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "", "last": "Doe"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceFirstNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "   ", "last": "Doe"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "John", "last": ""}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "John", "last": "   "}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBothBlankFirstAndLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "", "last": ""}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingFirstNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"last": "Doe"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "John"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithDuplicateNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"first": "John", "middle": "Michael", "last": "Doe"}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )
        whenever(
            mockPirWebOnboardingStateHolder.addName(
                "John",
                "Michael",
                "Doe",
            ),
        ).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder).addName("John", "Michael", "Doe")
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """invalid json""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithEmptyJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = null,
            method = PirDashboardWebMessages.ADD_NAME_TO_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebOnboardingStateHolder, org.mockito.kotlin.never()).addName(
            any(),
            any(),
            any(),
        )
        PirMessageHandlerUtils.verifyResponse(jsMessage, false, mockJsMessaging)
    }
}
