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

package com.duckduckgo.app.tabs.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionConfig
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionConfigProvider
import com.duckduckgo.app.browser.tabs.transition.RealPendingNewTabTransitions
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.ui.TabSwitcherViewModel.Command
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/**
 * Runs the real view model, TabSwitcherNewTabTransition and TabSwitcherActivity.processCommand on a partial mock of the activity, so a tab
 * operation's command reaches the transition the way it does in the app.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = Application::class)
class TabSwitcherNewTabTransitionFlowTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val repository: TabRepository = mock {
        on { flowTabs } doAnswer { flowOf(emptyList()) }
    }
    private val duckChat: DuckChat = mock()
    private var swiping = true

    // Each request reads the config once; tests that need to decide which command arrives first hold the reads.
    private val configReads = ArrayDeque<CompletableDeferred<Unit>>()
    private val configProvider = object : NewTabTransitionConfigProvider {
        override suspend fun enabledConfig(): NewTabTransitionConfig {
            configReads.removeFirstOrNull()?.await()
            return NewTabTransitionConfig.DEFAULT
        }
    }
    private val pictures = mutableListOf<CompletableDeferred<Bitmap?>>()
    private val pending = RealPendingNewTabTransitions(mock(), coroutineRule.testScope, coroutineRule.testDispatcherProvider)
    private val newTabTransition = TabSwitcherNewTabTransition(coroutineRule.testScope, pending) {
        CompletableDeferred<Bitmap?>().also { pictures += it }
    }.apply { enabled = true }
    private val activity: TabSwitcherActivity = mock(defaultAnswer = CALLS_REAL_METHODS)
    private val commands = mutableListOf<Command>()
    private val finishes = mutableListOf<String>()
    private val processCommand = TabSwitcherActivity::class.java
        .getDeclaredMethod("processCommand", Command::class.java)
        .apply { isAccessible = true }
    private val observer = Observer<Command> { command ->
        commands += command
        processCommand.invoke(activity, command)
    }
    private lateinit var viewModel: TabSwitcherViewModel

    @After
    fun tearDown() {
        viewModel.command.removeObserver(observer)
        viewModel.viewModelScope.cancel()
        newTabTransition.onClose()
    }

    @Test
    fun olderReuseCannotCloseTheTabManagerBeforeTheLatestTapsCommand() = checkOlderReuse(latestCommandFirst = false)

    @Test
    fun olderReuseCannotCancelTheLatestTapsPictureWait() = checkOlderReuse(latestCommandFirst = true)

    @Test
    fun selectingAnExistingTabDropsTheNewTabWaitingForItsPicture() = coroutineRule.testScope.runTest {
        startTabManager()
        viewModel.onNewTabRequested(newTabRequestId = newTabTransition.onNewTabTap())
        assertTrue(commands.single() is Command.CloseToNewTab)

        viewModel.onTabSelected(EXISTING_TAB)
        pictures.single().complete(mock())

        assertNull(pending.tabManagerTabId())
        assertEquals(listOf("plain"), finishes)
    }

    @Test
    fun selectingAnExistingTabIgnoresTheNewTabsLaterCommand() = coroutineRule.testScope.runTest {
        val configRead = CompletableDeferred<Unit>().also { configReads += it }
        startTabManager()
        viewModel.onNewTabRequested(newTabRequestId = newTabTransition.onNewTabTap())
        assertTrue(commands.isEmpty())

        viewModel.onTabSelected(EXISTING_TAB)
        configRead.complete(Unit)
        pictures.single().complete(mock())

        assertNull(pending.tabManagerTabId())
        assertEquals(listOf("plain"), finishes)
    }

    @Test
    fun selectingAnExistingTabRemovesTheNewTabsHandedOverPicture() = coroutineRule.testScope.runTest {
        startTabManager()
        viewModel.onNewTabRequested(newTabRequestId = newTabTransition.onNewTabTap())
        pictures.single().complete(mock())
        assertEquals(NEW_TAB, pending.tabManagerTabId())

        viewModel.onTabSelected(EXISTING_TAB)

        assertNull(pending.tabManagerTabId())
    }

    @Test
    fun closingDropsTheNewTabWaitingForItsPicture() = coroutineRule.testScope.runTest {
        startTabManager()
        viewModel.onNewTabRequested(newTabRequestId = newTabTransition.onNewTabTap())

        processCommand.invoke(activity, Command.Close)
        pictures.single().complete(mock())

        assertNull(pending.tabManagerTabId())
        assertEquals(listOf("plain"), finishes)
    }

    @Test
    fun duckAiButtonReusingAnEmptyChatClosesWithoutTheTransition() = checkDuckAiButtonReusesEmptyChat()

    @Test
    fun duckAiButtonReusingAnEmptyChatClosesWithoutTheTransitionWithSwipingOff() {
        swiping = false
        checkDuckAiButtonReusesEmptyChat()
    }

    private fun checkOlderReuse(latestCommandFirst: Boolean) = coroutineRule.testScope.runTest {
        val reuseConfigRead = CompletableDeferred<Unit>().also { configReads += it }
        val newChatConfigRead = CompletableDeferred<Unit>().also { configReads += it }
        startTabManager()
        val blankTab = TabSwitcherItem.Tab.NormalTab(TabEntity(BLANK_TAB), false)
        TabSwitcherViewModel::class.java.getDeclaredField("viewState").apply { isAccessible = true }.set(
            viewModel,
            MutableStateFlow(TabSwitcherViewModel.ViewState(tabItems = TabSwitcherViewModel.TabItems.Loaded(listOf(blankTab)))),
        )

        viewModel.onNewTabRequested(newTabRequestId = newTabTransition.onNewTabTap())
        viewModel.onDuckAiChatSelected("https://duck.ai/?chatID=chat", newTabRequestId = newTabTransition.onNewTabTap())
        if (latestCommandFirst) newChatConfigRead.complete(Unit)
        reuseConfigRead.complete(Unit)
        if (!latestCommandFirst) newChatConfigRead.complete(Unit)

        assertFalse("The latest picture isn't ready, so the older reuse must not close. Commands: $commands", "plain" in finishes)

        pictures[1].complete(mock())
        assertEquals(NEW_TAB, pending.tabManagerTabId())
        assertEquals(listOf("animated"), finishes)
    }

    private fun checkDuckAiButtonReusesEmptyChat() = coroutineRule.testScope.runTest {
        val url = "https://duck.ai/?chatID="
        whenever(repository.getTabs()).thenReturn(listOf(TabEntity(EMPTY_CHAT, url = url)))
        whenever(duckChat.isDuckChatUrl(Uri.parse(url))).thenReturn(true)
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        startTabManager()
        assertFalse(viewModel.viewState.value.isDuckAiChatsMenuEnabled)

        val requestId = newTabTransition.onDuckAiButtonTap(chatsMenuEnabled = false)
        assertNotNull(requestId)
        viewModel.onDuckAIButtonClicked(newTabRequestId = requestId)

        verify(repository).select(EMPTY_CHAT)
        verify(repository, never()).add(anyOrNull(), any())
        verify(duckChat, never()).reportDuckChatEntry(any(), any(), any(), any())
        assertEquals(listOf(Command.CloseToNewTab(requestId!!, EMPTY_CHAT, motion = null)), commands)
        assertEquals(listOf("plain"), finishes)

        pictures.single().complete(mock())
        assertNull(pending.tabManagerTabId())
        assertEquals(listOf("plain"), finishes)
    }

    private suspend fun startTabManager() {
        whenever(repository.add(anyOrNull(), any())).thenReturn(NEW_TAB)
        val repositories: BrowserModeDataProvider<TabRepository> = mock()
        whenever(repositories.forMode(BrowserMode.REGULAR)).thenReturn(repository)
        val duckAiFeature: DuckAiFeatureState = mock()
        whenever(duckAiFeature.showOmnibarShortcutOnNtpAndOnFocus).thenReturn(MutableStateFlow(false))
        val swipingTabsFeature: SwipingTabsFeatureProvider = mock()
        whenever(swipingTabsFeature.isEnabled).thenReturn(swiping)
        viewModel = TabSwitcherViewModel(
            tabRepositoryProvider = repositories,
            browserModeStateHolder = mock(),
            fireModeAvailability = mock(),
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pixel = mock(),
            swipingTabsFeature = swipingTabsFeature,
            duckChat = duckChat,
            duckAiFeatureState = duckAiFeature,
            webTrackersBlockedHistory = mock(),
            tabSwitcherDataStore = mock(),
            faviconManager = mock(),
            savedSitesRepository = mock(),
            trackersAnimationInfoPanelPixels = mock(),
            omnibarRepository = mock(),
            tabTitleResolver = mock(),
            duckAiSessionCallback = mock(),
            appCoroutineScope = coroutineRule.testScope,
            fireTabsPromos = mock(),
            remoteMessageModel = mock(),
            tabManagerFeatureFlags = FakeFeatureToggleFactory.create(TabManagerFeatureFlags::class.java),
            newTabTransitionConfigProvider = configProvider,
        )
        TabSwitcherActivity::class.java.getDeclaredField("newTabTransition\$delegate").apply { isAccessible = true }
            .set(activity, lazyOf(newTabTransition))
        doAnswer {
            finishes += "plain"
            null
        }.whenever(activity).finishAfterTransition()
        doAnswer {
            finishes += "animated"
            null
        }.whenever(activity).finish()
        doNothing().whenever(activity).setResult(any(), any<Intent>())
        @Suppress("DEPRECATION")
        doNothing().whenever(activity).overridePendingTransition(any(), any())
        viewModel.command.observeForever(observer)
    }

    private companion object {
        const val BLANK_TAB = "blank"
        const val NEW_TAB = "new-tab"
        const val EXISTING_TAB = "existing"
        const val EMPTY_CHAT = "empty-chat"
    }
}
