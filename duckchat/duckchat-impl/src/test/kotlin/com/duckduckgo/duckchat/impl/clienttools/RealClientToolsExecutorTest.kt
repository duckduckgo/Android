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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealClientToolsExecutorTest {

    private val appBuildConfig: AppBuildConfig = mock {
        on { flavor } doReturn BuildFlavor.PLAY
    }
    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val autoTool = FakeClientTool(name = "autoTool", version = 2)
    private val askTool = FakeClientTool(name = "askTool", permissionMode = ClientToolPermissionMode.ASK)

    private fun executor(vararg tools: ClientTool = arrayOf(autoTool, askTool)) = RealClientToolsExecutor(
        tools = FakeClientToolPluginPoint(*tools),
        permissionGate = PromptlessClientToolPermissionGate(appBuildConfig, feature),
        duckAiHostProvider = FakeDuckAiHostProvider(),
    )

    @Test
    fun whenPageIsDuckAiAndToolsEnabledThenConfigAdvertisesEnabledTools() {
        askTool.enabled = false

        val config = executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR)

        assertEquals("""{"protocolVersion":1,"capabilities":[{"name":"autoTool","version":2}]}""", config.toString())
    }

    @Test
    fun whenPageIsDuckDuckGoThenConfigIsOmitted() {
        assertNull(executor().clientToolsConfig("https://duckduckgo.com/?q=test&ia=chat&duckai=1", BrowserMode.REGULAR))
    }

    @Test
    fun whenPageUrlIsMissingThenConfigIsOmitted() {
        assertNull(executor().clientToolsConfig(null, BrowserMode.REGULAR))
    }

    @Test
    fun whenFireModeThenConfigIsOmitted() {
        assertNull(executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.FIRE))
    }

    @Test
    fun whenNoToolIsEnabledThenConfigIsOmitted() {
        autoTool.enabled = false
        askTool.enabled = false

        assertNull(executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenDuckAiHostIsOverriddenThenOnlyThatHostGetsConfig() {
        val executor = RealClientToolsExecutor(
            tools = FakeClientToolPluginPoint(autoTool),
            permissionGate = PromptlessClientToolPermissionGate(appBuildConfig, feature),
            duckAiHostProvider = FakeDuckAiHostProvider("dev.duck.ai"),
        )

        assertNull(executor.clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR))
        assertTrue(executor.clientToolsConfig("https://dev.duck.ai/chat", BrowserMode.REGULAR) != null)
    }

    @Test
    fun whenAutoToolSucceedsThenReplyCarriesValueAndContext() = runTest {
        val reply = executor().execute(request("autoTool", 2, input = """{"limit":3}"""), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals("""{"callId":"call-1","ok":true,"value":{"echo":{"limit":3}}}""", reply.toString())
        assertEquals(ClientToolCallContext(CHAT_TAB_ID, BrowserMode.REGULAR), autoTool.calls.single().second)
    }

    @Test
    fun whenToolIsUnknownThenUnavailable() = runTest {
        val reply = executor().execute(request("missing", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("unavailable", reply)
    }

    @Test
    fun whenVersionDoesNotMatchAdvertisedThenUnavailableAndToolNotRun() = runTest {
        val reply = executor().execute(request("autoTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("unavailable", reply)
        assertTrue(autoTool.calls.isEmpty())
    }

    @Test
    fun whenToolIsDisabledThenUnavailableAndToolNotRun() = runTest {
        autoTool.enabled = false

        val reply = executor().execute(request("autoTool", 2), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("unavailable", reply)
        assertTrue(autoTool.calls.isEmpty())
    }

    @Test
    fun whenCalledFromFireModeThenUnavailableAndToolNotRun() = runTest {
        val reply = executor().execute(request("autoTool", 2), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.FIRE)

        assertFailure("unavailable", reply)
        assertTrue(autoTool.calls.isEmpty())
    }

    @Test
    fun whenCalledFromNonDuckAiPageThenUnavailableAndToolNotRun() = runTest {
        val reply = executor().execute(request("autoTool", 2), "https://duckduckgo.com/", CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("unavailable", reply)
        assertTrue(autoTool.calls.isEmpty())
    }

    @Test
    fun whenCapabilityIsMissingThenInvalidArgumentsEchoingCallId() = runTest {
        val params = JSONObject("""{"callId":"call-1","input":{}}""")

        val reply = executor().execute(params, DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("invalid_arguments", reply)
    }

    @Test
    fun whenVersionIsFractionalThenInvalidArguments() = runTest {
        val params = JSONObject("""{"callId":"call-1","capability":{"name":"autoTool","version":1.5},"input":{}}""")

        assertFailure("invalid_arguments", executor().execute(params, DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR))
    }

    @Test
    fun whenInputIsMissingThenInvalidArguments() = runTest {
        val params = JSONObject("""{"callId":"call-1","capability":{"name":"autoTool","version":2}}""")

        assertFailure("invalid_arguments", executor().execute(params, DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR))
        assertTrue(autoTool.calls.isEmpty())
    }

    @Test
    fun whenCallIdIsMissingThenInvalidArgumentsWithoutCallId() = runTest {
        val params = JSONObject("""{"capability":{"name":"autoTool","version":2},"input":{}}""")

        val reply = executor().execute(params, DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals("""{"ok":false,"error":{"code":"invalid_arguments"}}""", reply.toString())
    }

    @Test
    fun whenParamsAreNullThenInvalidArguments() = runTest {
        val reply = executor().execute(null, DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals("""{"ok":false,"error":{"code":"invalid_arguments"}}""", reply.toString())
    }

    @Test
    fun whenAskToolOnPlayBuildThenDenied() = runTest {
        feature.devSkipPermissionPrompt().setRawStoredState(Toggle.State(enable = true))

        val reply = executor().execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("denied", reply)
        assertTrue(askTool.calls.isEmpty())
    }

    @Test
    fun whenAskToolOnInternalBuildWithoutDevFlagThenDenied() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val reply = executor().execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("denied", reply)
        assertTrue(askTool.calls.isEmpty())
    }

    @Test
    fun whenAskToolOnInternalBuildWithDevFlagThenRuns() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        feature.devSkipPermissionPrompt().setRawStoredState(Toggle.State(enable = true))

        val reply = executor().execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals(true, reply.getBoolean("ok"))
        assertEquals(1, askTool.calls.size)
    }

    @Test
    fun whenAskToolInputIsInvalidThenInvalidArgumentsBeforePermission() = runTest {
        askTool.validInput = { false }

        val reply = executor().execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("invalid_arguments", reply)
        assertTrue(askTool.calls.isEmpty())
    }

    @Test
    fun whenToolThrowsThenExecutionFailed() = runTest {
        autoTool.result = { throw IllegalStateException("boom") }

        val reply = executor().execute(request("autoTool", 2), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("execution_failed", reply)
    }

    @Test
    fun whenToolReturnsFailureThenItsCodeIsSent() = runTest {
        autoTool.result = { ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND) }

        val reply = executor().execute(request("autoTool", 2), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertFailure("not_found", reply)
    }

    private fun request(
        name: String,
        version: Int,
        input: String = "{}",
    ) = JSONObject("""{"callId":"call-1","capability":{"name":"$name","version":$version},"input":$input}""")

    private fun assertFailure(
        code: String,
        reply: JSONObject,
    ) {
        assertEquals("""{"callId":"call-1","ok":false,"error":{"code":"$code"}}""", reply.toString())
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/chat"
        const val CHAT_TAB_ID = "chat-tab"
    }
}
