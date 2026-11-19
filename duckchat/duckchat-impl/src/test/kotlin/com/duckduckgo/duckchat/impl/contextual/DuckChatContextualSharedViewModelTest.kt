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
import com.duckduckgo.duckchat.api.DuckAiPdfMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun whenReloadChatRequestedThenReloadChatCommandEmitted() = runTest {
        testee.commands.test {
            testee.onReloadChatRequested()

            val command = awaitItem()
            assertEquals(DuckChatContextualSharedViewModel.Command.ReloadChat, command)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenMainBrowserPageFinishedThenCommandEmitted() = runTest {
        testee.commands.test {
            testee.onMainBrowserPageFinished("https://example.com")

            val command = awaitItem()
            assertEquals(DuckChatContextualSharedViewModel.Command.MainBrowserPageFinished(), command)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenMainBrowserPageFinishedWithNullUrlThenCommandEmitted() = runTest {
        testee.commands.test {
            testee.onMainBrowserPageFinished(null)

            val command = awaitItem()
            assertEquals(DuckChatContextualSharedViewModel.Command.MainBrowserPageFinished(), command)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenContextualFireConfirmedThenOnContextualFireConfirmedCommandEmitted() = runTest {
        testee.commands.test {
            testee.onContextualFireConfirmed()

            val command = awaitItem()
            assertEquals(DuckChatContextualSharedViewModel.Command.OnContextualFireConfirmed, command)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRequestPageContextThenCollectPageContextCommandEmitted() = runTest {
        testee.commands.test {
            testee.requestPageContext()

            val command = awaitItem()
            assertEquals(DuckChatContextualSharedViewModel.Command.CollectPageContext, command)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenMultipleSubscribersThenAllReceiveCommands() = runTest {
        testee.commands.test {
            testee.commands.test {
                testee.onReloadChatRequested()

                assertEquals(DuckChatContextualSharedViewModel.Command.ReloadChat, awaitItem())
                cancelAndConsumeRemainingEvents()
            }

            assertEquals(DuckChatContextualSharedViewModel.Command.ReloadChat, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenReloadChatRequestedThenSingleReloadChatCommandEmitted() = runTest {
        testee.commands.test {
            testee.onReloadChatRequested()

            assertEquals(DuckChatContextualSharedViewModel.Command.ReloadChat, awaitItem())
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test fun whenTypedPdfMetadataReceivedThenSerializedWithoutBytesOrPersistence() = runTest {
        val metadata = DuckAiPdfMetadata("private.pdf", "https://private.example", DuckAiPdfMetadata.Availability.AVAILABLE)
        testee.commands.test {
            testee.onPdfPageContextReceived("tab", metadata)
            val result = awaitItem() as DuckChatContextualSharedViewModel.Command.PageContextAttached
            val json = JSONObject(result.pageContext)
            assertEquals("private.pdf", json.getString("title"))
            assertEquals("application/pdf", json.getString("mimeType"))
            assertFalse(json.has("data"))
            assertFalse(json.getBoolean("attached"))
            assertFalse(result.isStorePageContextEnabled)
            assertEquals("PageContextAttached", result.toString())
        }
    }

    @Test fun whenTypedPdfMetadataStaleThenNoCommandEmitted() = runTest {
        testee.commands.test {
            testee.onPdfPageContextReceived("tab", DuckAiPdfMetadata("private.pdf", "https://private.example", DuckAiPdfMetadata.Availability.STALE))
            expectNoEvents()
        }
    }
}
