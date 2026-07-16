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

import android.net.Uri
import android.webkit.ValueCallback
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputModeWidget
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * A prompt submitted from the unified input widget in the contextual sheet, in either the initial
 * (INPUT) state or a running chat (WEBVIEW). Carries the widget's current model/reasoning/tool/
 * attachment selections so the chat is submitted with everything the user configured.
 */
data class NativeInputPrompt(
    val prompt: String,
    val modelId: String?,
    val reasoningEffort: String?,
    val selectedTool: String?,
    val imagesJson: JSONArray?,
    val filesJson: JSONArray?,
)

interface ContextualNativeInputManager {
    fun init(
        tabId: String,
        card: MaterialCardView,
        widget: NativeInputModeWidget,
        jsMessaging: JsMessaging,
        lifecycleOwner: LifecycleOwner,
        chatIdFlow: Flow<String?>,
        onSearchSubmitted: (String) -> Unit,
        onCameraCaptureRequested: (ValueCallback<Array<Uri>>) -> Unit = {},
        onFilePickerRequested: (ValueCallback<Array<Uri>>, List<String>) -> Unit = { _, _ -> },
        // Invoked when the widget submits a prompt, in both INPUT and WEBVIEW modes. Routing every submit
        // through the ViewModel keeps prompt-building (and page-context attachment) in one place.
        onPromptSubmitted: (NativeInputPrompt) -> Unit = {},
        onAskAboutTab: () -> Unit = {},
        onAskAboutPage: () -> Unit = {},
        onPageContextRemoved: () -> Unit = {},
    )

    fun onWebViewMode()
    fun onInputMode()

    /**
     * Called when the contextual sheet is closed (e.g. STATE_HIDDEN). Reverts the per-tab
     * [NativeInputState] back to a browser-context default so other observers (like StartChatView
     * in the main widget) don't keep reading the DUCK_AI_CONTEXTUAL/DUCK_AI values the contextual
     * widget wrote during its lifetime.
     */
    fun onContextualClosed(tabId: String)

    /**
     * Called when the contextual sheet is reopened for a tab that was previously closed. Restores the
     * per-tab [NativeInputState] to the contextual (DUCK_AI) values that [onContextualClosed] reverted,
     * so the widget's plugin controls (attach, model picker, tools — gated on toggleSelection == DUCK_AI)
     * reappear. Without this the reused widget keeps the browser/search state and renders without them.
     */
    fun onContextualReopened(tabId: String)
}

