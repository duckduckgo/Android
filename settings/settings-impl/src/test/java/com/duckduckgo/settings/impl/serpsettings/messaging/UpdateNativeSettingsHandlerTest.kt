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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class UpdateNativeSettingsHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val fakeSettingsPageFeature: SettingsPageFeature =
        FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)
    private lateinit var fakeDataStore: FakeSerpSettingsDataStore
    private lateinit var fakeJsMessaging: FakeJsMessaging
    private lateinit var handler: UpdateNativeSettingsHandler

    @Before
    fun setUp() {
        fakeDataStore = FakeSerpSettingsDataStore()
        fakeJsMessaging = FakeJsMessaging()

        handler = UpdateNativeSettingsHandler(
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
    fun `only contains updateNativeSettings method`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("updateNativeSettings", methods[0])
    }

    @Test
    fun `when feature flag is disabled then settings are not stored`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = false))
        val settingsJson = """{"isDuckAiEnabled":true,"duckAiTitle":"Duck.AI"}"""
        val jsMessage = createJsMessage(JSONObject(settingsJson))

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        assertNull(fakeDataStore.getSerpSettings())
    }

    @Test
    fun `when valid settings provided then stores them as JSON string`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        val settingsJson = """{"isDuckAiEnabled":true,"duckAiTitle":"Duck.AI"}"""
        val jsMessage = createJsMessage(JSONObject(settingsJson))

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val storedSettings = fakeDataStore.getSerpSettings()
        assertEquals(settingsJson, storedSettings)
    }

    @Test
    fun `when empty params provided then stores empty object`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))
        val jsMessage = createJsMessage(JSONObject())

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val storedSettings = fakeDataStore.getSerpSettings()
        assertEquals("{}", storedSettings)
    }

    @Test
    fun `when settings updated multiple times then stores latest settings`() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(Toggle.State(enable = true))

        val firstSettings = """{"isDuckAiEnabled":true}"""
        handler.getJsMessageHandler().process(
            jsMessage = createJsMessage(JSONObject(firstSettings)),
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val secondSettings = """{"isDuckAiEnabled":false,"newSetting":"value"}"""
        handler.getJsMessageHandler().process(
            jsMessage = createJsMessage(JSONObject(secondSettings)),
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        val storedSettings = fakeDataStore.getSerpSettings()
        assertEquals(secondSettings, storedSettings)
    }

    private fun createJsMessage(params: JSONObject): JsMessage {
        return JsMessage(
            context = "test",
            featureName = "serpSettings",
            method = "updateNativeSettings",
            id = "123",
            params = params,
        )
    }
}
