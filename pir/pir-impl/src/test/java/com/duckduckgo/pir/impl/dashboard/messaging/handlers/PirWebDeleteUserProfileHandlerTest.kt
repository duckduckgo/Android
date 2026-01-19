/*
 * Copyright (c) 2026 DuckDuckGo
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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.PirFeatureDataCleaner
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.verifyResponse
import com.duckduckgo.pir.impl.dashboard.state.PirWebProfileStateHolder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirWebDeleteUserProfileHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebDeleteUserProfileHandler

    private val mockWorkHandler: PirWorkHandler = mock()
    private val mockPirFeatureDataCleaner: PirFeatureDataCleaner = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val mockPirWebProfileStateHolder: PirWebProfileStateHolder = mock()

    @Before
    fun setUp() {
        testee = PirWebDeleteUserProfileHandler(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            workHandler = mockWorkHandler,
            pirFeatureDataCleaner = mockPirFeatureDataCleaner,
            pirWebProfileStateHolder = mockPirWebProfileStateHolder,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.DELETE_USER_PROFILE_DATA, testee.message)
    }

    @Test
    fun whenProcessSucceedsThenCancelsWorkRemovesUserDataAndSendsSuccessResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.DELETE_USER_PROFILE_DATA)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPirWebProfileStateHolder).clear()
        verify(mockWorkHandler).cancelWork()
        verify(mockPirFeatureDataCleaner).removeUserData()
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenCancelWorkThrowsExceptionThenSendsErrorResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.DELETE_USER_PROFILE_DATA)
        whenever(mockWorkHandler.cancelWork()).thenThrow(RuntimeException("Cancel work failed"))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenRemoveUserDataThrowsExceptionThenSendsErrorResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.DELETE_USER_PROFILE_DATA)
        whenever(mockPirFeatureDataCleaner.removeUserData()).thenThrow(RuntimeException("Remove user data failed"))

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verifyResponse(jsMessage, false, mockJsMessaging)
    }

    @Test
    fun whenProcessWithNullCallbackThenStillProcessesCorrectly() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.DELETE_USER_PROFILE_DATA)

        // When
        testee.process(jsMessage, mockJsMessaging, null)

        // Then
        verify(mockPirWebProfileStateHolder).clear()
        verify(mockWorkHandler).cancelWork()
        verify(mockPirFeatureDataCleaner).removeUserData()
        verifyResponse(jsMessage, true, mockJsMessaging)
    }
}
