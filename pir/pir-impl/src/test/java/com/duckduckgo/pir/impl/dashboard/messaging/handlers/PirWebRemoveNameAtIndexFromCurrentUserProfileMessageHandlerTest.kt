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
class PirWebRemoveNameAtIndexFromCurrentUserProfileMessageHandlerTest {

    private lateinit var testee: PirWebRemoveNameAtIndexFromCurrentUserProfileMessageHandler

    private val mockPirWebProfileStateHolder: PirWebProfileStateHolder = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirWebRemoveNameAtIndexFromCurrentUserProfileMessageHandler(
            pirWebProfileStateHolder = mockPirWebProfileStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE, testee.message)
    }

    @Test
    fun whenProcessWithValidIndexThenRemovesNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.removeNameAtIndex(0)).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).removeNameAtIndex(0)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithValidPositiveIndexThenRemovesNameAndSendsSuccessResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 2}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.removeNameAtIndex(2)).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).removeNameAtIndex(2)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 5}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.removeNameAtIndex(5)).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).removeNameAtIndex(5)
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNegativeIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": -1}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.removeNameAtIndex(-1)).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).removeNameAtIndex(-1)
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithZeroIndexButRemovalFailsThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 0}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.removeNameAtIndex(0)).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).removeNameAtIndex(0)
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).removeNameAtIndex(any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNonNumericIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": "invalid"}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).removeNameAtIndex(any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithFloatIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 1.5}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).removeNameAtIndex(any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullIndexThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": null}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).removeNameAtIndex(any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithInvalidJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """invalid json""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).removeNameAtIndex(any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithEmptyJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).removeNameAtIndex(any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullJsonThenSendsErrorResponse() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = null,
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder, org.mockito.kotlin.never()).removeNameAtIndex(any())
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithLargeIndexThenCallsStateHolderAndHandlesResult() {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{"index": 999}""",
            method = PirDashboardWebMessages.REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE,
        )
        whenever(mockPirWebProfileStateHolder.removeNameAtIndex(999)).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).removeNameAtIndex(999)
        verifyResponse(jsMessage, false, mockJsMessaging)
    }
}
