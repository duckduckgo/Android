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
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.messaging.handlers.PirMessageHandlerUtils.createJsMessage
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.model.Entitlement
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
class PirWebHandshakeMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PirWebHandshakeMessageHandler

    private val mockSubscriptions: Subscriptions = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val mockJsMessageCallback: JsMessageCallback = mock()
    private val testScope = TestScope()

    @Before
    fun setUp() {
        testee = PirWebHandshakeMessageHandler(
            subscriptions = mockSubscriptions,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            appCoroutineScope = testScope,
        )
    }

    @Test
    fun whenMessageIsSetThenReturnsCorrectMessage() {
        assertEquals(PirDashboardWebMessages.HANDSHAKE, testee.message)
    }

    @Test
    fun whenSignedInWithNoSubscriptionThenHandshakeReportsUnauthenticated() = runTest {
        // A failed purchase leaves an account and a valid access token behind with no subscription.
        // The run gate resolves that user to SCAN_ONLY, so the dashboard must render freemium too —
        // otherwise the page offers removals the native side will refuse to run.
        val jsMessage = createJsMessage("", PirDashboardWebMessages.HANDSHAKE)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(mockSubscriptions.getCurrentEntitlements()).thenReturn(emptySet())
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(false)

        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        verifyHandshakeResponse(jsMessage, isAuthenticated = false, isEligibleForTrial = false)
    }

    @Test
    fun whenSubscribedWithPirEntitlementThenHandshakeReportsAuthenticated() = runTest {
        val jsMessage = createJsMessage("", PirDashboardWebMessages.HANDSHAKE)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(mockSubscriptions.getCurrentEntitlements()).thenReturn(setOf(Entitlement("", Product.PIR.value)))
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(false)

        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        verifyHandshakeResponse(jsMessage, isAuthenticated = true, isEligibleForTrial = false)
    }

    @Test
    fun whenSubscribedWithoutPirEntitlementThenHandshakeReportsUnauthenticated() = runTest {
        val jsMessage = createJsMessage("", PirDashboardWebMessages.HANDSHAKE)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(mockSubscriptions.getCurrentEntitlements()).thenReturn(emptySet())
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(false)

        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        verifyHandshakeResponse(jsMessage, isAuthenticated = false, isEligibleForTrial = false)
    }

    @Test
    fun whenProcessWithAuthenticatedUserEligibleForTrialThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.HANDSHAKE)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(mockSubscriptions.getCurrentEntitlements()).thenReturn(setOf(Entitlement("", Product.PIR.value)))
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockSubscriptions).getSubscriptionStatus()
        verify(mockSubscriptions).isFreeTrialEligible()
        verifyHandshakeResponse(jsMessage, isAuthenticated = true, isEligibleForTrial = true)
    }

    @Test
    fun whenProcessWithAuthenticatedUserNotEligibleForTrialThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.HANDSHAKE)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(mockSubscriptions.getCurrentEntitlements()).thenReturn(setOf(Entitlement("", Product.PIR.value)))
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockSubscriptions).getSubscriptionStatus()
        verify(mockSubscriptions).isFreeTrialEligible()
        verifyHandshakeResponse(jsMessage, isAuthenticated = true, isEligibleForTrial = false)
    }

    @Test
    fun whenProcessWithNonAuthenticatedUserEligibleForTrialThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.HANDSHAKE)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(mockSubscriptions.getCurrentEntitlements()).thenReturn(emptySet())
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(true)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockSubscriptions).getSubscriptionStatus()
        verify(mockSubscriptions).isFreeTrialEligible()
        verifyHandshakeResponse(jsMessage, isAuthenticated = false, isEligibleForTrial = true)
    }

    @Test
    fun whenProcessWithNonAuthenticatedUserNotEligibleForTrialThenSendsCorrectResponse() = runTest {
        // Given
        val jsMessage = createJsMessage("""""", PirDashboardWebMessages.HANDSHAKE)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(mockSubscriptions.getCurrentEntitlements()).thenReturn(emptySet())
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(false)

        // When
        testee.process(jsMessage, mockJsMessaging, mockJsMessageCallback)

        // Then
        verify(mockSubscriptions).getSubscriptionStatus()
        verify(mockSubscriptions).isFreeTrialEligible()
        verifyHandshakeResponse(jsMessage, isAuthenticated = false, isEligibleForTrial = false)
    }

    private fun verifyHandshakeResponse(
        jsMessage: JsMessage,
        isAuthenticated: Boolean,
        isEligibleForTrial: Boolean,
    ) {
        val callbackDataCaptor = argumentCaptor<JsCallbackData>()
        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())

        val callbackData = callbackDataCaptor.firstValue
        assertEquals(jsMessage.featureName, callbackData.featureName)
        assertEquals(jsMessage.method, callbackData.method)
        assertEquals(jsMessage.id ?: "", callbackData.id)

        // Verify handshake response structure
        assertTrue(callbackData.params.has("success"))
        assertEquals(true, callbackData.params.getBoolean("success"))

        assertTrue(callbackData.params.has("userdata"))
        val userData = callbackData.params.getJSONObject("userdata")

        assertTrue(userData.has("isAuthenticatedUser"))
        assertEquals(isAuthenticated, userData.getBoolean("isAuthenticatedUser"))

        assertTrue(userData.has("isUserEligibleForFreeTrial"))
        assertEquals(isEligibleForTrial, userData.getBoolean("isUserEligibleForFreeTrial"))

        assertTrue(callbackData.params.has("version"))
        assertEquals(
            PirDashboardWebConstants.SCRIPT_API_VERSION,
            callbackData.params.getInt("version"),
        )
    }
}
