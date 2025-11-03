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

package com.duckduckgo.settings.impl.serpsettings.messaging

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.settings.impl.serpsettings.fakes.FakeJsMessaging
import com.duckduckgo.settings.impl.serpsettings.fakes.FakeSerpSettingsDataStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class GetNativeSettingsHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val fakeSettingsPageFeature: SettingsPageFeature =
        FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)
    private lateinit var fakeDataStore: FakeSerpSettingsDataStore
    private lateinit var fakeJsMessaging: FakeJsMessaging
    private lateinit var handler: GetNativeSettingsHandler

    @Before
    fun setUp() {
        fakeDataStore = FakeSerpSettingsDataStore()
        fakeJsMessaging = FakeJsMessaging()

        handler = GetNativeSettingsHandler(
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            appScope = coroutineTestRule.testScope,
            settingsPageFeature = fakeSettingsPageFeature,
            serpSettingsDataStore = fakeDataStore,
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
    fun `only contains getNativeSettings method`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("getNativeSettings", methods[0])
    }

    @Test
    fun `when feature flag is disabled then no response is sent`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = false))
        val jsMessage = createJsMessage()

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(0, fakeJsMessaging.getResponseCount())
    }

    @Test
    fun `when settings are null then returns empty JSONObject`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        fakeDataStore.reset()
        val jsMessage = createJsMessage()

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val response = fakeJsMessaging.getLastResponse()!!
        assertEquals(jsMessage.featureName, response.featureName)
        assertEquals(jsMessage.method, response.method)
        assertEquals(jsMessage.id, response.id)
        assertEquals(0, response.params.length())
    }

    @Test
    fun `when settings are empty string then returns empty JSONObject`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        fakeDataStore.setSerpSettings("")
        val jsMessage = createJsMessage()

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val response = fakeJsMessaging.getLastResponse()!!
        assertEquals(jsMessage.featureName, response.featureName)
        assertEquals(jsMessage.method, response.method)
        assertEquals(jsMessage.id, response.id)
        assertEquals(0, response.params.length())
    }

    @Test
    fun `when settings contain valid JSON then parses and returns JSONObject`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        val settingsJson = """{"isDuckAiEnabled":"true","duckAiTitle":"Duck.AI"}"""
        fakeDataStore.setSerpSettings(settingsJson)
        val jsMessage = createJsMessage()

        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val response = fakeJsMessaging.getLastResponse()!!
        assertEquals(jsMessage.featureName, response.featureName)
        assertEquals(jsMessage.method, response.method)
        assertEquals(jsMessage.id, response.id)

        assertEquals(2, response.params.length())
        assertEquals(true, response.params.getBoolean("isDuckAiEnabled"))
        assertEquals("Duck.AI", response.params.getString("duckAiTitle"))
    }

    @Test
    fun `when id is null then no response is sent`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        fakeDataStore.setSerpSettings("""{"isDuckAiEnabled":"true"}""")

        val jsMessage = JsMessage(
            context = "test",
            featureName = "serpSettings",
            method = "getNativeSettings",
            id = null,
            params = JSONObject(),
        )

        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(0, fakeJsMessaging.getResponseCount())
    }

    @Test
    fun `when id is not null then response is sent`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        fakeDataStore.setSerpSettings("""{"isDuckAiEnabled":"true"}""")

        val jsMessage = JsMessage(
            context = "test",
            featureName = "serpSettings",
            method = "getNativeSettings",
            id = "test-id",
            params = JSONObject(),
        )

        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertEquals(1, fakeJsMessaging.getResponseCount())
        val response = fakeJsMessaging.getLastResponse()!!
        assertEquals("test-id", response.id)
    }

    private fun createJsMessage(): JsMessage {
        return JsMessage(
            context = "test",
            featureName = "serpSettings",
            method = "getNativeSettings",
            id = "123",
            params = JSONObject(),
        )
    }
}
