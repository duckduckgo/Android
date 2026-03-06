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

package com.duckduckgo.duckchat.impl.clearing

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckAiDataClearingFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealDuckChatDeleterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val messaging: DuckChatDeleterJsMessaging = mock()
    private val duckAiDataClearingFeature = FakeFeatureToggleFactory.create(DuckAiDataClearingFeature::class.java)

    private lateinit var clearer: RealDuckChatDeleter

    @Before
    fun setup() {
        clearer = RealDuckChatDeleter(
            context = context,
            dispatchers = coroutineRule.testDispatcherProvider,
            appBuildConfig = appBuildConfig,
            messaging = messaging,
            duckAiDataClearingFeature = duckAiDataClearingFeature,
        )
    }

    // region getContentScopeJson

    @Test
    fun `when feature is enabled then content scope state is enabled`() {
        duckAiDataClearingFeature.self().setRawStoredState(State(enable = true))

        val result = JSONObject(clearer.getContentScopeJson())
        val feature = result.getJSONObject("features").getJSONObject("duckAiDataClearing")

        assertEquals("enabled", feature.getString("state"))
    }

    @Test
    fun `when feature is disabled then content scope state is disabled`() {
        duckAiDataClearingFeature.self().setRawStoredState(State(enable = false))

        val result = JSONObject(clearer.getContentScopeJson())
        val feature = result.getJSONObject("features").getJSONObject("duckAiDataClearing")

        assertEquals("disabled", feature.getString("state"))
    }

    @Test
    fun `when feature has settings then content scope includes settings`() {
        duckAiDataClearingFeature.self().setRawStoredState(
            State(
                enable = true,
                settings = """{"chatsLocalStorageKeys":["savedAIChats"],""" +
                    """"chatImagesIndexDbNameObjectStoreNamePairs":[["savedAIChatData","chat-images"]]}""",
            ),
        )

        val result = JSONObject(clearer.getContentScopeJson())
        val settings = result.getJSONObject("features").getJSONObject("duckAiDataClearing").getJSONObject("settings")

        assertEquals("savedAIChats", settings.getJSONArray("chatsLocalStorageKeys").getString(0))
        assertEquals(1, settings.getJSONArray("chatImagesIndexDbNameObjectStoreNamePairs").length())
    }

    @Test
    fun `when feature has no settings then content scope uses default settings`() {
        duckAiDataClearingFeature.self().setRawStoredState(State(enable = true))

        val result = JSONObject(clearer.getContentScopeJson())
        val settings = result.getJSONObject("features").getJSONObject("duckAiDataClearing").getJSONObject("settings")

        assertEquals("savedAIChats", settings.getJSONArray("chatsLocalStorageKeys").getString(0))
        assertEquals(2, settings.getJSONArray("chatImagesIndexDbNameObjectStoreNamePairs").length())
    }

    @Test
    fun `when feature has exceptions then content scope includes exceptions`() {
        duckAiDataClearingFeature.self().setRawStoredState(
            State(
                enable = true,
                exceptions = listOf(FeatureException(domain = "example.com", reason = "test reason")),
            ),
        )

        val result = JSONObject(clearer.getContentScopeJson())
        val exceptions = result.getJSONObject("features").getJSONObject("duckAiDataClearing").getJSONArray("exceptions")

        assertEquals(1, exceptions.length())
        assertEquals("example.com", exceptions.getJSONObject(0).getString("domain"))
        assertEquals("test reason", exceptions.getJSONObject(0).getString("reason"))
    }

    @Test
    fun `when feature has exception without reason then exception has no reason field`() {
        duckAiDataClearingFeature.self().setRawStoredState(
            State(
                enable = true,
                exceptions = listOf(FeatureException(domain = "example.com", reason = null)),
            ),
        )

        val result = JSONObject(clearer.getContentScopeJson())
        val exceptions = result.getJSONObject("features").getJSONObject("duckAiDataClearing").getJSONArray("exceptions")

        assertEquals("example.com", exceptions.getJSONObject(0).getString("domain"))
        assertEquals(false, exceptions.getJSONObject(0).has("reason"))
    }

    @Test
    fun `when getContentScopeJson called then result has unprotectedTemporary empty array`() {
        val result = JSONObject(clearer.getContentScopeJson())

        assertEquals(0, result.getJSONArray("unprotectedTemporary").length())
    }

    // endregion
}
