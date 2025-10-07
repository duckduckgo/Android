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
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages.SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.verifyResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebProfileStateHolder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirWebSetNameAtIndexInCurrentUserProfileMessageHandlerTest {

    private lateinit var testee: PirWebSetNameAtIndexInCurrentUserProfileMessageHandler

    private val mockPirWebProfileStateHolder: PirWebProfileStateHolder = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirWebSetNameAtIndexInCurrentUserProfileMessageHandler(
            pirWebProfileStateHolder = mockPirWebProfileStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE, testee.message)
    }

    @Test
    fun whenProcessWithValidNameAndIndexThenSetsNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "John", "middle": "Michael", "last": "Doe"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(
            mockPirWebProfileStateHolder.setNameAtIndex(
                0,
                "John",
                "Michael",
                "Doe",
            ),
        ).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).setNameAtIndex(0, "John", "Michael", "Doe")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidNameWithoutMiddleNameThenSetsNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 1, "name": {"first": "Jane", "last": "Smith"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.setNameAtIndex(1, "Jane", "", "Smith")).thenReturn(
            true,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).setNameAtIndex(1, "Jane", "", "Smith")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidNameWithEmptyMiddleNameThenSetsNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "Bob", "middle": "", "last": "Johnson"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(
            mockPirWebProfileStateHolder.setNameAtIndex(
                0,
                "Bob",
                "",
                "Johnson",
            ),
        ).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).setNameAtIndex(0, "Bob", "", "Johnson")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidNameWithWhitespaceThenTrimsAndSetsName() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 2, "name": {"first": "  Alice  ", "middle": "  Marie  ", "last": "  Brown  "}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(
            mockPirWebProfileStateHolder.setNameAtIndex(
                2,
                "Alice",
                "Marie",
                "Brown",
            ),
        ).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).setNameAtIndex(2, "Alice", "Marie", "Brown")
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"name": {"first": "Charlie", "last": "Wilson"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(
            mockPirWebProfileStateHolder.setNameAtIndex(
                0,
                "Charlie",
                "",
                "Wilson",
            ),
        ).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankFirstNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "", "last": "Doe"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceFirstNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "   ", "last": "Doe"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBlankLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "John", "last": ""}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithWhitespaceLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "John", "last": "   "}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithBothBlankFirstAndLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "", "last": ""}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingFirstNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"last": "Doe"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingLastNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "John"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingNameObjectThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithDuplicateNameThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0, "name": {"first": "John", "middle": "Michael", "last": "Doe"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(
            mockPirWebProfileStateHolder.setNameAtIndex(
                0,
                "John",
                "Michael",
                "Doe",
            ),
        ).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).setNameAtIndex(0, "John", "Michael", "Doe")
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 5, "name": {"first": "John", "last": "Doe"}}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.setNameAtIndex(5, "John", "", "Doe")).thenReturn(
            false,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).setNameAtIndex(5, "John", "", "Doe")
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """invalid json""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithEmptyJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = null,
            method = SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).setNameAtIndex(
            any(),
            any(),
            any(),
            any(),
        )
        verifyResponse(jsMessage, false, mockJsMessaging)
    }
}
