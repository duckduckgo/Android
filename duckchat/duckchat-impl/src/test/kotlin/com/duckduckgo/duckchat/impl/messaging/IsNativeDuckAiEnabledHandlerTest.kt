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

package com.duckduckgo.duckchat.impl.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.messaging.fakes.FakeDuckChat
import com.duckduckgo.duckchat.impl.messaging.fakes.FakeJsMessaging
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.settings.api.SettingsPageFeature
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IsNativeDuckAiEnabledHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val fakeDuckChat = FakeDuckChat(enabled = true)
    private val settingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)
    private lateinit var handler: IsNativeDuckAiEnabledHandler

    @Before
    fun setUp() {
        handler = IsNativeDuckAiEnabledHandler(
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appScope = coroutineTestRule.testScope,
            settingsPageFeature = settingsPageFeature,
            duckChat = fakeDuckChat,
        )
    }

    @Test
    fun `only allow duckduckgo dot com domains`() {
        val domains = handler.getJsMessageHandler().allowedDomains
        assertEquals(1, domains.size)
        assertEquals("duckduckgo.com", domains.first())
    }

    @Test
    fun `feature name is serpSettings`() {
        assertEquals("serpSettings", handler.getJsMessageHandler().featureName)
    }

    @Test
    fun `only contains isNativeDuckAiEnabled method`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("isNativeDuckAiEnabled", methods[0])
    }

    @Test
    fun `when id is null then no response is sent`() = runTest {
        @Suppress("DenyListedApi")
        settingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        fakeDuckChat.setEnabled(true)

        val fakeJsMessaging = FakeJsMessaging()
        val jsMessage = JsMessage(
            context = "test",
            featureName = "serpSettings",
            method = "isNativeDuckAiEnabled",
            id = null,
            params = JSONObject(),
        )

        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(0, fakeJsMessaging.getResponseCount())
    }

    @Test
    fun `when id is not null then response is sent`() = runTest {
        @Suppress("DenyListedApi")
        settingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        fakeDuckChat.setEnabled(true)

        val fakeJsMessaging = FakeJsMessaging()
        val jsMessage = JsMessage(
            context = "test",
            featureName = "serpSettings",
            method = "isNativeDuckAiEnabled",
            id = "test-id",
            params = JSONObject(),
        )

        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)

        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(1, fakeJsMessaging.getResponseCount())
        val response = fakeJsMessaging.getLastResponse()!!
        assertEquals("test-id", response.id)
        assertEquals("serpSettings", response.featureName)
        assertEquals("isNativeDuckAiEnabled", response.method)
        assertEquals(true, response.params.getBoolean("enabled"))
    }
}
