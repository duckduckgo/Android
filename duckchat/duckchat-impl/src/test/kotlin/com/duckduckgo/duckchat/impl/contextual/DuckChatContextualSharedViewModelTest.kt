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

package com.duckduckgo.duckchat.impl.contextual

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DuckChatContextualSharedViewModelTest {

    private val testee = DuckChatContextualSharedViewModel()

    @Test
    fun whenPageContextReceivedThenCommandEmitted() = runTest {
        val tabId = "tab-1"
        val pageContext = """{"title":"Example"}"""

        testee.commands.test {
            testee.onPageContextReceived(tabId, pageContext)

            val command = awaitItem()
            assertEquals(
                DuckChatContextualSharedViewModel.Command.PageContextAttached(tabId, pageContext),
                command,
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenMultiplePageContextsReceivedThenCommandsEmittedInOrder() = runTest {
        val first = DuckChatContextualSharedViewModel.Command.PageContextAttached("tab-1", "ctx-1")
        val second = DuckChatContextualSharedViewModel.Command.PageContextAttached("tab-2", "ctx-2")

        testee.commands.test {
            testee.onPageContextReceived(first.tabId, first.pageContext)
            testee.onPageContextReceived(second.tabId, second.pageContext)

            assertEquals(first, awaitItem())
            assertEquals(second, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOpenRequestedThenOpenSheetCommandEmitted() = runTest {
        testee.commands.test {
            testee.onOpenRequested()

            val command = awaitItem()
            assertEquals(DuckChatContextualSharedViewModel.Command.OpenSheet, command)
            cancelAndConsumeRemainingEvents()
        }
    }
}
