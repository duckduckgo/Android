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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.verifyResponse
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class PirRemoveOptOutFromDashboardMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirRemoveOptOutFromDashboardMessageHandler

    private val mockJobRecordUpdater: JobRecordUpdater = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirRemoveOptOutFromDashboardMessageHandler(
            jobRecordUpdater = mockJobRecordUpdater,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.REMOVE_OPT_OUT_FROM_DASHBOARD, testee.message)
    }

    @Test
    fun whenProcessWithValidRecordIdThenCallsJobRecordUpdaterAndSendsSuccessResponse() = runTest {
        // Given
        val testRecordId = 123L
        val jsMessage = createJsMessage(
            paramsJson = """{"recordId": $testRecordId}""",
            method = PirDashboardWebMessages.REMOVE_OPT_OUT_FROM_DASHBOARD,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockJobRecordUpdater).markRecordsAsRemovedByUser(testRecordId)
        verifyResponse(jsMessage, true, mockJsMessaging)
    }

    @Test
    fun whenProcessWithMissingRecordIdThenSendsErrorResponseWithoutCallingJobRecordUpdater() = runTest {
        // Given
        val jsMessage = createJsMessage(
            paramsJson = """{}""",
            method = PirDashboardWebMessages.REMOVE_OPT_OUT_FROM_DASHBOARD,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verifyResponse(jsMessage, false, mockJsMessaging)
        verify(mockJobRecordUpdater, org.mockito.kotlin.never()).markRecordsAsRemovedByUser(org.mockito.kotlin.any())
    }
}
