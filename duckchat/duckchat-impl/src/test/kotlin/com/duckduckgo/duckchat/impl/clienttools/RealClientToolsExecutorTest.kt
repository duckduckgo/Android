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
import com.duckduckgo.dataclearing.api.plugin.ClearableData
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
    private val askTool = FakeClientTool(name = "askTool", permissionMode = ASK_MODE)
    private val otherAskTool = FakeClientTool(name = "otherAskTool", permissionMode = OTHER_ASK_MODE)

    private val store = FakeClientToolPermissionStore()
    private val prompt = FakeClientToolPermissionPrompt()
    private val chatGrants = ClientToolChatGrants()
    private val permissionGate = RealClientToolPermissionGate(appBuildConfig, feature, store, chatGrants, prompt)

    private fun executor(vararg tools: ClientTool = arrayOf(autoTool, askTool, otherAskTool)) = RealClientToolsExecutor(
        tools = FakeClientToolPluginPoint(*tools),
        permissionGate = permissionGate,
        duckAiHostProvider = FakeDuckAiHostProvider(),
        browserToolsFeature = feature,
    )

    @Test
    fun whenPageIsDuckAiAndToolsEnabledThenConfigAdvertisesEnabledTools() {
        askTool.enabled = false
        otherAskTool.enabled = false

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
        enableRequestAccess()
        autoTool.enabled = false
        askTool.enabled = false
        otherAskTool.enabled = false

        assertNull(executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR))
    }

    @Test
    fun whenDuckAiHostIsOverriddenThenOnlyThatHostGetsConfig() {
        val executor = RealClientToolsExecutor(
            tools = FakeClientToolPluginPoint(autoTool),
            permissionGate = permissionGate,
            duckAiHostProvider = FakeDuckAiHostProvider("dev.duck.ai"),
            browserToolsFeature = feature,
        )

        assertNull(executor.clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR))
        assertTrue(executor.clientToolsConfig("https://dev.duck.ai/chat", BrowserMode.REGULAR) != null)
    }

    @Test
    fun whenAutoToolSucceedsThenReplyCarriesValueAndContext() = runTest {
        val reply = executor().execute(request("autoTool", 2, input = """{"limit":3}"""), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals("""{"callId":"call-1","ok":true,"value":{"echo":{"limit":3}}}""", reply.toString())
        assertEquals(ClientToolCallContext(CHAT_TAB_ID, BrowserMode.REGULAR, CHAT_ID), autoTool.calls.single().second)
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
    fun whenChatIdIsInParamsThenCallCarriesIt() = runTest {
        executor().execute(request("autoTool", 2, chatId = "params-chat"), DUCK_AI_URL_WITH_CHAT, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals("params-chat", autoTool.calls.single().second.chatId)
    }

    @Test
    fun whenChatIdIsNotInParamsThenPageChatIdIsUsed() = runTest {
        executor().execute(request("autoTool", 2, chatId = null), DUCK_AI_URL_WITH_CHAT, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals(PAGE_CHAT_ID, autoTool.calls.single().second.chatId)
    }

    @Test
    fun whenNeitherParamsNorPageHaveChatIdThenCallHasNone() = runTest {
        executor().execute(request("autoTool", 2, chatId = null), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertNull(autoTool.calls.single().second.chatId)
    }

    @Test
    fun whenChatIdIsJsonNullThenPageChatIdIsUsed() = runTest {
        val params = JSONObject("""{"callId":"call-1","chatId":null,"capability":{"name":"autoTool","version":2},"input":{}}""")

        executor().execute(params, DUCK_AI_URL_WITH_CHAT, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertEquals(PAGE_CHAT_ID, autoTool.calls.single().second.chatId)
    }

    @Test
    fun whenChatIdIsInvalidThenInvalidArgumentsAndToolNotRun() = runTest {
        listOf("\"has space\"", "\"\"", "\"${"a".repeat(129)}\"", "5", "{}").forEach { chatId ->
            val params = JSONObject("""{"callId":"call-1","chatId":$chatId,"capability":{"name":"autoTool","version":2},"input":{}}""")

            assertFailure("invalid_arguments", executor().execute(params, DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR))
        }
        assertTrue(autoTool.calls.isEmpty())
    }

    @Test
    fun whenAutoToolRunsThenNoPromptIsShown() = runTest {
        executor().execute(request("autoTool", 2), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenAskToolCalledThenPromptAsksToRunThatToolInItsChatTab() = runTest {
        val reply = async { executeAskTool() }
        runCurrent()

        assertEquals(
            ClientToolPermissionRequest.RunTool(CHAT_TAB_ID, BrowserMode.REGULAR, AskingTool("askTool", ASK_MODE)),
            prompt.current.request,
        )
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        reply.await()
    }

    @Test
    fun whenAllowForChatThenToolRunsAndNothingIsStored() = runTest {
        val reply = async { executeAskTool() }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())

        assertEquals(true, reply.await().getBoolean("ok"))
        assertEquals(1, askTool.calls.size)
        assertTrue(store.choices.isEmpty())
    }

    @Test
    fun whenAllowedForChatThenSameChatRunsItAgainWithoutPrompt() = runTest {
        allowForChat("askTool", CHAT_ID)

        val second = executeAskTool()

        assertEquals(true, second.getBoolean("ok"))
        assertEquals(1, prompt.prompts.size)
        assertEquals(2, askTool.calls.size)
    }

    @Test
    fun whenAllowedForChatThenAnotherChatIsAskedAgain() = runTest {
        allowForChat("askTool", CHAT_ID)

        val other = async { executeAskTool(chatId = OTHER_CHAT_ID) }
        runCurrent()

        assertEquals(2, prompt.prompts.size)
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        assertFailure("denied", other.await())
    }

    @Test
    fun whenAllowedForChatThenAnotherToolInThatChatIsAskedAgain() = runTest {
        allowForChat("askTool", CHAT_ID)

        val other = async { executor().execute(request("otherAskTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        assertEquals(2, prompt.prompts.size)
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        assertFailure("denied", other.await())
    }

    @Test
    fun whenCallHasNoChatIdThenAllowForChatCoversThatCallOnly() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1, chatId = null), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertEquals(true, first.await().getBoolean("ok"))

        val second = async { executor.execute(request("askTool", 1, chatId = null), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        assertEquals(2, prompt.prompts.size)
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        assertFailure("denied", second.await())
    }

    @Test
    fun whenDontAllowThenDeniedNothingStoredAndNextCallAsksAgain() = runTest {
        val first = async { executeAskTool() }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        assertFailure("denied", first.await())

        val second = async { executeAskTool() }
        runCurrent()

        assertEquals(2, prompt.prompts.size)
        assertTrue(store.choices.isEmpty())
        assertTrue(askTool.calls.isEmpty())
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        second.await()
    }

    @Test
    fun whenAlwaysAllowIsStoredThenAskToolRunsWithoutPrompt() = runTest {
        store.choices["askTool"] = ClientToolPermission.ALWAYS_ALLOW

        assertEquals(true, executeAskTool().getBoolean("ok"))
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenNeverAllowIsStoredThenDeniedWithoutPromptEvenForAutoTool() = runTest {
        store.choices["askTool"] = ClientToolPermission.NEVER_ALLOW
        store.choices["autoTool"] = ClientToolPermission.NEVER_ALLOW

        assertFailure("denied", executeAskTool())
        assertFailure("denied", executor().execute(request("autoTool", 2), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR))
        assertTrue(prompt.prompts.isEmpty())
        assertTrue(askTool.calls.isEmpty())
        assertTrue(autoTool.calls.isEmpty())
    }

    @Test
    fun whenNeverAllowIsStoredThenItWinsOverChatGrant() = runTest {
        allowForChat("askTool", CHAT_ID)
        store.choices["askTool"] = ClientToolPermission.NEVER_ALLOW

        assertFailure("denied", executeAskTool())
    }

    @Test
    fun whenFireButtonClearsEverythingThenChatGrantsEnd() = runTest {
        allowForChat("askTool", CHAT_ID)

        ClientToolChatGrantsClearingPlugin(chatGrants).onClearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.REGULAR)))
        val next = async { executeAskTool() }
        runCurrent()

        assertEquals(2, prompt.prompts.size)
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        next.await()
    }

    @Test
    fun whenOneTabIsClearedThenChatGrantsStay() = runTest {
        allowForChat("askTool", CHAT_ID)

        ClientToolChatGrantsClearingPlugin(chatGrants).onClearData(setOf(ClearableData.Tabs.SingleForMode("other", BrowserMode.REGULAR)))

        assertEquals(true, executeAskTool().getBoolean("ok"))
        assertEquals(1, prompt.prompts.size)
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
    fun whenTwoCallsOfOneChatArriveTogetherThenOnlyOnePromptIsOpenAtATime() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1, chatId = CHAT_ID), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1, chatId = OTHER_CHAT_ID), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()
        assertEquals(1, prompt.prompts.size)

        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        runCurrent()
        assertEquals(2, prompt.prompts.size)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())

        assertEquals(true, first.await().getBoolean("ok"))
        assertEquals(true, second.await().getBoolean("ok"))
        assertEquals(1, prompt.maxOpenAtOnce)
    }

    @Test
    fun whenAnotherChatsPromptIsUnansweredThenThisChatStillGetsItsPrompt() = runTest {
        val executor = executor()
        val hiddenChat = async {
            executor.execute(request("askTool", 1, chatId = OTHER_CHAT_ID), DUCK_AI_URL, OTHER_CHAT_TAB_ID, BrowserMode.REGULAR)
        }
        runCurrent()
        val visibleChat = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        assertEquals(CHAT_TAB_ID, prompt.current.request.chatTabId)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertEquals(true, visibleChat.await().getBoolean("ok"))
        assertFalse(hiddenChat.isCompleted)
        hiddenChat.cancel()
    }

    @Test
    fun whenQueuedCallOfSameChatGetsItsTurnAfterAllowForChatThenItRunsWithoutPrompt() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())

        assertEquals(true, first.await().getBoolean("ok"))
        assertEquals(true, second.await().getBoolean("ok"))
        assertEquals(1, prompt.prompts.size)
        assertEquals(2, askTool.calls.size)
    }

    @Test
    fun whenQueuedCallGetsItsTurnAfterDontAllowThenItIsAskedItself() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()

        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        assertFailure("denied", first.await())
        runCurrent()

        assertEquals(2, prompt.prompts.size)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertEquals(true, second.await().getBoolean("ok"))
    }

    @Test
    fun whenCallWaitedInQueueThenThatTimeCountsAgainstItsDeadline() = runTest {
        val executor = executor()
        val first = async { executor.execute(request("askTool", 1, chatId = CHAT_ID), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val second = async { executor.execute(request("askTool", 1, chatId = OTHER_CHAT_ID), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        advanceTimeBy(100_000)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
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
        val otherChat = async {
            executor.execute(request("askTool", 1, chatId = OTHER_CHAT_ID), DUCK_AI_URL, OTHER_CHAT_TAB_ID, BrowserMode.REGULAR)
        }
        runCurrent()
        val firstPrompt = prompt.prompts.first()

        executor.cancelPendingCalls(CHAT_TAB_ID, contextual = false)
        runCurrent()

        assertFailure("cancelled", prompting.await())
        assertFailure("cancelled", queued.await())
        assertTrue(firstPrompt.withdrawn)
        assertFalse(otherChat.isCompleted)
        assertEquals(OTHER_CHAT_TAB_ID, prompt.current.request.chatTabId)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertEquals(true, otherChat.await().getBoolean("ok"))
        assertEquals(listOf(OTHER_CHAT_TAB_ID), askTool.calls.map { it.second.chatTabId })
    }

    @Test
    fun whenPageUnderContextualSheetReloadsThenTheSheetsPromptStaysOpen() = runTest {
        val executor = executor()
        val sheetCall = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR, contextual = true) }
        runCurrent()
        val sheetPrompt = prompt.current

        executor.cancelPendingCalls(CHAT_TAB_ID, contextual = false)
        runCurrent()

        assertFalse(sheetPrompt.withdrawn)
        sheetPrompt.answer(ClientToolPermissionAnswer.AllowForChat())
        assertEquals(true, sheetCall.await().getBoolean("ok"))
    }

    @Test
    fun whenContextualSheetGoesAwayThenItsPromptIsWithdrawn() = runTest {
        val executor = executor()
        val sheetCall = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR, contextual = true) }
        runCurrent()

        executor.cancelPendingCalls(CHAT_TAB_ID, contextual = true)
        runCurrent()

        assertFailure("cancelled", sheetCall.await())
        assertTrue(prompt.current.withdrawn)
        assertTrue(askTool.calls.isEmpty())
    }

    @Test
    fun whenToolIsTurnedOffWhilePromptIsOpenThenUnavailableOnceAnswered() = runTest {
        val reply = async { executeAskTool() }
        runCurrent()
        askTool.enabled = false

        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())

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
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)

        assertFailure("denied", reply.await())
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun whenRequestAccessFlagOffThenNotAdvertisedAndUnavailable() = runTest {
        feature.self().setRawStoredState(Toggle.State(enable = true))

        val config = executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR)
        val reply = requestAccess("askTool")

        assertFalse(config.toString().contains("requestAccess"))
        assertFailure("unavailable", reply)
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenRequestAccessFlagOnThenAdvertisedAfterTheTools() {
        enableRequestAccess()
        askTool.enabled = false
        otherAskTool.enabled = false

        val config = executor().clientToolsConfig(DUCK_AI_URL, BrowserMode.REGULAR)

        assertEquals(
            """{"protocolVersion":1,"capabilities":[{"name":"autoTool","version":2},{"name":"requestAccess","version":1}]}""",
            config.toString(),
        )
    }

    @Test
    fun whenRequestAccessVersionIsUnknownThenUnavailable() = runTest {
        enableRequestAccess()

        val reply = executor().execute(
            request("requestAccess", 2, input = """{"capabilities":["askTool"]}"""),
            DUCK_AI_URL,
            CHAT_TAB_ID,
            BrowserMode.REGULAR,
        )

        assertFailure("unavailable", reply)
    }

    @Test
    fun whenRequestAccessInputIsInvalidThenInvalidArgumentsWithoutPrompt() = runTest {
        enableRequestAccess()
        val invalidInputs = listOf(
            """{}""",
            """{"capabilities":[]}""",
            """{"capabilities":"askTool"}""",
            """{"capabilities":["askTool"],"extra":1}""",
            """{"capabilities":["askTool","askTool"]}""",
            """{"capabilities":[1]}""",
            """{"capabilities":[""]}""",
            """{"capabilities":["${"a".repeat(65)}"]}""",
            """{"capabilities":["requestAccess"]}""",
            """{"capabilities":["loadUserSkill"]}""",
            """{"capabilities":[${(1..13).joinToString(",") { "\"t$it\"" }}]}""",
        )

        invalidInputs.forEach { input ->
            assertFailure(
                "invalid_arguments",
                executor().execute(request("requestAccess", 1, input = input), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR),
            )
        }
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenRequestAccessAsksForTwelveUnknownToolsThenNoneIsGranted() = runTest {
        enableRequestAccess()
        val names = (1..12).map { "t$it" }

        val reply = requestAccess(*names.toTypedArray())

        assertAccess(reply, *names.map { it to "notGranted" }.toTypedArray())
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenRequestAccessHasNoChatIdThenInvalidArguments() = runTest {
        enableRequestAccess()

        val reply = executor().execute(
            request("requestAccess", 1, input = """{"capabilities":["askTool"]}""", chatId = null),
            DUCK_AI_URL,
            CHAT_TAB_ID,
            BrowserMode.REGULAR,
        )

        assertFailure("invalid_arguments", reply)
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenRequestAccessToolsAreAlreadyAllowedThenGrantedWithoutPrompt() = runTest {
        enableRequestAccess()
        store.choices["otherAskTool"] = ClientToolPermission.ALWAYS_ALLOW
        chatGrants.grant(CHAT_ID, listOf("askTool"))

        val reply = requestAccess("askTool", "autoTool", "otherAskTool")

        assertAccess(reply, "askTool" to "chat", "autoTool" to "default", "otherAskTool" to "always")
        assertTrue(prompt.prompts.isEmpty())
        assertTrue(askTool.calls.isEmpty() && autoTool.calls.isEmpty() && otherAskTool.calls.isEmpty())
    }

    @Test
    fun whenRequestAccessNamesNeverAllowedUnknownOrDisabledToolsThenTheyAreNotAsked() = runTest {
        enableRequestAccess()
        store.choices["askTool"] = ClientToolPermission.NEVER_ALLOW
        otherAskTool.enabled = false

        val reply = requestAccess("askTool", "missing", "otherAskTool", "autoTool")

        assertAccess(reply, "askTool" to "never", "missing" to "notGranted", "otherAskTool" to "notGranted", "autoTool" to "default")
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenRequestAccessAllowedForChatThenOnePromptGrantsAllInRequestedOrder() = runTest {
        enableRequestAccess()

        val reply = async { requestAccess("otherAskTool", "autoTool", "askTool") }
        runCurrent()

        assertEquals(
            ClientToolPermissionRequest.GrantAccess(
                CHAT_TAB_ID,
                BrowserMode.REGULAR,
                listOf(AskingTool("otherAskTool", OTHER_ASK_MODE), AskingTool("askTool", ASK_MODE)),
                toolsRemovable = false,
            ),
            prompt.current.request,
        )
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertAccess(reply.await(), "otherAskTool" to "chat", "autoTool" to "default", "askTool" to "chat")
        assertTrue(askTool.calls.isEmpty() && otherAskTool.calls.isEmpty())
        assertTrue(store.choices.isEmpty())

        assertEquals(true, executeAskTool().getBoolean("ok"))
        assertEquals(true, executor().execute(request("otherAskTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR).getBoolean("ok"))
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun whenRequestAccessUsesPageChatIdThenGrantsThatChat() = runTest {
        enableRequestAccess()
        val reply = async {
            executor().execute(
                request("requestAccess", 1, input = """{"capabilities":["askTool"]}""", chatId = null),
                DUCK_AI_URL_WITH_CHAT,
                CHAT_TAB_ID,
                BrowserMode.REGULAR,
            )
        }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertAccess(reply.await(), "askTool" to "chat")

        assertEquals(true, executeAskTool(chatId = PAGE_CHAT_ID).getBoolean("ok"))
        assertEquals(1, prompt.prompts.size)
    }

    @Test
    fun whenRequestAccessDontAllowThenDeniedAndNothingGranted() = runTest {
        enableRequestAccess()

        val reply = async { requestAccess("askTool") }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)

        assertFailure("denied", reply.await())
        assertFalse(chatGrants.isGranted(CHAT_ID, "askTool"))
        assertTrue(store.choices.isEmpty())
    }

    @Test
    fun whenRequestAccessUnansweredFor120SecondsThenCancelled() = runTest {
        enableRequestAccess()

        val reply = async { requestAccess("askTool") }
        advanceTimeBy(120_001)
        runCurrent()

        assertFailure("cancelled", reply.await())
        assertTrue(prompt.current.withdrawn)
    }

    @Test
    fun whenRequestAccessChatGoesAwayThenCancelled() = runTest {
        enableRequestAccess()
        val executor = executor()

        val reply =
            async {
                executor.execute(
                    request("requestAccess", 1, input = """{"capabilities":["askTool"]}"""),
                    DUCK_AI_URL,
                    CHAT_TAB_ID,
                    BrowserMode.REGULAR,
                )
            }
        runCurrent()
        executor.cancelPendingCalls(CHAT_TAB_ID, contextual = false)
        runCurrent()

        assertFailure("cancelled", reply.await())
        assertTrue(prompt.current.withdrawn)
    }

    @Test
    fun whenRequestAccessFromFireModeThenUnavailable() = runTest {
        enableRequestAccess()

        val reply = executor().execute(
            request("requestAccess", 1, input = """{"capabilities":["askTool"]}"""),
            DUCK_AI_URL,
            CHAT_TAB_ID,
            BrowserMode.FIRE,
        )

        assertFailure("unavailable", reply)
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenRequestAccessQueuesBehindToolPromptThenOnePromptAtATimeAndItAsksOnlyForWhatIsStillNeeded() = runTest {
        enableRequestAccess()
        val executor = executor()
        val toolCall = async { executor.execute(request("askTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        val access = async {
            executor.execute(
                request("requestAccess", 1, input = """{"capabilities":["askTool","otherAskTool"]}"""),
                DUCK_AI_URL,
                CHAT_TAB_ID,
                BrowserMode.REGULAR,
            )
        }
        runCurrent()
        assertEquals(1, prompt.prompts.size)

        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        runCurrent()

        assertEquals(true, toolCall.await().getBoolean("ok"))
        assertEquals(listOf(AskingTool("otherAskTool", OTHER_ASK_MODE)), (prompt.current.request as ClientToolPermissionRequest.GrantAccess).tools)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertAccess(access.await(), "askTool" to "chat", "otherAskTool" to "chat")
        assertEquals(1, prompt.maxOpenAtOnce)
    }

    @Test
    fun whenRemoveToolsEnabledThenPromptLetsUserRemoveTools() = runTest {
        enableRequestAccess()
        feature.requestAccessRemoveTools().setRawStoredState(Toggle.State(enable = true))

        val reply = async { requestAccess("askTool", "otherAskTool") }
        runCurrent()

        assertTrue((prompt.current.request as ClientToolPermissionRequest.GrantAccess).toolsRemovable)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        reply.await()
    }

    @Test
    fun whenAllowedWithToolsRemovedThenOnlyTheRestAreGrantedAndRemovedOnesAskAgain() = runTest {
        enableRequestAccess()
        feature.requestAccessRemoveTools().setRawStoredState(Toggle.State(enable = true))
        val executor = executor()

        val reply = async { requestAccess("otherAskTool", "autoTool", "askTool") }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat(removedTools = setOf("otherAskTool")))

        assertAccess(reply.await(), "otherAskTool" to "notGranted", "autoTool" to "default", "askTool" to "chat")
        assertTrue(chatGrants.isGranted(CHAT_ID, "askTool"))
        assertFalse(chatGrants.isGranted(CHAT_ID, "otherAskTool"))
        assertTrue(store.choices.isEmpty())

        val call = async { executor.execute(request("otherAskTool", 1), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()
        assertEquals(
            ClientToolPermissionRequest.RunTool(CHAT_TAB_ID, BrowserMode.REGULAR, AskingTool("otherAskTool", OTHER_ASK_MODE)),
            prompt.current.request,
        )
        prompt.current.answer(ClientToolPermissionAnswer.DontAllow)
        assertFailure("denied", call.await())

        val again = async { requestAccess("askTool", "otherAskTool") }
        runCurrent()
        assertEquals(listOf(AskingTool("otherAskTool", OTHER_ASK_MODE)), (prompt.current.request as ClientToolPermissionRequest.GrantAccess).tools)
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertAccess(again.await(), "askTool" to "chat", "otherAskTool" to "chat")
    }

    private fun enableRequestAccess() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        feature.requestAccess().setRawStoredState(Toggle.State(enable = true))
    }

    private suspend fun requestAccess(vararg names: String): JSONObject {
        val input = JSONObject().put("capabilities", JSONArray(names.toList())).toString()
        return executor().execute(request("requestAccess", 1, input = input), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)
    }

    private suspend fun TestScope.allowForChat(
        toolName: String,
        chatId: String,
    ) {
        val reply = async { executor().execute(request(toolName, 1, chatId = chatId), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR) }
        runCurrent()
        prompt.current.answer(ClientToolPermissionAnswer.AllowForChat())
        assertEquals(true, reply.await().getBoolean("ok"))
    }

    private suspend fun executeAskTool(chatId: String? = CHAT_ID): JSONObject =
        executor().execute(request("askTool", 1, chatId = chatId), DUCK_AI_URL, CHAT_TAB_ID, BrowserMode.REGULAR)

    private fun request(
        name: String,
        version: Int,
        input: String = "{}",
        chatId: String? = CHAT_ID,
    ): JSONObject {
        val params = JSONObject("""{"callId":"call-1","capability":{"name":"$name","version":$version},"input":$input}""")
        chatId?.let { params.put("chatId", it) }
        return params
    }

    private fun assertAccess(
        reply: JSONObject,
        vararg levels: Pair<String, String>,
    ) {
        val access = levels.joinToString(",") { (name, level) -> "\"$name\":\"$level\"" }
        assertEquals("""{"callId":"call-1","ok":true,"value":{"access":{$access}}}""", reply.toString())
    }

    private fun assertFailure(
        code: String,
        reply: JSONObject,
    ) {
        assertEquals("""{"callId":"call-1","ok":false,"error":{"code":"$code"}}""", reply.toString())
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/chat"
        const val PAGE_CHAT_ID = "page-chat"
        const val DUCK_AI_URL_WITH_CHAT = "https://duck.ai/?chatID=$PAGE_CHAT_ID"
        const val CHAT_TAB_ID = "chat-tab"
        const val OTHER_CHAT_TAB_ID = "other-chat-tab"
        const val CHAT_ID = "chat-1"
        const val OTHER_CHAT_ID = "chat-2"
        val ASK_MODE = ClientToolPermissionMode.Ask(promptTitle = 1, promptMessage = 2, name = 4)
        val OTHER_ASK_MODE = ClientToolPermissionMode.Ask(promptTitle = 6, promptMessage = 7, name = 9)
    }
}
