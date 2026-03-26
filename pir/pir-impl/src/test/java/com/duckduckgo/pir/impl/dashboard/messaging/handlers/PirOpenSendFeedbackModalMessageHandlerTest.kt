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
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages.OPEN_SEND_FEEDBACK_MODAL
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.PIR_DASHBOARD
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirOpenSendFeedbackModalMessageHandlerTest {

    private lateinit var testee: PirOpenSendFeedbackModalMessageHandler

    private val mockGlobalActivityStarter: GlobalActivityStarter = mock()
    private val mockContext: Context = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()

    @Before
    fun setUp() {
        testee = PirOpenSendFeedbackModalMessageHandler(
            globalActivityStarter = mockGlobalActivityStarter,
            context = mockContext,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(OPEN_SEND_FEEDBACK_MODAL, testee.message)
    }

    @Test
    fun whenProcessThenCallsGlobalActivityStarterWithCorrectParams() {
        // Given
        val jsMessage = createJsMessage("""""", OPEN_SEND_FEEDBACK_MODAL)
        val mockIntent = Intent()
        whenever(
            mockGlobalActivityStarter.startIntent(
                mockContext,
                PrivacyProFeedbackScreenWithParams(feedbackSource = PIR_DASHBOARD),
            ),
        ).thenReturn(mockIntent)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        val paramsCaptor = argumentCaptor<PrivacyProFeedbackScreenWithParams>()
        verify(mockGlobalActivityStarter).startIntent(eq(mockContext), paramsCaptor.capture())
        assertEquals(PIR_DASHBOARD, paramsCaptor.firstValue.feedbackSource)
    }

    @Test
    fun whenProcessThenSetsIntentFlags() {
        // Given
        val jsMessage = createJsMessage("""""", OPEN_SEND_FEEDBACK_MODAL)
        val mockIntent = Intent()
        whenever(
            mockGlobalActivityStarter.startIntent(
                mockContext,
                PrivacyProFeedbackScreenWithParams(feedbackSource = PIR_DASHBOARD),
            ),
        ).thenReturn(mockIntent)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, mockIntent.flags)
    }

    @Test
    fun whenProcessThenStartsActivityWithIntent() {
        // Given
        val jsMessage = createJsMessage("""""", OPEN_SEND_FEEDBACK_MODAL)
        val mockIntent = Intent()
        whenever(
            mockGlobalActivityStarter.startIntent(
                mockContext,
                PrivacyProFeedbackScreenWithParams(feedbackSource = PIR_DASHBOARD),
            ),
        ).thenReturn(mockIntent)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockContext).startActivity(mockIntent)
    }
}
