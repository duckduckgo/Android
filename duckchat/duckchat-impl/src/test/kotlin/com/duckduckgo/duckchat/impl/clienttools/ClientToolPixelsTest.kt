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

package com.duckduckgo.duckchat.impl.clienttools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class ClientToolPixelsTest {

    private val appBuildConfig: AppBuildConfig = mock {
        on { flavor } doReturn BuildFlavor.PLAY
    }
    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val store = FakeClientToolPermissionStore()
    private val prompt = FakeClientToolPermissionPrompt()
    private val chatGrants = ClientToolChatGrants()
    private val pixel = RecordingPixel()
    private var now = 0L
    private val timeProvider = FakeElapsedTimeProvider { now }

    private val listOpenTabs = FakeClientTool(
        name = "listOpenTabs",
        result = { ClientToolResult.Success(JSONObject().put("tabs", JSONArray(listOf(1, 2, 3)))) },
    )
    private val searchHistory = FakeClientTool(name = "searchHistory", permissionMode = ASK_MODE)
    private val searchBookmarks = FakeClientTool(name = "searchBookmarks", permissionMode = ASK_MODE)
    private val openTab = FakeClientTool(
        name = "openTab",
        permissionMode = ClientToolPermissionMode.AskEveryTime(promptTitle = 1, promptMessage = 2),
        detail = { ClientToolPromptDetail.Shown("https://example.com/") },
    )

    private fun executor(clock: () -> Long = { now }): RealClientToolsExecutor {
        val time = FakeElapsedTimeProvider(clock)
        val pixels = ClientToolPixels(pixel)
        return RealClientToolsExecutor(
            tools = FakeClientToolPluginPoint(listOpenTabs, searchHistory, searchBookmarks, openTab),
            permissionGate = RealClientToolPermissionGate(appBuildConfig, feature, store, chatGrants, prompt, pixels, time),
            duckAiHostProvider = FakeDuckAiHostProvider(),
            browserToolsFeature = feature,
            pixels = pixels,
            timeProvider = time,
        )
    }

    @Test
    fun whenToolsAreOfferedToDuckAiThenEnabledDailyPixelFires() {
        executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR)

        assertEquals(listOf(Pixel.PixelType.Daily()), pixel.named("m_aichat_tools_enabled_daily").map { it.type })
    }

    @Test
    fun whenNoToolIsOfferedThenEnabledPixelDoesNotFire() {
        executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.FIRE)

        assertTrue(pixel.fired.isEmpty())
    }

    @Test
    fun whenAutoToolSucceedsThenCallPixelCarriesSlugPathDurationAndResultCount() = runTest {
        listOpenTabs.result = {
            now += 600
            ClientToolResult.Success(JSONObject().put("tabs", JSONArray(listOf(1, 2, 3))))
        }

        execute("listOpenTabs")

        assertEquals(
            mapOf(
                "tool" to "list_open_tabs",
                "result" to "success",
                "permission_path" to "auto",
                "duration" to "lt_2s",
                "result_count" to "1-5",
            ),
            pixel.named(ClientToolPixels.CALL).single().parameters,
        )
    }

    @Test
    fun whenToolSucceedsThenUsedPixelsFireDaily() = runTest {
        execute("listOpenTabs")

        assertEquals(Pixel.PixelType.Daily(), pixel.named("m_aichat_tools_used_daily").single().type)
        assertEquals(Pixel.PixelType.Daily(), pixel.named("m_aichat_tools_used_list_open_tabs_daily").single().type)
    }

    @Test
    fun whenToolSetToNeverThenCallIsDeniedByNeverAndNotCountedAsUsed() = runTest {
        store.choices["listOpenTabs"] = ClientToolPermission.NEVER_ALLOW

        execute("listOpenTabs")

        assertEquals(
            mapOf("tool" to "list_open_tabs", "result" to "denied", "permission_path" to "never"),
            pixel.named(ClientToolPixels.CALL).single().parameters,
        )
        assertTrue(pixel.named("m_aichat_tools_used_daily").isEmpty())
    }

    @Test
    fun whenToolTurnedOffRemotelyThenUnavailableBeforePermissionWithoutProtocolError() = runTest {
        listOpenTabs.enabled = false

        execute("listOpenTabs")

        assertEquals(
            mapOf("tool" to "list_open_tabs", "result" to "unavailable", "permission_path" to "none"),
            pixel.named(ClientToolPixels.CALL).single().parameters,
        )
        assertTrue(pixel.named(ClientToolPixels.PROTOCOL_ERROR).isEmpty())
    }

    @Test
    fun whenDuckAiCallsUnknownCapabilityThenProtocolErrorNeverSendsTheName() = runTest {
        execute("pinTab")

        assertEquals(
            listOf(mapOf("reason" to "unknown_capability", "tool" to "unknown")),
            pixel.fired.map { it.parameters },
        )
    }

    @Test
    fun whenDuckAiCallsKnownToolWithUnknownVersionThenProtocolErrorNamesTheTool() = runTest {
        execute("listOpenTabs", version = 9)

        assertEquals(
            mapOf("reason" to "unknown_capability", "tool" to "list_open_tabs"),
            pixel.named(ClientToolPixels.PROTOCOL_ERROR).single().parameters,
        )
    }

    @Test
    fun whenCallCannotBeReadThenInvalidRequestProtocolError() = runTest {
        val params = JSONObject("""{"callId":"call-1","capability":{"name":"listOpenTabs","version":1}}""")

        executor().execute(params, DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals(
            listOf(mapOf("reason" to "invalid_request", "tool" to "list_open_tabs")),
            pixel.fired.map { it.parameters },
        )
    }

    @Test
    fun whenCalledFromFireModeThenNothingIsSent() = runTest {
        executor().execute(request("listOpenTabs"), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.FIRE)

        assertTrue(pixel.fired.isEmpty())
    }

    @Test
    fun whenUserAllowsInChatThenPromptAnsweredAndLaterCallsTakeTheChatPath() = runTest {
        val reply = executeAsync("searchHistory")
        now += 3_000
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        reply.await()
        execute("searchHistory")

        assertEquals(
            mapOf("tool" to "search_history", "answer" to "allow_for_chat", "time_to_answer" to "lt_10s"),
            pixel.named(ClientToolPixels.PROMPT_ANSWERED).single().parameters,
        )
        assertEquals(listOf("prompt", "chat"), pixel.named(ClientToolPixels.CALL).map { it.parameters["permission_path"] })
    }

    @Test
    fun whenBatchAccessIsOnThenPromptsAreNumberedPerChat() = runTest {
        enableRequestAccess()

        repeat(2) {
            val reply = executeAsync("searchHistory")
            prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
            reply.await()
        }

        assertEquals(
            listOf("1", "2"),
            pixel.named(ClientToolPixels.PROMPT_ANSWERED).map { it.parameters["prompt_number_in_chat"] },
        )
        assertEquals(listOf("dont_allow", "dont_allow"), pixel.named(ClientToolPixels.PROMPT_ANSWERED).map { it.parameters["answer"] })
    }

    @Test
    fun whenPromptReachesDeadlineThenAnsweredAsTimeoutAndCallCancelled() = runTest {
        val executor = executor { testScheduler.currentTime }
        val reply = async { executor.execute(request("searchHistory"), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        advanceTimeBy(120_001)
        reply.await()

        assertEquals(
            mapOf("tool" to "search_history", "answer" to "timeout", "time_to_answer" to "gte_60s"),
            pixel.named(ClientToolPixels.PROMPT_ANSWERED).single().parameters,
        )
        assertEquals(
            mapOf("tool" to "search_history", "result" to "cancelled", "permission_path" to "prompt"),
            pixel.named(ClientToolPixels.CALL).single().parameters,
        )
    }

    @Test
    fun whenChatGoesAwayThenOpenPromptIsWithdrawn() = runTest {
        val executor = executor()
        val reply = async { executor.execute(request("searchHistory"), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        executor.cancelPendingCalls(CHAT_TAB_ID, contextual = false)
        reply.await()

        assertEquals("withdrawn", pixel.named(ClientToolPixels.PROMPT_ANSWERED).single().parameters["answer"])
    }

    @Test
    fun whenAskEveryTimeToolAllowedThenAnsweredAllowOnce() = runTest {
        val reply = executeAsync("openTab")
        prompt.current.answer(ClientToolPermissionAnswer.AllowOnce)
        reply.await()

        assertEquals("allow_once", pixel.named(ClientToolPixels.PROMPT_ANSWERED).single().parameters["answer"])
        assertEquals("prompt", pixel.named(ClientToolPixels.CALL).single().parameters["permission_path"])
    }

    @Test
    fun whenBatchRequestAllowedWithOneToolRemovedThenEachToolReportsItsAccess() = runTest {
        enableRequestAccess()
        val reply = async { requestAccess("listOpenTabs", "searchHistory", "searchBookmarks", "pinTab") }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat(removedTools = setOf("searchBookmarks")))
        reply.await()

        assertEquals(
            mapOf("decision" to "allow", "requested" to "4", "asked" to "2", "removed" to "1"),
            pixel.named(ClientToolPixels.ACCESS_REQUESTED).single().parameters,
        )
        assertEquals(
            listOf(
                mapOf("tool" to "list_open_tabs", "access" to "already_allowed"),
                mapOf("tool" to "search_history", "access" to "granted"),
                mapOf("tool" to "search_bookmarks", "access" to "removed"),
            ),
            pixel.named(ClientToolPixels.ACCESS_RESULT).map { it.parameters },
        )
    }

    @Test
    fun whenBatchRequestRefusedThenAskedToolsAreDenied() = runTest {
        enableRequestAccess()
        store.choices["listOpenTabs"] = ClientToolPermission.NEVER_ALLOW
        val reply = async { requestAccess("listOpenTabs", "searchHistory") }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        reply.await()

        assertEquals("dont_allow", pixel.named(ClientToolPixels.ACCESS_REQUESTED).single().parameters["decision"])
        assertEquals(listOf("never", "denied"), pixel.named(ClientToolPixels.ACCESS_RESULT).map { it.parameters["access"] })
    }

    @Test
    fun whenNothingNeedsAskingThenBatchRequestReportsNoPrompt() = runTest {
        enableRequestAccess()
        listOpenTabs.enabled = false

        requestAccess("listOpenTabs")

        assertEquals(
            mapOf("decision" to "no_prompt", "requested" to "1", "asked" to "0", "removed" to "0"),
            pixel.named(ClientToolPixels.ACCESS_REQUESTED).single().parameters,
        )
        assertEquals("unavailable", pixel.named(ClientToolPixels.ACCESS_RESULT).single().parameters["access"])
    }

    @Test
    fun whenBatchRequestNamesAskEveryTimeToolThenInvalidWithoutCounts() = runTest {
        enableRequestAccess()

        requestAccess("openTab")

        assertEquals(listOf(mapOf("decision" to "invalid")), pixel.fired.map { it.parameters })
    }

    @Test
    fun whenBatchAccessIsOffThenRequestIsUnavailable() = runTest {
        requestAccess("listOpenTabs")

        assertEquals(listOf(mapOf("decision" to "unavailable")), pixel.fired.map { it.parameters })
    }

    private suspend fun execute(
        name: String,
        version: Int = 1,
    ): JSONObject = executor().execute(request(name, version), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

    private fun TestScope.executeAsync(name: String) =
        async { execute(name) }.also { runCurrent() }

    private suspend fun requestAccess(vararg names: String): JSONObject {
        val input = JSONObject().put("capabilities", JSONArray(names.toList())).toString()
        return executor().execute(request("requestAccess", 1, input), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)
    }

    private fun enableRequestAccess() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        feature.requestAccess().setRawStoredState(Toggle.State(enable = true))
    }

    private fun request(
        name: String,
        version: Int = 1,
        input: String = "{}",
    ) = JSONObject("""{"callId":"call-1","capability":{"name":"$name","version":$version},"input":$input,"chatId":"$CHAT_ID"}""")

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/chat"
        const val CHAT_TAB_ID = "chat-tab"
        const val CHAT_ID = "chat-1"
        val ASK_MODE = ClientToolPermissionMode.Ask(promptTitle = 1, promptMessage = 2, name = 3)
    }
}
