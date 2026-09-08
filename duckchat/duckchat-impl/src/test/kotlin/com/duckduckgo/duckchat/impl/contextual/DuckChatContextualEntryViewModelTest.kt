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
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelPageType
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.ui.nativeinput.textselection.RealTextSelectionPayloadBuilder
import com.duckduckgo.duckchat.impl.ui.nativeinput.textselection.RealTextSelectionStore
import com.duckduckgo.duckchat.impl.ui.nativeinput.textselection.TextSelectionPayloadBuilder
import com.duckduckgo.duckchat.impl.ui.nativeinput.textselection.TextSelectionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class DuckChatContextualEntryViewModelTest {

    private val store: ContextualEntryPromptStore = mock()
    private val duckChatPixels: DuckChatPixels = mock()
    private val modelManager: DuckAiModelManager = mock()
    private val textSelectionStore = RealTextSelectionStore()
    private val viewModel = DuckChatContextualEntryViewModel(
        store,
        duckChatPixels,
        modelManager,
        textSelectionStore,
        RealTextSelectionPayloadBuilder(RuntimeEnvironment.getApplication()),
    )

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
    fun whenSuggestionSubmittedThenUnifiedInputPromptSubmittedPixelFired() = runTest {
        viewModel.start("tab-1")
        whenever(modelManager.getSelectedModelId()).thenReturn("gpt-5.2")
        whenever(modelManager.getResolvedReasoningEffort()).thenReturn("low")

        viewModel.commands.test {
            viewModel.onSuggestionSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        verify(duckChatPixels).firePromptSubmitted(
            selectedTool = "none",
            modelId = "gpt-5.2",
            reasoningEffort = "low",
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.CONTEXTUAL_CHAT,
            defaultMode = null,
            tabId = "tab-1",
            pageType = DuckChatPixelPageType.CONTEXTUAL,
            addressBarEntryPoint = null,
        )
    }

    @Test
    fun whenSummarizeSubmittedThenUnifiedInputPromptSubmittedPixelFired() = runTest {
        viewModel.start("tab-1")
        viewModel.onPageContextReceived(validContext)
        whenever(modelManager.getSelectedModelId()).thenReturn("gpt-5.2")
        whenever(modelManager.getResolvedReasoningEffort()).thenReturn("low")

        viewModel.commands.test {
            viewModel.onSummarizeSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        verify(duckChatPixels).firePromptSubmitted(
            selectedTool = "none",
            modelId = "gpt-5.2",
            reasoningEffort = "low",
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.CONTEXTUAL_CHAT,
            defaultMode = null,
            tabId = "tab-1",
            pageType = DuckChatPixelPageType.CONTEXTUAL,
            addressBarEntryPoint = null,
        )
    }

    @Test
    fun whenPromptSubmittedThenUnifiedInputPromptSubmittedPixelNotFired() = runTest {
        viewModel.start("tab-1")

        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        verify(duckChatPixels, never()).firePromptSubmitted(
            selectedTool = any(),
            modelId = any(),
            reasoningEffort = any(),
            hasImageAttachment = any(),
            hasFileAttachment = any(),
            hasText = any(),
            surface = any(),
            defaultMode = anyOrNull(),
            tabId = anyOrNull(),
            pageType = any(),
            addressBarEntryPoint = anyOrNull(),
        )
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

    @Test
    fun whenStartedThenReportsFloatingInputShown() {
        viewModel.start("tab-1")

        verify(duckChatPixels).reportContextualFloatingInputShown()
    }

    @Test
    fun whenStartedThenDoesNotReportSheetOpened() {
        viewModel.start("tab-1")

        verify(duckChatPixels, never()).reportContextualSheetOpened()
    }

    @Test
    fun whenDismissedThenDoesNotReportSheetDismissed() {
        viewModel.onDismiss()

        verify(duckChatPixels, never()).reportContextualSheetDismissed()
    }

    @Test
    fun whenPromptSubmittedThenReportsFloatingInputPromotedToSheet() = runTest {
        viewModel.start("tab-1")
        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        verify(duckChatPixels).reportContextualFloatingInputPromotedToSheet()
    }

    @Test
    fun whenDismissedWithAttachedContextThenReportsDismissed() {
        viewModel.onDismiss()

        verify(duckChatPixels).reportContextualFloatingInputDismissedWithoutSubmission()
    }

    @Test
    fun whenContextManuallyAttachedThenReportsManualAttachPixel() {
        viewModel.onPageContextReceived(validContext)
        viewModel.onContextRemoved()

        viewModel.onAttachContextRequested()

        assertEquals(validContext, viewModel.viewState.value.attachedContext?.serialized)
        verify(duckChatPixels).reportContextualPageContextManuallyAttachedNative()
    }

    @Test
    fun whenNoValidContextToAttachThenDoesNotReportManualAttachPixel() {
        viewModel.onAttachContextRequested()

        assertNull(viewModel.viewState.value.attachedContext)
        verify(duckChatPixels, never()).reportContextualPageContextManuallyAttachedNative()
    }

    @Test
    fun whenContextRemovedThenReportsRemoveAttachmentPixel() {
        viewModel.onPageContextReceived(validContext)

        viewModel.onContextRemoved()

        verify(duckChatPixels).reportContextualPageContextRemovedNative()
    }

    @Test
    fun whenTextSelectionAttachedThenPageContextNotAttached() = runTest {
        viewModel.start("tab-1")
        textSelectionStore.add("tab-1", "selected words")

        viewModel.onPageContextReceived(validContext)

        assertNull(viewModel.viewState.value.attachedContext)
    }

    @Test
    fun whenPromptSubmittedWithTextSelectionsThenSelectionsSentOnOwnKeyAndCleared() = runTest {
        viewModel.start("tab-1")
        viewModel.onPageContextReceived(validContext)
        textSelectionStore.add("tab-1", "first selection")
        textSelectionStore.add("tab-1", "second selection")

        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        val captor = argumentCaptor<ContextualEntryPrompt>()
        verify(store).store(captor.capture())
        val selections = captor.firstValue.selectionsJson!!
        assertEquals(2, selections.length())
        val first = selections.getJSONObject(0)
        assertEquals("first selection", first.getString("content"))
        assertEquals("https://example.com", first.getString("url"))
        assertEquals(2, first.getInt("wordCount"))
        assertEquals(15, first.getInt("fullContentLength"))
        assertFalse(first.getBoolean("truncated"))
        assertTrue(textSelectionStore.selections("tab-1").value.isEmpty())
    }

    @Test
    fun whenPromptSubmittedWithoutTextSelectionsThenNoSelectionsKey() = runTest {
        viewModel.start("tab-1")
        viewModel.onPageContextReceived(validContext)

        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        val captor = argumentCaptor<ContextualEntryPrompt>()
        verify(store).store(captor.capture())
        assertNull(captor.firstValue.selectionsJson)
        assertEquals(validContext, captor.firstValue.serializedPageContext)
    }

    @Test
    fun whenSelectionExceedsMaxContentLengthThenTruncatedButSizeReported() = runTest {
        val long = "word ".repeat(3000)
        viewModel.start("tab-1")
        textSelectionStore.add("tab-1", long)

        viewModel.commands.test {
            viewModel.onPromptSubmitted(samplePrompt)
            assertEquals(DuckChatContextualEntryViewModel.Command.HandOffToSheet, awaitItem())
        }

        val captor = argumentCaptor<ContextualEntryPrompt>()
        verify(store).store(captor.capture())
        val selection = captor.firstValue.selectionsJson!!.getJSONObject(0)
        assertTrue(selection.getBoolean("truncated"))
        assertEquals(TextSelectionPayloadBuilder.MAX_CONTENT_LENGTH, selection.getString("content").length)
        assertEquals(long.trim().length, selection.getInt("fullContentLength"))
        assertEquals(3000, selection.getInt("wordCount"))
    }

    @Test
    fun whenSelectionsExceedMaxThenExtrasDropped() {
        repeat(
            TextSelectionStore.MAX_SELECTIONS + 2,
        ) { index -> textSelectionStore.add("tab-1", "selection $index") }

        assertEquals(TextSelectionStore.MAX_SELECTIONS, textSelectionStore.consume("tab-1").size)
    }

    @Test
    fun whenSelectionRemovedThenDroppedFromStore() {
        textSelectionStore.add("tab-1", "keep me")
        textSelectionStore.add("tab-1", "remove me")
        val target = textSelectionStore.selections("tab-1").value.last()

        textSelectionStore.remove("tab-1", target.id)

        assertEquals(listOf("keep me"), textSelectionStore.consume("tab-1").map { it.text })
    }
}
