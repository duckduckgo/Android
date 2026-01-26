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

package com.duckduckgo.duckchat.impl.ui

import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.messaging.sync.SyncStatusChangedObserver
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewViewModel.Command
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckChatWebViewViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()
    private val duckChat: DuckChatInternal = mock()
    private val syncStatusChangedObserver: SyncStatusChangedObserver = mock()
    private val subscriptionStatusFlow = MutableSharedFlow<SubscriptionStatus>()
    private val syncStatusChangedEventsFlow = MutableSharedFlow<JSONObject>()

    private lateinit var viewModel: DuckChatWebViewViewModel

    @Before
    fun setup() {
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(subscriptionStatusFlow)
        whenever(syncStatusChangedObserver.syncStatusChangedEvents).thenReturn(syncStatusChangedEventsFlow)
        viewModel = DuckChatWebViewViewModel(
            subscriptions = subscriptions,
            duckChat = duckChat,
            syncStatusChangedObserver = syncStatusChangedObserver,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenSubscriptionStatusChangesToActiveThenSendSubscriptionAuthUpdateEventCommand() = runTest {
        viewModel.commands.test {
            subscriptionStatusFlow.emit(AUTO_RENEWABLE)

            val command = awaitItem()
            assertTrue(command is Command.SendSubscriptionAuthUpdateEvent)
        }
    }

    @Test
    fun whenSubscriptionStatusChangesToInactiveThenSendSubscriptionAuthUpdateEventCommand() = runTest {
        viewModel.commands.test {
            subscriptionStatusFlow.emit(INACTIVE)

            val command = awaitItem()
            assertTrue(command is Command.SendSubscriptionAuthUpdateEvent)
        }
    }

    @Test
    fun whenSubscriptionStatusChangesToExpiredThenSendSubscriptionAuthUpdateEventCommand() = runTest {
        viewModel.commands.test {
            subscriptionStatusFlow.emit(EXPIRED)

            val command = awaitItem()
            assertTrue(command is Command.SendSubscriptionAuthUpdateEvent)
        }
    }

    @Test
    fun whenSubscriptionStatusChangesToUnknownThenSendSubscriptionAuthUpdateEventCommand() = runTest {
        viewModel.commands.test {
            subscriptionStatusFlow.emit(UNKNOWN)

            val command = awaitItem()
            assertTrue(command is Command.SendSubscriptionAuthUpdateEvent)
        }
    }

    @Test
    fun whenSubscriptionStatusChangesTwiceToSameValueThenOnlyOneCommandSent() = runTest {
        viewModel.commands.test {
            // Emit the same status twice
            subscriptionStatusFlow.emit(AUTO_RENEWABLE)
            subscriptionStatusFlow.emit(AUTO_RENEWABLE)

            // Should only receive one command due to distinctUntilChanged
            val command = awaitItem()
            assertTrue(command is Command.SendSubscriptionAuthUpdateEvent)
            expectNoEvents()
        }
    }

    @Test
    fun whenSubscriptionStatusChangesTwiceToDifferentValuesThenTwoCommandsSent() = runTest {
        viewModel.commands.test {
            subscriptionStatusFlow.emit(AUTO_RENEWABLE)
            subscriptionStatusFlow.emit(EXPIRED)

            val firstCommand = awaitItem()
            assertTrue(firstCommand is Command.SendSubscriptionAuthUpdateEvent)

            val secondCommand = awaitItem()
            assertTrue(secondCommand is Command.SendSubscriptionAuthUpdateEvent)
        }
    }

    @Test
    fun whenMultipleSubscriptionStatusChangesOccurThenCorrespondingCommandsSent() = runTest {
        viewModel.commands.test {
            subscriptionStatusFlow.emit(UNKNOWN)
            subscriptionStatusFlow.emit(INACTIVE)
            subscriptionStatusFlow.emit(AUTO_RENEWABLE)
            subscriptionStatusFlow.emit(EXPIRED)

            repeat(4) {
                val command = awaitItem()
                assertTrue(command is Command.SendSubscriptionAuthUpdateEvent)
            }
        }
    }

    @Test
    fun `when handle on same webview then call duck chat`() {
        viewModel.handleOnSameWebView("https://duck.ai/somepath")
        verify(duckChat).canHandleOnAiWebView("https://duck.ai/somepath")
    }

    @Test
    fun `when should close duck chat and current is duck ai and first is duckduckgo then return true`() {
        whenever(duckChat.isStandaloneMigrationEnabled()).thenReturn(true)
        val history = mock<WebBackForwardList>()

        val currentItem = mock<WebHistoryItem> {
            on { url } doReturn "https://duck.ai/somepath"
        }
        val firstItem = mock<WebHistoryItem> {
            on { url } doReturn "https://duckduckgo.com/somepath"
        }
        whenever(history.currentItem).thenReturn(currentItem)
        whenever(history.getItemAtIndex(0)).thenReturn(firstItem)
        assertTrue(viewModel.shouldCloseDuckChat(history))
    }

    @Test
    fun `when should close duck chat and current is not duck ai or first is no duckduckgo then return false`() {
        whenever(duckChat.isStandaloneMigrationEnabled()).thenReturn(true)
        val history = mock<WebBackForwardList>()

        var currentItem = mock<WebHistoryItem> {
            on { url } doReturn "https://duckduckgo.com/somepath"
        }
        var firstItem = mock<WebHistoryItem> {
            on { url } doReturn "https://duckduckgo.com/somepath"
        }
        whenever(history.currentItem).thenReturn(currentItem)
        whenever(history.getItemAtIndex(0)).thenReturn(firstItem)
        assertFalse(viewModel.shouldCloseDuckChat(history))
        currentItem = mock<WebHistoryItem> {
            on { url } doReturn "https://duck.ai/somepath"
        }
        firstItem = mock<WebHistoryItem> {
            on { url } doReturn "https://somesite.com"
        }
        whenever(history.currentItem).thenReturn(currentItem)
        whenever(history.getItemAtIndex(0)).thenReturn(firstItem)
        assertFalse(viewModel.shouldCloseDuckChat(history))
    }

    @Test
    fun `when should close duck chat and feature flag is disabled then return false`() {
        whenever(duckChat.isStandaloneMigrationEnabled()).thenReturn(false)
        val history = mock<WebBackForwardList>()

        val currentItem = mock<WebHistoryItem> {
            on { url } doReturn "https://duck.ai/somepath"
        }
        val firstItem = mock<WebHistoryItem> {
            on { url } doReturn "https://duckduckgo.com/somepath"
        }
        whenever(history.currentItem).thenReturn(currentItem)
        whenever(history.getItemAtIndex(0)).thenReturn(firstItem)
        assertFalse(viewModel.shouldCloseDuckChat(history))
    }
}
