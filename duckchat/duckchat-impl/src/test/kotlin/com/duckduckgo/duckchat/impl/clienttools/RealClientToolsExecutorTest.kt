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
import com.duckduckgo.site.permissions.api.ClientToolPermission
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val store = FakeClientToolPermissionStore()
    private val prompt = FakeClientToolPermissionPrompt()
    private val permissionGate = RealClientToolPermissionGate(appBuildConfig, feature, store, prompt)

    private fun executor(vararg tools: ClientTool = arrayOf(autoTool, askTool)) = RealClientToolsExecutor(
        tools = FakeClientToolPluginPoint(*tools),
        permissionGate = permissionGate,
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
            permissionGate = permissionGate,
            duckAiHostProvider = FakeDuckAiHostProvider("dev.duck.ai"),
        )

        assertNull(executor.clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR))
        assertTrue(executor.clientToolsConfig("https://dev.duck.ai/chat", BrowserMode.REGULAR) != null)
    }

    @Test
    fun whenAutoToolSucceedsThenReplyCarriesValueAndContext() = runTest {
        val reply = executor().execute(request("autoTool", 2, input = """{"limit":3}"""), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals("""{"callId":"call-1","ok":true,"value":{"echo":{"limit":3}}}""", reply.toString())
        assertEquals(ClientToolCallContext(CHAT_TAB_ID, BrowserMode.REGULAR, DUCK_AI_ORIGIN), autoTool.calls.single().second)
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

    @Test
    fun whenAutoToolRunsThenNoPromptIsShown() = runTest {
        executor().execute(request("autoTool", 2), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenAskToolCalledThenPromptCarriesOriginToolAndChat() = runTest {
        val reply = async { executeAskTool() }
        runCurrent()

        assertEquals(ClientToolPermissionRequest(DUCK_AI_ORIGIN, "askTool", CHAT_TAB_ID), prompt.current.request)
        prompt.current.answer(ClientToolPermissionAnswer.ALLOW_ONCE)
        reply.await()
    }

    @Test
    fun whenAllowOnceThenToolRunsAndNothingIsStored() = runTest {
        val reply = async { executeAskTool() }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.ALLOW_ONCE)

        assertEquals(true, reply.await().getBoolean("ok"))
        assertEquals(1, askTool.calls.size)
        assertTrue(store.choices.isEmpty())
    }

    @Test
    fun whenAlwaysThenToolRunsAndNextCallSkipsPrompt() = runTest {
        val first = async { executeAskTool() }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.ALWAYS)
        first.await()

        val second = executeAskTool()

        assertEquals(true, second.getBoolean("ok"))
        assertEquals(1, prompt.prompts.size)
        assertEquals(2, askTool.calls.size)
        assertEquals(ClientToolPermission.ALWAYS_ALLOW, store.choices[DUCK_AI_ORIGIN to "askTool"])
    }

    @Test
    fun whenNeverThenDeniedAndNextCallDeniedWithoutPrompt() = runTest {
        val first = async { executeAskTool() }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.NEVER)

        assertFailure("denied", first.await())
        assertFailure("denied", executeAskTool())
        assertEquals(1, prompt.prompts.size)
        assertTrue(askTool.calls.isEmpty())
        assertEquals(ClientToolPermission.NEVER_ALLOW, store.choices[DUCK_AI_ORIGIN to "askTool"])
    }

    @Test
    fun whenPromptDismissedThenCancelledAndNothingStored() = runTest {
        val reply = async { executeAskTool() }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.DISMISSED)

        assertFailure("cancelled", reply.await())
        assertTrue(askTool.calls.isEmpty())
        assertTrue(store.choices.isEmpty())
    }

    @Test
    fun whenNoAnswerWithin120SecondsThenCancelledAndPromptWithdrawn() = runTest {
        val reply = async { executeAskTool() }
        advanceTimeBy(119_999)
        assertFalse(reply.isCompleted)

        advanceTimeBy(1)
        runCurrent()

        assertFailure("cancelled", reply.await())
        assertTrue(prompt.current.withdrawn)
        assertTrue(askTool.calls.isEmpty())
    }

    @Test
    fun whenTwoCallsArriveTogetherThenOnlyOnePromptIsOpenAtATime() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1), DUCK_AI_URL, OTHER_CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()
        assertEquals(1, prompt.prompts.size)

        prompt.current.answer(ClientToolPermissionAnswer.ALLOW_ONCE)
        runCurrent()
        assertEquals(2, prompt.prompts.size)
        prompt.current.answer(ClientToolPermissionAnswer.ALLOW_ONCE)

        assertEquals(true, first.await().getBoolean("ok"))
        assertEquals(true, second.await().getBoolean("ok"))
        assertEquals(1, prompt.maxOpenAtOnce)
    }

    @Test
    fun whenQueuedCallGetsItsTurnAfterAlwaysThenItRunsWithoutPrompt() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        prompt.current.answer(ClientToolPermissionAnswer.ALWAYS)

        assertEquals(true, first.await().getBoolean("ok"))
        assertEquals(true, second.await().getBoolean("ok"))
        assertEquals(1, prompt.prompts.size)
        assertEquals(2, askTool.calls.size)
    }

    @Test
    fun whenQueuedCallGetsItsTurnAfterNeverThenItIsDeniedWithoutPrompt() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        prompt.current.answer(ClientToolPermissionAnswer.NEVER)

        assertFailure("denied", first.await())
        assertFailure("denied", second.await())
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun whenCallWaitedInQueueThenThatTimeCountsAgainstItsDeadline() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        advanceTimeBy(100_000)
        prompt.current.answer(ClientToolPermissionAnswer.ALLOW_ONCE)
        first.await()
        runCurrent()
        val secondPrompt = prompt.current

        advanceTimeBy(20_000)
        runCurrent()

        assertFailure("cancelled", second.await())
        assertTrue(secondPrompt.withdrawn)
        assertEquals(1, askTool.calls.size)
    }

    @Test
    fun whenChatGoesAwayThenItsWaitingCallsAreCancelledAndOtherChatsKeepWaiting() = runTest {
        val executor = executor()
        val prompting = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val queued = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val otherChat = async { executor.execute(request("askTool", 1), DUCK_AI_URL, OTHER_CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()
        val firstPrompt = prompt.current

        executor.cancelPendingCalls(CHAT_TAB_ID)
        runCurrent()

        assertFailure("cancelled", prompting.await())
        assertFailure("cancelled", queued.await())
        assertTrue(firstPrompt.withdrawn)
        assertFalse(otherChat.isCompleted)
        assertEquals(OTHER_CHAT_TAB_ID, prompt.current.request.chatTabId)
        prompt.current.answer(ClientToolPermissionAnswer.ALLOW_ONCE)
        assertEquals(true, otherChat.await().getBoolean("ok"))
        assertEquals(listOf(OTHER_CHAT_TAB_ID), askTool.calls.map { it.second.chatTabId })
    }

    @Test
    fun whenToolIsTurnedOffWhilePromptIsOpenThenUnavailableOnceAnswered() = runTest {
        val reply = async { executeAskTool() }
        runCurrent()
        askTool.enabled = false

        prompt.current.answer(ClientToolPermissionAnswer.ALLOW_ONCE)

        assertFailure("unavailable", reply.await())
        assertTrue(askTool.calls.isEmpty())
    }

    @Test
    fun whenAskToolCalledFromFireModeThenUnavailableWithoutPromptOrStoring() = runTest {
        val reply = executor().execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.FIRE)

        assertFailure("unavailable", reply)
        assertTrue(prompt.prompts.isEmpty())
        assertTrue(store.choices.isEmpty())
    }

    @Test
    fun whenDevSkipFlagOnInternalBuildThenAskToolRunsWithoutPromptOrStoring() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        feature.devSkipPermissionPrompt().setRawStoredState(Toggle.State(enable = true))

        val reply = executeAskTool()

        assertEquals(true, reply.getBoolean("ok"))
        assertTrue(prompt.prompts.isEmpty())
        assertTrue(store.choices.isEmpty())
    }

    @Test
    fun whenDevSkipFlagOnPlayBuildThenPromptIsStillShown() = runTest {
        feature.devSkipPermissionPrompt().setRawStoredState(Toggle.State(enable = true))

        val reply = async { executeAskTool() }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.DISMISSED)

        assertFailure("cancelled", reply.await())
        assertEquals(1, prompt.prompts.size)
    }

    private suspend fun executeAskTool(): JSONObject =
        executor().execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

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
        const val DUCK_AI_ORIGIN = "https://duck.ai"
        const val CHAT_TAB_ID = "chat-tab"
        const val OTHER_CHAT_TAB_ID = "other-chat-tab"
    }
}