@ContributesBinding(FragmentScope::class)
class RealContextualNativeInputManager @Inject constructor(
    private val duckChat: DuckChat,
    private val nativeInputStatePublisher: NativeInputStatePublisher,
) : ContextualNativeInputManager {

    private var isNativeInputEnabled = false
    private var card: MaterialCardView? = null
    private var jsMessaging: JsMessaging? = null
    private var widget: NativeInputModeWidget? = null
    private var lastMode: Mode? = null
    private val modelPickerEnabled = MutableStateFlow(true)

    private enum class Mode { WEBVIEW, INPUT }

    override fun init(
        tabId: String,
        card: MaterialCardView,
        widget: NativeInputModeWidget,
        jsMessaging: JsMessaging,
        lifecycleOwner: LifecycleOwner,
        chatIdFlow: Flow<String?>,
        onSearchSubmitted: (String) -> Unit,
        onCameraCaptureRequested: (ValueCallback<Array<Uri>>) -> Unit,
        onFilePickerRequested: (ValueCallback<Array<Uri>>, List<String>) -> Unit,
        onPromptSubmitted: (NativeInputPrompt) -> Unit,
        onAskAboutTab: () -> Unit,
        onAskAboutPage: () -> Unit,
        onPageContextRemoved: () -> Unit,
    ) {
        this.card = card
        this.jsMessaging = jsMessaging
        this.widget = widget

        applyCardShape(card)
        setupWidget(
            tabId, widget, chatIdFlow, onSearchSubmitted,
            onCameraCaptureRequested, onFilePickerRequested,
            onPromptSubmitted, onAskAboutTab, onAskAboutPage, onPageContextRemoved,
        )
        observeNativeInputSetting(lifecycleOwner)
    }

    override fun onContextualClosed(tabId: String) {
        if (tabId.isBlank()) return
        val browser = NativeInputState.InputContext.BROWSER
        nativeInputStatePublisher.update(tabId) {
            it.copy(
                inputContext = browser,
                toggleSelection = NativeInputState.defaultToggleFor(browser),
            )
        }
    }

    override fun onContextualReopened(tabId: String) {
        if (tabId.isBlank()) return
        val contextual = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL
        nativeInputStatePublisher.update(tabId) {
            it.copy(
                inputContext = contextual,
                toggleSelection = NativeInputState.defaultToggleFor(contextual),
            )
        }
    }

    override fun onWebViewMode() {
        lastMode = Mode.WEBVIEW
        if (isNativeInputEnabled) {
            card?.show()
            // WEBVIEW mode means a chat is in progress.
            // Hide the picker so the user can't change models mid-chat.
            modelPickerEnabled.value = false
        } else {
            card?.gone()
        }
    }

    override fun onInputMode() {
        lastMode = Mode.INPUT
        if (isNativeInputEnabled) {
            // The unified input widget is the composer for the initial sheet.
            card?.show()
            // INPUT mode is a new chat: restore the picker so the user can pick a model before starting.
            modelPickerEnabled.value = true
        } else {
            // Flag off: the legacy EditText composer is shown instead, so keep the widget card hidden.
            card?.gone()
        }
    }

    private fun applyCardShape(card: MaterialCardView) {
        // The card floats within the input area (margins on all sides), matching the Duck.ai omnibar
        // card, so all four corners are rounded rather than the docked top-only shape.
        val radius = card.resources.getDimension(
            com.duckduckgo.mobile.android.R.dimen.extraLargeShapeCornerRadius,
        )
        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setAllCornerSizes(radius)
            .build()
    }

    private fun setupWidget(
        tabId: String,
        widget: NativeInputModeWidget,
        chatIdFlow: Flow<String?>,
        onSearchSubmitted: (String) -> Unit,
        onCameraCaptureRequested: (ValueCallback<Array<Uri>>) -> Unit,
        onFilePickerRequested: (ValueCallback<Array<Uri>>, List<String>) -> Unit,
        onPromptSubmitted: (NativeInputPrompt) -> Unit,
        onAskAboutTab: () -> Unit,
        onAskAboutPage: () -> Unit,
        onPageContextRemoved: () -> Unit,
    ) {
        widget.configureContextual(tabId)
        widget.bindChatIdSource(chatIdFlow)
        widget.bindModelPickerEnabledSource(modelPickerEnabled)
        widget.hideMainButtons()
        widget.onStopTapped = ::sendStopEvent
        widget.bindAttachmentCallbacks(
            onCameraCaptureRequested = onCameraCaptureRequested,
            onFilePickerRequested = onFilePickerRequested,
        )
        widget.setContextualAttachmentActions(
            onAskAboutTab = onAskAboutTab,
            onAskAboutPage = onAskAboutPage,
            onPageContextRemoved = onPageContextRemoved,
        )
        widget.bindInputEvents(
            onSearchTextChanged = { },
            onSearchSubmitted = { query ->
                onSearchSubmitted(query)
            },
            onChatSubmitted = { prompt ->
                val imagesJson = widget.getImageAttachmentsJson()
                val filesJson = widget.getFileAttachmentsJson()
                val modelId = widget.getSelectedModelId()
                val reasoningEffort = widget.getResolvedReasoningEffort()
                val selectedTool = widget.getSelectedTool()
                widget.clearAttachments()
                // Both the initial submit (INPUT) and follow-ups in a running chat (WEBVIEW) go through the
                // ViewModel so the prompt is built in one place (generateContextPrompt). That single path is
                // what attaches any page context the user added from the "+" menu; the previous WEBVIEW
                // shortcut built its own JS event and silently dropped that context. onPromptSent starts a
                // new chat from INPUT and appends to the active chat from WEBVIEW — the web page decides
                // which, based on its own state, not on the native caller.
                onPromptSubmitted(
                    NativeInputPrompt(prompt, modelId, reasoningEffort, selectedTool, imagesJson, filesJson),
                )
                widget.clearSelectedTool()
                widget.text = ""
            },
        )
    }

    private fun observeNativeInputSetting(lifecycleOwner: LifecycleOwner) {
        duckChat.observeNativeChatInputEnabled()
            .onEach { isEnabled ->
                isNativeInputEnabled = isEnabled
                // Re-apply the current mode so the card shows/hides immediately when the flag
                // flips at runtime — otherwise a card left over from WEBVIEW mode would overlap
                // the web input after the flag turns off.
                when (lastMode) {
                    Mode.WEBVIEW -> onWebViewMode()
                    Mode.INPUT -> onInputMode()
                    null -> {}
                }
            }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    private fun sendStopEvent() {
        jsMessaging?.sendSubscriptionEvent(
            SubscriptionEventData(
                featureName = RealDuckChatJSHelper.DUCK_CHAT_FEATURE_NAME,
                subscriptionName = "submitPromptInterruption",
                params = JSONObject("{}"),
            ),
        )
    }
}
