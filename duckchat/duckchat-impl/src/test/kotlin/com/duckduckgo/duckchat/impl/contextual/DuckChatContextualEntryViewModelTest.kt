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

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DuckChatContextualEntryViewModelTest {

    private val store: ContextualEntryPromptStore = mock()
    private val viewModel = DuckChatContextualEntryViewModel(store)

    private val validContext = """{"title":"Example","url":"https://example.com","content":"some page content"}"""
    private val samplePrompt = NativeInputPrompt("hi", "model-1", "high", "tool-1", null, null)

    @Test
    fun whenValidPageContextReceivedThenAttached() {
        viewModel.onPageContextReceived(validContext)

        val attached = viewModel.viewState.value.attachedContext
        assertEquals(validContext, attached?.serialized)
        assertEquals("Example", attached?.title)
        assertEquals("https://example.com", attached?.url)
    }

    @Test
    fun whenPageContextInvalidThenNotAttached() {
        viewModel.onPageContextReceived("""{"title":"Example","url":"https://example.com","content":""}""")

        assertNull(viewModel.viewState.value.attachedContext)
    }

    @Test
    fun whenContextRemovedThenLaterContextNotReAttached() {
        viewModel.onPageContextReceived(validContext)
        viewModel.onContextRemoved()
        assertNull(viewModel.viewState.value.attachedContext)

        viewModel.onPageContextReceived(validContext)

        assertNull(viewModel.viewState.value.attachedContext)
    }

    @Test
    fun whenPromptSubmittedThenStoredWithAttachedContextAndHandsOff() = runTest {
        viewModel.start("tab-1")
        viewModel.onPageContextReceived(validContext)

        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        val captor = argumentCaptor<ContextualEntryPrompt>()
        verify(store).store(captor.capture())
        assertEquals("tab-1", captor.firstValue.tabId)
        assertEquals(samplePrompt, captor.firstValue.prompt)
        assertEquals(validContext, captor.firstValue.serializedPageContext)
    }

    @Test
    fun whenSuggestionSubmittedAfterRemovalThenReAttachesContextBeforeStoring() = runTest {
        viewModel.start("tab-1")
        viewModel.onPageContextReceived(validContext)
        viewModel.onContextRemoved()

        viewModel.commands.test {
            viewModel.onSuggestionSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        val captor = argumentCaptor<ContextualEntryPrompt>()
        verify(store).store(captor.capture())
        assertEquals(validContext, captor.firstValue.serializedPageContext)
    }

    @Test
    fun whenPromptSubmittedWithoutContextThenStoredWithNullContext() = runTest {
        viewModel.start("tab-1")

        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        val captor = argumentCaptor<ContextualEntryPrompt>()
        verify(store).store(captor.capture())
        assertNull(captor.firstValue.serializedPageContext)
    }

    @Test
    fun whenContextRemovedThenPromptStoredWithNullContext() = runTest {
        viewModel.start("tab-1")
        viewModel.onPageContextReceived(validContext)
        viewModel.onContextRemoved()

        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        val captor = argumentCaptor<ContextualEntryPrompt>()
        verify(store).store(captor.capture())
        assertNull(captor.firstValue.serializedPageContext)
    }
}
