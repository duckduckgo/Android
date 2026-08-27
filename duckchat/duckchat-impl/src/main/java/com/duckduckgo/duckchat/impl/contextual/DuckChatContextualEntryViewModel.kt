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

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelPageType
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import javax.inject.Inject

/**
 * Owns the entry dialog's INPUT-stage state: page-context attach/validity and the prompt hand-off to
 * the contextual sheet. View/Android concerns (the dialog window, insets, focus, file pickers, and the
 * suggestion/input view wiring) stay in [DuckChatContextualEntryDialog].
 */
@ContributesViewModel(FragmentScope::class)
class DuckChatContextualEntryViewModel @Inject constructor(
    private val contextualEntryPromptStore: ContextualEntryPromptStore,
    private val duckChatPixels: DuckChatPixels,
    private val modelManager: DuckAiModelManager,
) : ViewModel() {

    data class ViewState(
        val attachedContext: AttachedPageContext? = null,
    )

    data class AttachedPageContext(
        val serialized: String,
        val title: String,
        val url: String,
    )

    sealed class Command {
        /** The prompt is parked in the store; the host should open the sheet and the dialog should close. */
        data object HandOffToSheet : Command()
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    private var tabId: String = ""

    // The latest valid page context delivered for this tab — the source for auto- and manual attachment.
    private var latestValidPageContext: String? = null

    // Set once the user explicitly removes the context, so a later context update doesn't silently re-attach.
    private var userRemovedContext: Boolean = false

    fun start(tabId: String) {
        this.tabId = tabId
        duckChatPixels.reportContextualFloatingInputShown()
    }

    fun onPageContextReceived(serializedPageContext: String) {
        if (!isContextValid(serializedPageContext)) return
        latestValidPageContext = serializedPageContext
        // The dialog is only shown from "Ask about page", so attach regardless of the auto-attach feature
        // flag — unless the user explicitly removed the context this session.
        if (!userRemovedContext) attach(serializedPageContext)
    }

    /** The composer's "attach page context" affordance (shown when nothing is attached). */
    fun onAttachContextRequested() {
        latestValidPageContext?.let {
            duckChatPixels.reportContextualPageContextManuallyAttachedNative()
            attach(it)
        }
    }

    fun onContextRemoved() {
        userRemovedContext = true
        _viewState.update { it.copy(attachedContext = null) }
        duckChatPixels.reportContextualPageContextRemovedNative()
    }

    /** A suggested prompt was picked; suggestions are page-specific, so attach the context before submit. */
    fun onSuggestionSubmitted(prompt: NativeInputPrompt) {
        if (_viewState.value.attachedContext == null) latestValidPageContext?.let { attach(it) }
        duckChatPixels.firePromptSubmitted(
            selectedTool = "none",
            modelId = modelManager.getSelectedModelId(),
            reasoningEffort = modelManager.getResolvedReasoningEffort(),
            hasImageAttachment = false,
            hasFileAttachment = false,
            hasText = true,
            surface = DuckChatPixelSurface.CONTEXTUAL_CHAT,
            defaultMode = null,
            tabId = tabId,
            pageType = DuckChatPixelPageType.CONTEXTUAL,
            addressBarEntryPoint = null,
        )
        submit(prompt)
    }

    /** A typed prompt or the Summarize quick action was submitted. */
    fun onPromptSubmitted(prompt: NativeInputPrompt) {
        submit(prompt)
    }

    private fun submit(prompt: NativeInputPrompt) {
        contextualEntryPromptStore.store(
            ContextualEntryPrompt(tabId, prompt, _viewState.value.attachedContext?.serialized),
        )
        duckChatPixels.reportContextualFloatingInputPromotedToSheet()
        commandChannel.trySend(Command.HandOffToSheet)
    }

    private fun attach(serializedPageContext: String) {
        val json = runCatching { JSONObject(serializedPageContext) }.getOrNull() ?: return
        userRemovedContext = false
        _viewState.update {
            it.copy(
                attachedContext = AttachedPageContext(
                    serialized = serializedPageContext,
                    title = json.optString("title"),
                    url = json.optString("url"),
                ),
            )
        }
    }

    private fun isContextValid(serializedPageContext: String): Boolean {
        val json = runCatching { JSONObject(serializedPageContext) }.getOrNull() ?: return false
        return json.optString("title").isNotBlank() && json.optString("content").isNotBlank()
    }

    fun onDismiss() {
        duckChatPixels.reportContextualFloatingInputDismissedWithoutSubmission()
    }
}
