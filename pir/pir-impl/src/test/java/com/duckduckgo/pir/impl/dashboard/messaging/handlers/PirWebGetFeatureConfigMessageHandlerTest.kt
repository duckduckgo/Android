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
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages.GET_FEATURE_CONFIG
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.DDG_SETTINGS
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirWebGetFeatureConfigMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebGetFeatureConfigMessageHandler

    private val mockPrivacyProUnifiedFeedback: PrivacyProUnifiedFeedback = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val testScope = TestScope()

    @Before
    fun setUp() {
        testee = PirWebGetFeatureConfigMessageHandler(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = testScope,
            privacyProUnifiedFeedback = mockPrivacyProUnifiedFeedback,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(GET_FEATURE_CONFIG, testee.message)
    }

    @Test
    fun whenProcessWithUnifiedFeedbackEnabledThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", GET_FEATURE_CONFIG)
        whenever(mockPrivacyProUnifiedFeedback.shouldUseUnifiedFeedback(DDG_SETTINGS)).thenReturn(
            true,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPrivacyProUnifiedFeedback).shouldUseUnifiedFeedback(DDG_SETTINGS)
        verifyFeatureConfigResponse(jsMessage, useUnifiedFeedback = true)
    }

    @Test
    fun whenProcessWithUnifiedFeedbackDisabledThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", GET_FEATURE_CONFIG)
        whenever(mockPrivacyProUnifiedFeedback.shouldUseUnifiedFeedback(DDG_SETTINGS)).thenReturn(
            false,
        )

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockPrivacyProUnifiedFeedback).shouldUseUnifiedFeedback(DDG_SETTINGS)
        verifyFeatureConfigResponse(jsMessage, useUnifiedFeedback = false)
    }

    private fun verifyFeatureConfigResponse(jsMessage: JsMessage, useUnifiedFeedback: Boolean) {
        val callbackDataCaptor = argumentCaptor<JsCallbackData>()
        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())

        val callbackData = callbackDataCaptor.firstValue
        assertEquals(jsMessage.featureName, callbackData.featureName)
        assertEquals(jsMessage.method, callbackData.method)
        assertEquals(jsMessage.id ?: "", callbackData.id)

        // Verify feature config response structure
        assertTrue(callbackData.params.has("useUnifiedFeedback"))
        assertEquals(useUnifiedFeedback, callbackData.params.getBoolean("useUnifiedFeedback"))
    }
}
