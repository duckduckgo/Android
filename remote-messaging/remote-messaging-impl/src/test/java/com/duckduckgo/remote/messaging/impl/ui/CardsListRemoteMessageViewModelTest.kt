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

package com.duckduckgo.remote.messaging.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.ui.CardsListRemoteMessageViewModel.Command
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CardsListRemoteMessageViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var viewModel: CardsListRemoteMessageViewModel
    private val remoteMessagingRepository: RemoteMessagingRepository = mock()
    private val commandActionMapper: CommandActionMapper = mock()

    @Before
    fun setup() {
        viewModel = CardsListRemoteMessageViewModel(
            remoteMessagingRepository = remoteMessagingRepository,
            commandActionMapper = commandActionMapper,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenInitCalledWithNullMessageIdThenDismissMessageCommandEmitted() = runTest {
        viewModel.commands.test {
            viewModel.init(null)

            val command = awaitItem()
            assertTrue(command is Command.DismissMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInitCalledWithNullMessageIdThenViewStateRemainsNull() = runTest {
        viewModel.viewState.test {
            assertNull(awaitItem()) // Initial state

            viewModel.init(null)

            // Wait briefly for any potential state updates
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInitCalledWithValidMessageIdThenViewStateUpdatedWithCardsListContent() = runTest {
        val messageId = "message-123"
        val cardsList = Content.CardsList(
            titleText = "Test Cards",
            descriptionText = "Description",
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            listItems = listOf(
                CardItem(
                    id = "id",
                    type = CardItemType.TWO_LINE_LIST_ITEM,
                    placeholder = Content.Placeholder.CRITICAL_UPDATE,
                    titleText = "Card 1",
                    descriptionText = "Description 1",
                    primaryAction = Action.Dismiss,
                ),
            ),
            primaryActionText = "Dismiss",
            primaryAction = Action.Dismiss,
        )
        val message = RemoteMessage(
            id = messageId,
            content = cardsList,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
            surfaces = listOf(Surface.MODAL),
        )
        whenever(remoteMessagingRepository.getMessageById(eq(messageId))).thenReturn(message)

        viewModel.viewState.test {
            assertNull(awaitItem()) // Initial state

            viewModel.init(messageId)

            val viewState = awaitItem()
            assertNotNull(viewState)
            assertEquals(cardsList, viewState?.cardsLists)
            assertEquals("Test Cards", viewState?.cardsLists?.titleText)
            assertEquals(1, viewState?.cardsLists?.listItems?.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInitCalledWithNonCardsListContentThenViewStateRemainsNull() = runTest {
        val messageId = "message-123"
        val message = RemoteMessage(
            id = messageId,
            content = Content.Small(titleText = "Small Message", descriptionText = "Description"),
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
            surfaces = listOf(),
        )
        whenever(remoteMessagingRepository.getMessageById(eq(messageId))).thenReturn(message)

        viewModel.viewState.test {
            assertNull(awaitItem()) // Initial state

            viewModel.init(messageId)

            // View state should remain null for non-CardsList content
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInitCalledWithNonExistentMessageIdThenViewStateRemainsNull() = runTest {
        val messageId = "non-existent"
        whenever(remoteMessagingRepository.getMessageById(eq(messageId))).thenReturn(null)

        viewModel.viewState.test {
            assertNull(awaitItem()) // Initial state

            viewModel.init(messageId)

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCloseButtonClickedThenDismissMessageCommandEmitted() = runTest {
        viewModel.commands.test {
            viewModel.onCloseButtonClicked()

            val command = awaitItem()
            assertTrue(command is Command.DismissMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnActionButtonClickedWithNullViewStateThenNoCommandEmitted() = runTest {
        viewModel.commands.test {
            viewModel.onActionButtonClicked()

            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnActionButtonClickedWithValidActionThenCommandEmitted() = runTest {
        val messageId = "message-123"
        val primaryAction = Action.Url("https://example.com")
        val cardsList = Content.CardsList(
            titleText = "Test Cards",
            descriptionText = "Description",
            listItems = emptyList(),
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            primaryAction = primaryAction,
            primaryActionText = "Action",
        )
        val message = RemoteMessage(
            id = messageId,
            content = cardsList,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
            surfaces = listOf(Surface.MODAL),
        )
        val expectedCommand = Command.SubmitUrl("https://example.com")
        whenever(remoteMessagingRepository.getMessageById(eq(messageId))).thenReturn(message)
        whenever(commandActionMapper.asCommand(eq(primaryAction))).thenReturn(expectedCommand)

        // Initialize view state
        viewModel.init(messageId)
        coroutineTestRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.commands.test {
            viewModel.onActionButtonClicked()

            val command = awaitItem()
            assertTrue(command is Command.SubmitUrl)
            assertEquals("https://example.com", (command as Command.SubmitUrl).url)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnItemClickedThenCommandEmittedForItemAction() = runTest {
        val itemAction = Action.PlayStore("com.example.app")
        val cardItem = CardItem(
            id = "id",
            titleText = "Test Card",
            descriptionText = "Description",
            primaryAction = itemAction,
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
            type = CardItemType.TWO_LINE_LIST_ITEM,
        )
        val expectedCommand = Command.LaunchPlayStore("com.example.app")
        whenever(commandActionMapper.asCommand(eq(itemAction))).thenReturn(expectedCommand)

        viewModel.commands.test {
            viewModel.onItemClicked(cardItem)

            val command = awaitItem()
            assertTrue(command is Command.LaunchPlayStore)
            assertEquals("com.example.app", (command as Command.LaunchPlayStore).appPackage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnItemClickedMultipleTimesThenMultipleCommandsEmitted() = runTest {
        val itemAction1 = Action.Url("https://example1.com")
        val itemAction2 = Action.Url("https://example2.com")
        val cardItem1 = CardItem(
            id = "id1",
            titleText = "Card 1",
            descriptionText = "Description 1",
            primaryAction = itemAction1,
            type = CardItemType.TWO_LINE_LIST_ITEM,
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
        )
        val cardItem2 = CardItem(
            id = "id2",
            titleText = "Card 2",
            descriptionText = "Description 2",
            primaryAction = itemAction2,
            type = CardItemType.TWO_LINE_LIST_ITEM,
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
        )
        whenever(commandActionMapper.asCommand(eq(itemAction1))).thenReturn(Command.SubmitUrl("https://example1.com"))
        whenever(commandActionMapper.asCommand(eq(itemAction2))).thenReturn(Command.SubmitUrl("https://example2.com"))

        viewModel.commands.test {
            viewModel.onItemClicked(cardItem1)
            val command1 = awaitItem()
            assertEquals("https://example1.com", (command1 as Command.SubmitUrl).url)

            viewModel.onItemClicked(cardItem2)
            val command2 = awaitItem()
            assertEquals("https://example2.com", (command2 as Command.SubmitUrl).url)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCommandActionMapperCalledThenVerifyCorrectActionPassed() = runTest {
        val action = Action.DefaultBrowser
        val cardItem = CardItem(
            id = "id",
            titleText = "Set Default Browser",
            descriptionText = "Make DDG your default",
            primaryAction = action,
            type = CardItemType.TWO_LINE_LIST_ITEM,
            placeholder = Content.Placeholder.DDG_ANNOUNCE,
        )
        whenever(commandActionMapper.asCommand(eq(action))).thenReturn(Command.LaunchDefaultBrowser)

        viewModel.onItemClicked(cardItem)
        coroutineTestRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(commandActionMapper).asCommand(eq(action))
    }
}
