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

package com.duckduckgo.duckchat.impl.clienttools.settings

import app.cash.turbine.test
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.clienttools.AiChatBrowserToolsFeature
import com.duckduckgo.duckchat.impl.clienttools.ClientToolCallContext
import com.duckduckgo.duckchat.impl.clienttools.ClientToolChatGrants
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermission
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermissionDecision
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermissionMode
import com.duckduckgo.duckchat.impl.clienttools.ClientToolPermissionRequest
import com.duckduckgo.duckchat.impl.clienttools.ClientToolSettingsListing
import com.duckduckgo.duckchat.impl.clienttools.ClientToolSettingsSection
import com.duckduckgo.duckchat.impl.clienttools.FakeClientTool
import com.duckduckgo.duckchat.impl.clienttools.FakeClientToolPermissionPrompt
import com.duckduckgo.duckchat.impl.clienttools.FakeClientToolPermissionStore
import com.duckduckgo.duckchat.impl.clienttools.FakeClientToolPluginPoint
import com.duckduckgo.duckchat.impl.clienttools.RealClientToolPermissionGate
import com.duckduckgo.duckchat.impl.clienttools.settings.BrowserAccessChoice.ALWAYS_ALLOW
import com.duckduckgo.duckchat.impl.clienttools.settings.BrowserAccessChoice.ALWAYS_ASK
import com.duckduckgo.duckchat.impl.clienttools.settings.BrowserAccessChoice.NEVER_ALLOW
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class BrowserAccessSettingsViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val store = FakeClientToolPermissionStore()
    private val chatGrants = ClientToolChatGrants()
    private val prompt = FakeClientToolPermissionPrompt()
    private val appBuildConfig: AppBuildConfig = mock {
        on { flavor } doReturn BuildFlavor.PLAY
    }
    private val gate = RealClientToolPermissionGate(
        appBuildConfig,
        FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java),
        store,
        chatGrants,
        prompt,
    )

    private val listOpenTabs = tool("listOpenTabs", ClientToolSettingsSection.SEE, ClientToolPermissionMode.Ask(1, 2, 3))
    private val searchBookmarks = tool("searchBookmarks", ClientToolSettingsSection.SEE, ClientToolPermissionMode.Ask(1, 2, 3))
    private val switchToTab = tool("switchToTab", ClientToolSettingsSection.DO, ClientToolPermissionMode.Auto)
    private val openTab = tool("openTab", ClientToolSettingsSection.DO, ClientToolPermissionMode.AskEveryTime(1, 2))

    @Test
    fun whenToolsLoadThenGroupedBySectionInDisplayOrder() = runTest {
        val testee = testee(switchToTab, searchBookmarks, openTab, listOpenTabs)

        val state = testee.viewState.value
        assertEquals(listOf("listOpenTabs", "searchBookmarks"), state.seeTools.map { it.toolName })
        assertEquals(listOf("openTab", "switchToTab"), state.doTools.map { it.toolName })
    }

    @Test
    fun whenToolDisabledThenNotListed() = runTest {
        searchBookmarks.enabled = false

        val testee = testee(listOpenTabs, searchBookmarks)

        assertEquals(listOf("listOpenTabs"), testee.viewState.value.seeTools.map { it.toolName })
    }

    @Test
    fun whenNothingStoredThenEachToolShowsItsModeDefault() = runTest {
        val testee = testee(listOpenTabs, switchToTab, openTab)

        assertEquals(ALWAYS_ASK, testee.row("listOpenTabs").choice)
        assertEquals(ALWAYS_ALLOW, testee.row("switchToTab").choice)
        assertEquals(ALWAYS_ASK, testee.row("openTab").choice)
        assertFalse(testee.viewState.value.isResetAllEnabled)
    }

    @Test
    fun whenToolsLoadThenOptionsMatchWhatTheGateHonours() = runTest {
        val testee = testee(listOpenTabs, switchToTab, openTab)

        assertEquals(listOf(ALWAYS_ALLOW, ALWAYS_ASK, NEVER_ALLOW), testee.row("listOpenTabs").options)
        assertEquals(listOf(ALWAYS_ALLOW, NEVER_ALLOW), testee.row("switchToTab").options)
        assertEquals(listOf(ALWAYS_ASK, NEVER_ALLOW), testee.row("openTab").options)
    }

    @Test
    fun whenAlwaysAllowStoredForAskEveryTimeToolThenShowsAlwaysAsk() = runTest {
        store.choices["openTab"] = ClientToolPermission.ALWAYS_ALLOW

        val testee = testee(openTab)

        assertEquals(ALWAYS_ASK, testee.row("openTab").choice)
    }

    @Test
    fun whenNonDefaultChoiceSelectedThenStoredAndShown() = runTest {
        val testee = testee(listOpenTabs)

        testee.onChoiceSelected("listOpenTabs", ALWAYS_ALLOW)

        assertEquals(ClientToolPermission.ALWAYS_ALLOW, store.choices["listOpenTabs"])
        assertEquals(ALWAYS_ALLOW, testee.row("listOpenTabs").choice)
        assertTrue(testee.viewState.value.isResetAllEnabled)
    }

    @Test
    fun whenDefaultChoiceSelectedThenToolChoiceCleared() = runTest {
        store.choices["listOpenTabs"] = ClientToolPermission.NEVER_ALLOW
        store.choices["switchToTab"] = ClientToolPermission.NEVER_ALLOW
        val testee = testee(listOpenTabs, switchToTab)

        testee.onChoiceSelected("listOpenTabs", ALWAYS_ASK)

        assertEquals(setOf("switchToTab"), store.choices.keys)
        assertEquals(ALWAYS_ASK, testee.row("listOpenTabs").choice)
    }

    @Test
    fun whenAutoToolSetBackToAlwaysAllowThenChoiceCleared() = runTest {
        store.choices["switchToTab"] = ClientToolPermission.NEVER_ALLOW
        val testee = testee(switchToTab)

        testee.onChoiceSelected("switchToTab", ALWAYS_ALLOW)

        assertTrue(store.choices.isEmpty())
    }

    @Test
    fun whenResetAllConfirmedThenEveryChoiceCleared() = runTest {
        store.choices["listOpenTabs"] = ClientToolPermission.ALWAYS_ALLOW
        store.choices["openTab"] = ClientToolPermission.NEVER_ALLOW
        val testee = testee(listOpenTabs, openTab)
        assertTrue(testee.viewState.value.isResetAllEnabled)

        testee.onResetAllConfirmed()

        assertTrue(store.choices.isEmpty())
        assertFalse(testee.viewState.value.isResetAllEnabled)
    }

    @Test
    fun whenChatGrantedToolSetToAlwaysAskThenNextCallInThatChatPrompts() = runTest {
        val testee = testee(listOpenTabs)
        chatGrants.grant(CHAT_ID, listOf("listOpenTabs"))
        assertEquals(ClientToolPermissionDecision.ALLOWED, gate.decide(listOpenTabs, callContext, null))

        testee.onChoiceSelected("listOpenTabs", ALWAYS_ASK)

        assertPrompts(listOpenTabs)
    }

    @Test
    fun whenOneToolChoiceChangedThenOtherToolsKeepTheirChatGrants() = runTest {
        val testee = testee(listOpenTabs, searchBookmarks)
        chatGrants.grant(CHAT_ID, listOf("listOpenTabs", "searchBookmarks"))

        testee.onChoiceSelected("listOpenTabs", NEVER_ALLOW)

        assertEquals(ClientToolPermissionDecision.ALLOWED, gate.decide(searchBookmarks, callContext, null))
        assertTrue(prompt.prompts.isEmpty())
    }

    @Test
    fun whenResetAllConfirmedThenEveryChatGrantedToolPromptsAgain() = runTest {
        store.choices["openTab"] = ClientToolPermission.NEVER_ALLOW
        val testee = testee(listOpenTabs, searchBookmarks, openTab)
        chatGrants.grant(CHAT_ID, listOf("listOpenTabs", "searchBookmarks"))

        testee.onResetAllConfirmed()

        assertPrompts(listOpenTabs)
        assertPrompts(searchBookmarks)
    }

    @Test
    fun whenToolClickedThenShowsItsChoices() = runTest {
        val testee = testee(listOpenTabs)

        testee.commands.test {
            testee.onToolClicked("listOpenTabs")

            assertEquals(BrowserAccessSettingsViewModel.Command.ShowChoices(testee.row("listOpenTabs")), awaitItem())
        }
    }

    @Test
    fun whenResetAllClickedThenAsksForConfirmation() = runTest {
        val testee = testee(listOpenTabs)

        testee.commands.test {
            testee.onResetAllClicked()

            assertEquals(BrowserAccessSettingsViewModel.Command.ConfirmResetAll, awaitItem())
        }
    }

    private fun TestScope.assertPrompts(tool: FakeClientTool) {
        val decision = async { gate.decide(tool, callContext, null) }
        runCurrent()

        assertEquals(tool.name, (prompt.current.request as ClientToolPermissionRequest.RunTool).tool.name)
        assertFalse(decision.isCompleted)
        decision.cancel()
        runCurrent()
    }

    private fun TestScope.testee(vararg tools: FakeClientTool) = BrowserAccessSettingsViewModel(
        tools = FakeClientToolPluginPoint(*tools),
        permissionStore = store,
        chatGrants = chatGrants,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    ).also { viewModel -> backgroundScope.launch(coroutineRule.testDispatcher) { viewModel.viewState.collect() } }

    private fun BrowserAccessSettingsViewModel.row(toolName: String) =
        (viewState.value.seeTools + viewState.value.doTools).single { it.toolName == toolName }

    private fun tool(
        name: String,
        section: ClientToolSettingsSection,
        mode: ClientToolPermissionMode,
    ) = FakeClientTool(
        name = name,
        permissionMode = mode,
        settingsListing = ClientToolSettingsListing(section, name = 0, description = 0, icon = 0),
    )

    private companion object {
        const val CHAT_ID = "chat-1"
        val callContext = ClientToolCallContext(chatTabId = "tab-1", browserMode = BrowserMode.REGULAR, chatId = CHAT_ID)
    }
}
