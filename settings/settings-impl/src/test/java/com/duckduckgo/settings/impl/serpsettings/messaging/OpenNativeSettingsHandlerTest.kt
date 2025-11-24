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
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChatNativeSettingsNoParams
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.settings.impl.serpsettings.fakes.FakeGlobalActivityStarter
import com.duckduckgo.settings.impl.serpsettings.fakes.FakeJsMessaging
import com.duckduckgo.settings.impl.serpsettings.fakes.FakePixel
import com.duckduckgo.settings.impl.serpsettings.pixel.SerpSettingsPixelName.SERP_SETTINGS_OPEN_DUCK_AI
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class OpenNativeSettingsHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var fakePixel: FakePixel
    private lateinit var fakeJsMessaging: FakeJsMessaging
    private lateinit var fakeGlobalActivityStarter: FakeGlobalActivityStarter
    private lateinit var handler: OpenNativeSettingsHandler

    @Before
    fun setUp() {
        fakePixel = FakePixel()
        fakeJsMessaging = FakeJsMessaging()
        fakeGlobalActivityStarter = FakeGlobalActivityStarter()

        handler = OpenNativeSettingsHandler(
            context = ApplicationProvider.getApplicationContext(),
            globalActivityStarter = fakeGlobalActivityStarter,
            pixel = fakePixel,
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
    fun `only contains openNativeSettings method`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("openNativeSettings", methods[0])
    }

    @Test
    fun `when openNativeSettings called with aiFeatures screen then pixel is fired`() = runTest {
        val jsMessage = createJsMessage(screenParam = "aiFeatures")
        fakeGlobalActivityStarter.intentToReturn = Intent()

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )

        advanceUntilIdle()

        assertEquals(1, fakePixel.firedPixels.size)
        assertEquals(SERP_SETTINGS_OPEN_DUCK_AI.pixelName, fakePixel.firedPixels.first())
    }

    @Test
    fun `when openNativeSettings called with aiFeatures screen then launches DuckChat settings`() = runTest {
        val jsMessage = createJsMessage(screenParam = "aiFeatures")
        fakeGlobalActivityStarter.intentToReturn = Intent()

        handler.getJsMessageHandler().process(
            jsMessage = jsMessage,
            jsMessaging = fakeJsMessaging,
            jsMessageCallback = null,
        )

        advanceUntilIdle()

        assertEquals(1, fakeGlobalActivityStarter.startedActivities.size)
        assertEquals(DuckChatNativeSettingsNoParams, fakeGlobalActivityStarter.startedActivities.first())
    }

    private fun createJsMessage(screenParam: String): JsMessage {
        return JsMessage(
            context = "test",
            featureName = "serpSettings",
            method = "openNativeSettings",
            id = "123",
            params = JSONObject().apply {
                put("screen", screenParam)
            },
        )
    }
}
