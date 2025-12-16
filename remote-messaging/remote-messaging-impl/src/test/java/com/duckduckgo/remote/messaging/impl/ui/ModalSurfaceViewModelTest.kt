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
import com.duckduckgo.remote.messaging.api.Content
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ModalSurfaceViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var viewModel: ModalSurfaceViewModel

    @Before
    fun setup() {
        viewModel = ModalSurfaceViewModel(
            dispatchers = coroutineTestRule.testDispatcherProvider,
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
}
