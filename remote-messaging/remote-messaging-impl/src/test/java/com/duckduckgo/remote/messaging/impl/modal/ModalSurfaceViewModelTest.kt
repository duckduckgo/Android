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

package com.duckduckgo.remote.messaging.impl.modal

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessagePixelHelper
import com.duckduckgo.remote.messaging.impl.modal.cardslist.RealCardsListRemoteMessagePixelHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class ModalSurfaceViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val cardsListPixelHelper: CardsListRemoteMessagePixelHelper = mock()

    private lateinit var viewModel: ModalSurfaceViewModel

    @Before
    fun setup() {
        viewModel = ModalSurfaceViewModel(
            dispatchers = coroutineTestRule.testDispatcherProvider,
            cardsListPixelHelper = cardsListPixelHelper,
        )
    }

    @Test
    fun whenInitialiseCalledWithNullParamsThenViewStateIsNull() = runTest {
        viewModel.viewState.test {
            viewModel.onInitialise(null)

            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInitialiseCalledWithCardsListMessageTypeThenViewStateShowsCardsListView() = runTest {
        val messageId = "test-message-id"
        val params = ModalSurfaceActivityFromMessageId(
            messageId = messageId,
            messageType = Content.MessageType.CARDS_LIST,
        )

        viewModel.viewState.test {
            assertNull(awaitItem()) // Initial state

            viewModel.onInitialise(params)

            val viewState = awaitItem()
            assertNotNull(viewState)
            assertEquals(messageId, viewState?.messageId)
            assertTrue(viewState?.showCardsListView == true)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenInitialiseCalledWithNonCardsListMessageTypeThenViewStateRemainsNull() = runTest {
        val messageId = "test-message-id"
        val params = ModalSurfaceActivityFromMessageId(
            messageId = messageId,
            messageType = Content.MessageType.SMALL, // or any other type
        )

        viewModel.viewState.test {
            viewModel.onInitialise(params)

            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDismissCalledThenDismissMessageCommandEmitted() = runTest {
        viewModel.commands.test {
            viewModel.onDismiss()

            val command = awaitItem()
            assertTrue(command is ModalSurfaceViewModel.Command.DismissMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnBackPressedWithCardsListViewShownThenDismissMessageCommandEmittedAndPixelFired() = runTest {
        val messageId = "test-message-id"
        val params = ModalSurfaceActivityFromMessageId(
            messageId = messageId,
            messageType = Content.MessageType.CARDS_LIST,
        )

        // Initialize with CARDS_LIST to set showCardsListView = true
        viewModel.onInitialise(params)

        viewModel.commands.test {
            viewModel.onBackPressed()

            val command = awaitItem()
            assertTrue(command is ModalSurfaceViewModel.Command.DismissMessage)

            // Verify pixel helper was called with correct parameters
            val expectedParams = mapOf(
                RealCardsListRemoteMessagePixelHelper.PARAM_NAME_DISMISS_TYPE to
                    RealCardsListRemoteMessagePixelHelper.PARAM_VALUE_BACK_BUTTON_OR_GESTURE,
            )
            verify(cardsListPixelHelper).dismissCardsListMessage(messageId, expectedParams)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnBackPressedWithCardsListViewNotShownThenDismissMessageCommandEmittedWithoutPixel() = runTest {
        // Don't initialize or initialize with non-CARDS_LIST type, so showCardsListView = false
        viewModel.commands.test {
            viewModel.onBackPressed()

            val command = awaitItem()
            assertTrue(command is ModalSurfaceViewModel.Command.DismissMessage)

            // Verify pixel helper was NOT called
            verifyNoInteractions(cardsListPixelHelper)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnBackPressedWithCardsListViewButNoMessageIdThenDismissMessageCommandEmittedWithoutPixel() = runTest {
        // This tests an edge case where viewState might be set but lastRemoteMessageIdSeen is null
        // In the current implementation, this shouldn't happen, but it's good to verify the null-safe behavior
        viewModel.commands.test {
            viewModel.onBackPressed()

            val command = awaitItem()
            assertTrue(command is ModalSurfaceViewModel.Command.DismissMessage)

            // Verify pixel helper was NOT called since lastRemoteMessageIdSeen is null
            verifyNoInteractions(cardsListPixelHelper)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
