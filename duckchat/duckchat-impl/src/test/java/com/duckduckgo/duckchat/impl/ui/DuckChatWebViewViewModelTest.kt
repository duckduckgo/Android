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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewViewModel.Command
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckChatWebViewViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()
    private val duckChat: DuckChatInternal = mock()
    private val subscriptionStatusFlow = MutableSharedFlow<SubscriptionStatus>()

    private lateinit var viewModel: DuckChatWebViewViewModel

    @Before
    fun setup() {
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(subscriptionStatusFlow)
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.observeFullscreenModeUserSetting()).thenReturn(flowOf(true))
        viewModel = DuckChatWebViewViewModel(subscriptions, duckChat)
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
    fun `fullscreen mode - when flags enabled, then viewstate enabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeFullscreenModeUserSetting()).thenReturn(flowOf(true))
            viewModel = DuckChatWebViewViewModel(
                subscriptions,
                duckChat = duckChat,
            )

            viewModel.viewState.test {
                val state = awaitItem()
                assertTrue(state.isDuckChatUserEnabled)
                assertTrue(state.isFullScreenModeEnabled)
            }
        }

    @Test
    fun `fullscreen mode - when flags disabled, then viewstate disabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeFullscreenModeUserSetting()).thenReturn(flowOf(false))
            viewModel = DuckChatWebViewViewModel(
                subscriptions,
                duckChat = duckChat,
            )

            viewModel.viewState.test {
                val state = awaitItem()
                assertFalse(state.isDuckChatUserEnabled)
                assertFalse(state.isFullScreenModeEnabled)
            }
        }
}
