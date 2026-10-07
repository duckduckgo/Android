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

package com.duckduckgo.app.internalfeedback

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class InternalFeedbackContentScopeJsMessageHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val mockDeviceInfoProvider: InternalFeedbackDeviceInfoProvider = mock()
    private val mockJsMessaging: JsMessaging = mock()
    private val deviceInfo = JSONObject().put("platform", "android")

    private lateinit var handler: InternalFeedbackContentScopeJsMessageHandler

    @Before
    fun setUp() = runTest {
        whenever(mockDeviceInfoProvider.getDeviceInfo()).thenReturn(Result.success(deviceInfo))

        handler = InternalFeedbackContentScopeJsMessageHandler(
            deviceInfoProvider = mockDeviceInfoProvider,
            appCoroutineScope = coroutineTestRule.testScope,
        )
    }

    @Test
    fun whenCheckingAllowedDomainsThenOnlyInternalFeedbackHostIsAllowed() {
        assertEquals(listOf("internalapps.duckduckgo.com"), handler.getJsMessageHandler().allowedDomains)
    }

    @Test
    fun whenCheckingFeatureNameThenReturnsInternalFeedback() {
        assertEquals("internalFeedback", handler.getJsMessageHandler().featureName)
    }

    @Test
    fun whenCheckingMethodsThenReturnsGetDeviceInfo() {
        assertEquals(listOf("getDeviceInfo"), handler.getJsMessageHandler().methods)
    }

    @Test
    fun whenGetDeviceInfoRequestedThenRespondsWithDeviceInfo() {
        with(processGetDeviceInfo()) {
            assertSame(deviceInfo, params)
            assertEquals("internalFeedback", featureName)
            assertEquals("getDeviceInfo", method)
            assertEquals("123", id)
        }
    }

    @Test
    fun whenGettingDeviceInfoFailsThenRespondsWithEmptyObject() = runTest {
        whenever(mockDeviceInfoProvider.getDeviceInfo()).thenReturn(Result.failure(IllegalStateException()))

        assertEquals(0, processGetDeviceInfo().params.length())
    }

    @Test
    fun whenGetDeviceInfoRequestedWithoutIdThenDoesNotRespond() {
        handler.getJsMessageHandler().process(getDeviceInfoMessage(id = null), mockJsMessaging, null)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(mockJsMessaging)
    }

    private fun processGetDeviceInfo(): JsCallbackData {
        handler.getJsMessageHandler().process(getDeviceInfoMessage(id = "123"), mockJsMessaging, null)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val captor = argumentCaptor<JsCallbackData>()
        verify(mockJsMessaging).onResponse(captor.capture())
        return captor.firstValue
    }

    private fun getDeviceInfoMessage(id: String?) = JsMessage(
        context = "contentScopeScripts",
        featureName = "internalFeedback",
        method = "getDeviceInfo",
        params = JSONObject(),
        id = id,
    )
}
