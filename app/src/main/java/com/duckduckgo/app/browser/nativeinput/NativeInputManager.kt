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

package com.duckduckgo.app.browser.nativeinput

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Outline
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.ValueCallback
import android.widget.FrameLayout
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.QueryUrlPredictor
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.InputMode
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InteractionLock
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.usage.InputScreenSessionUsageMetric
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputWidget
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.json.JSONArray
import javax.inject.Inject

class NativeInputCallbacks(
    val onSearchTextChanged: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onDuckAiChatSubmitted: (
        query: String,
        modelId: String?,
        reasoningEffort: String?,
        selectedTool: String?,
        imagesJson: JSONArray?,
        filesJson: JSONArray?,
    ) -> Unit,
    val onChatSuggestionSelected: (String) -> Unit,
    val onDuckAiQuerySubmitted: (query: String) -> Unit = {},
    /** User picked a model in the native picker (→ submitChangeModelAction). */
    val onChangeModelSubmitted: (modelId: String) -> Unit = {},
    val onCustomizeResponsesClicked: () -> Unit = {},
    val onChatUrlSuggestionClicked: (AutoCompleteSuggestion) -> Unit = {},
    val onChatHistoryShortcutClicked: () -> Unit = {},
    val onClearAutocomplete: () -> Unit,
    val onStopTapped: () -> Unit,
    val onFireButtonPressed: () -> Unit = {},
    val onVoiceSearchPressed: (isChatTab: Boolean) -> Unit = {},
    val onCameraCaptureRequested: (ValueCallback<Array<Uri>>) -> Unit = {},
    val onFilePickerRequested: (ValueCallback<Array<Uri>>, List<String>) -> Unit = { _, _ -> },
    /**
     * Restore the autocomplete view state from the always-on cache the viewmodel keeps for
     * the omnibar's text. Returns true when the cache matched [forQuery] and was applied;
     * the caller uses the return value to decide whether to re-show the suggestions list.
     */
    val restoreOmnibarAutocomplete: (forQuery: String) -> Boolean = { _ -> false },
    val onContextualSheetRequested: () -> Unit = {},
)

interface NativeInputManager {
    fun init(
        omnibar: Omnibar,
        rootView: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        onDisabled: () -> Unit = {},
    )

    fun isNativeInputEnabled(): Boolean

    /** True when the widget is shown with the chat tab selected. */
    fun isChatTabSelected(): Boolean

    fun showNativeInput(
        tabId: String,
        layoutInflater: LayoutInflater,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        currentTabUrl: Flow<String?>,
        query: String = "",
        callbacks: NativeInputCallbacks,
        initialInputMode: InputMode? = null,
    )

    fun hideNativeInput(animate: Boolean = true, isNavigation: Boolean = false): Boolean
    fun handleDuckAiVoiceResult(query: String)
    fun onKeyboardVisibilityChanged(isVisible: Boolean)
    fun setPickingImage(picking: Boolean)
    fun setText(text: String)

    /** Lock the input field (making it non-interactive + dimmed).*/
    fun setInteractionLock(lock: InteractionLock)

    /** Show pulse animation around the Duck.ai fire button. */
    fun setDuckAiFireButtonHighlighted(highlighted: Boolean)
}

@ContributesBinding(FragmentScope::class)
class RealNativeInputManager @Inject constructor(
    private val duckChat: DuckChat,
    private val animator: NativeInputAnimator,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val globalActivityStarter: GlobalActivityStarter,
    private val queryUrlPredictor: QueryUrlPredictor,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val pixel: Pixel,
    private val nativeInputStateBugKillSwitch: NativeInputStateBugKillSwitch,
    private val duckChatPixels: DuckChatPixels,
    private val inputScreenSessionUsageMetric: InputScreenSessionUsageMetric,
    private val inputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel,
) : NativeInputManager {
    private lateinit var omnibarController: NativeInputOmnibarController
    private lateinit var rootView: ViewGroup
    private lateinit var layoutCoordinator: NativeInputLayoutCoordinator
    private var isNativeInputFieldEnabled: Boolean = false
    private var isNativeChatInputEnabled: Boolean = false
    private var isExiting: Boolean = false
    private var isPickingImage: Boolean = false
    private var duckAiToolbarHidden: Boolean = false
    private var floatingSubmitContainer: View? = null
    private var widgetRoot: View? = null
    private var lastCallbacks: NativeInputCallbacks? = null

    private val interactionLockSource = MutableStateFlow(InteractionLock.Unlocked)
    private val duckAiFireButtonHighlightSource = MutableStateFlow(false)

    private fun widgetFrom(widgetView: View): NativeInputWidget? {
        return widgetView.findViewById<View?>(R.id.inputModeWidget) as? NativeInputWidget
    }

    override fun init(
        omnibar: Omnibar,
        rootView: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        onDisabled: () -> Unit,
    ) {
        this.omnibarController = RealNativeInputOmnibarController(omnibar, rootView, nativeInputStateBugKillSwitch)
        this.rootView = rootView
        this.layoutCoordinator = NativeInputLayoutCoordinator(rootView, this.omnibarController)
        duckChat.observeNativeInputFieldUserSettingEnabled()
            .onEach { isEnabled ->
                if (isNativeInputFieldEnabled && !isEnabled) onDisabled()
                isNativeInputFieldEnabled = isEnabled
            }
            .launchIn(lifecycleOwner.lifecycleScope)
        duckChat.observeNativeChatInputEnabled()
            .onEach { isEnabled ->
                val wasEnabled = isNativeChatInputEnabled
                isNativeChatInputEnabled = isEnabled
                // If the flag turns off while Duck.ai is showing the native widget, tear it down so
                // Duck.ai's own web input is the only input — otherwise the two overlap. Go through
                // hideNativeInput (not a bare removeWidget) so the omnibar overlay chrome that
                // showNativeInput set up — forceToTop, hidden content — is restored too.
                if (wasEnabled && !isEnabled && omnibarController.isDuckAiMode()) {
                    hideNativeInput(animate = false)
                }
            }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun isNativeInputEnabled(): Boolean = isNativeInputFieldEnabled

    override fun isChatTabSelected(): Boolean {
        if (!::rootView.isInitialized) return false
        val widget = widgetFrom(rootView) ?: return false
        return widget.isChatTabSelected()
    }

    override fun setPickingImage(picking: Boolean) {
        isPickingImage = picking
    }

    override fun setText(text: String) {
        if (!::rootView.isInitialized) return
        val widget = widgetFrom(rootView) ?: return
        widget.text = text
    }

    override fun handleDuckAiVoiceResult(query: String) {
        val widget = widgetFrom(rootView)
        if (widget != null) {
            if (!widget.isChatTabSelected()) {
                widget.selectChatTab()
            }
            widget.submitMessage(query)
        } else {
            duckChat.openDuckChatWithAutoPrompt(query)
        }
    }

    override fun hideNativeInput(animate: Boolean, isNavigation: Boolean): Boolean {
        if (!isNativeInputFieldEnabled) return false

        val widgetView = rootView.findViewById<View?>(R.id.inputModeTopRoot)
            ?: rootView.findViewById(R.id.inputModeBottomRoot)
            ?: return false

        if (isNavigation) {
            widgetFrom(widgetView)?.let { widget ->
                widget.saveLastUsedTogglePosition(isChat = widget.isChatTabSelected())
            }
        }

        rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList)?.gone()
        rootView.findViewById<View?>(R.id.focusedView)?.gone()

        // Reveal the browser behind the closing widget so the exit animation plays over the
        // live page instead of the NTP background that showNtp swapped in.
        if (omnibarController.isBrowserMode()) {
            hideNtp()
        }

        // Roll the autocomplete cache back to the omnibar's text so in-widget typing is
        // dismissed. Skip on navigation paths: submit/voice-result/etc. leave the cache to
        // follow the destination, not the pre-submit omnibar state.
        if (!isNavigation) {
            lastCallbacks?.restoreOmnibarAutocomplete?.invoke(omnibarController.getText())
        }

        if (!animate) {
            animator.cancelAnimation()
            isExiting = false
            omnibarController.restore()
            omnibarController.show()
            removeWidget()
            return !omnibarController.isDuckAiMode()
        }

        val card = widgetView.findViewById<View?>(R.id.inputModeWidgetCard)
        val omnibarCard = omnibarController.getCardView()

        val isBottom = widgetFrom(widgetView)?.isWidgetBottom() ?: false
        isExiting = true
        if (!omnibarController.isDuckAiMode() && card != null && omnibarCard != null && omnibarCard.width > 0) {
            layoutCoordinator.setWidgetAnimating(true)
            animator.animateExit(
                widgetCard = card,
                widgetView = widgetView,
                omnibarCard = omnibarCard,
                isBottom = isBottom,
                onUpdate = { layoutCoordinator.onWidgetAnimationFrame(card) },
                onCancel = { layoutCoordinator.setWidgetAnimating(false) },
                onComplete = {
                    layoutCoordinator.setWidgetAnimating(false)
                    isExiting = false
                    onHide()
                },
            )
        } else {
            isExiting = false
            onHide()
        }

        return !omnibarController.isDuckAiMode()
    }

    private fun onHide() {
        omnibarController.restore()
        omnibarController.show()

        val widgetCard = rootView.findViewById<View?>(R.id.inputModeWidgetCard)
        if (widgetCard != null) {
            (widgetCard as? MaterialCardView)?.cardElevation = 0f
            val animatingRoot = widgetRoot
            widgetCard.animate()
                .alpha(0f)
                .setDuration(FADE_OUT_DURATION_MS)
                .withEndAction {
                    widgetCard.alpha = 1f
                    if (!nativeInputStateBugKillSwitch.self().isEnabled() || widgetRoot === animatingRoot) {
                        removeWidget()
                    }
                }
                .start()
        } else {
            removeWidget()
        }
    }

    override fun onKeyboardVisibilityChanged(isVisible: Boolean) {
        if (!isNativeInputFieldEnabled) return
        if (isExiting) return
        val widget = widgetFrom(rootView) ?: return
        val widgetRoot = widgetRoot

        if (omnibarController.isDuckAiMode()) {
            widget.setToggleVisible(isVisible)
        }

        if (isVisible) {
            isPickingImage = false
            onKeyboardShown(widgetRoot)
        } else {
            onKeyboardHidden(widget)
        }
    }

    private fun onKeyboardShown(widgetRoot: View?) {
        if (omnibarController.isSplitMode()) return
        if (omnibarController.isDuckAiMode()) {
            if (isLandscape()) {
                omnibarController.hide()
                duckAiToolbarHidden = true
            }
            return
        }
        omnibarController.hide()
        widgetRoot?.translationZ = 0f
    }

    private fun onKeyboardHidden(widget: NativeInputWidget) {
        if (widget.isModelMenuVisible) return
        if (isPickingImage) return
        if (omnibarController.isDuckAiMode()) {
            if (duckAiToolbarHidden) {
                omnibarController.show()
                omnibarController.hideBackground()
                duckAiToolbarHidden = false
            }
            updateWidgetFocus(widget)
        }
    }

    private fun isLandscape(): Boolean =
        rootView.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun updateWidgetFocus(widget: NativeInputWidget) {
        val focusedView = rootView.findFocus()
        val focusWithinWidget = focusedView != null && isDescendantOf(widget.asView(), focusedView)
        if (widget.hasInputFocus() || !focusWithinWidget) {
            widget.clearInputFocus()
        } else {
            widget.requestInputFocus()
        }
    }

    private fun isDescendantOf(
        ancestor: View,
        view: View,
    ): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    override fun showNativeInput(
        tabId: String,
        layoutInflater: LayoutInflater,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        currentTabUrl: Flow<String?>,
        query: String,
        callbacks: NativeInputCallbacks,
        initialInputMode: InputMode?,
    ) {
        if (!isNativeInputFieldEnabled) return

        // When native chat input is disabled, Duck.ai renders its own web input — don't overlay
        // the native widget. Remove any widget left over from a previous (non-Duck.ai) state.
        if (omnibarController.isDuckAiMode() && !isNativeChatInputEnabled) {
            removeWidget()
            return
        }

        if (omnibarController.isDuckAiMode() && rootView.findViewById<View?>(R.id.inputModeWidget) != null) return

        animator.cancelAnimation()
        isExiting = false
        if (omnibarController.isDuckAiMode()) {
            omnibarController.forceToTop()
        }
        removeWidget()
        // Assign after removeWidget — removeWidget clears lastCallbacks to drop references
        // to the previous widget's closures.
        lastCallbacks = callbacks
        if (omnibarController.isDuckAiMode()) {
            omnibarController.show()
            omnibarController.hideBackground()
        }
        val isBottom = omnibarController.isDuckAiMode() || omnibarController.isOmnibarBottom()
        val widgetView = createWidgetView(layoutInflater, isBottom)
        val prefillText = query.ifEmpty { omnibarController.getText() }
        bindWidget(widgetView, lifecycleOwner, tabs, currentTabUrl, callbacks, isBottom)
        if (!omnibarController.isDuckAiMode() && prefillText.isNotEmpty()) {
            // Restore the cache before setting text — triggerAutocomplete preserves the
            // current searchResults, so a stale cache (post-submit reset, or overwritten by
            // a previous in-widget query) would otherwise flash the list empty.
            val cacheRestored = callbacks.restoreOmnibarAutocomplete(prefillText)
            widgetFrom(widgetView)?.apply {
                text = prefillText
                selectAllText()
            }
            // hideNativeInput hid the list directly without touching autoCompleteViewState,
            // so renderIfChanged skips re-showing on reopen. Surface it manually when the
            // cache is for the current prefill; otherwise leave it hidden so a different
            // query's stale items don't flash.
            if (cacheRestored) {
                rootView.findViewById<RecyclerView?>(R.id.autoCompleteSuggestionsList)?.let { list ->
                    if ((list.adapter?.itemCount ?: 0) > 0) {
                        list.show()
                    }
                }
            }
        }
        attachWidget(widgetView, isBottom, tabId)
        val isNewTab = query.isEmpty() && omnibarController.getText().isEmpty()
        applyInitialTabSelection(widgetView, isNewTab, initialInputMode)
        if (omnibarController.isDuckAiMode()) {
            widgetFrom(widgetView)?.setToggleVisible(false)
        } else {
            showNtp()
        }
        inputScreenDiscoveryFunnel.onNativeInputActive()
        inputScreenDiscoveryFunnel.onInputScreenOpened()
        duckChatPixels.fireOmnibarShown()
        val landscape = rootView.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        duckChatPixels.fireOmnibarTextAreaFocused(landscape = landscape)
    }

    private fun bindSearchCallbacks(
        widgetView: View,
        callbacks: NativeInputCallbacks,
    ) {
        val widget = widgetFrom(widgetView) ?: return
        val onSearchTextChanged: (String) -> Unit = { text ->
            if (omnibarController.isDuckAiMode() && text.isBlank()) {
                callbacks.onClearAutocomplete()
            } else {
                callbacks.onSearchTextChanged(text)
            }
        }
        widget.bindInputEvents(
            onSearchTextChanged = onSearchTextChanged,
            onSearchSubmitted = { query ->
                duckChatPixels.fireOmnibarQuerySubmitted(query)
                inputScreenSessionUsageMetric.onSearchSubmitted()
                inputScreenDiscoveryFunnel.onSearchSubmitted()
                hideNativeInput(isNavigation = true)
                callbacks.onSearchSubmitted(query)
            },
            onChatSubmitted = { query ->
                if (omnibarController.isDuckAiMode()) {
                    // Already in a Duck.ai chat — every submission, URL or not, is a chat prompt
                    // that reaches the Duck.ai webview.
                    widget.saveLastUsedTogglePosition(isChat = true)
                    val imagesJson = widget.getImageAttachmentsJson()
                    val filesJson = widget.getFileAttachmentsJson()
                    widget.text = ""
                    widget.clearAttachments()
                    callbacks.onDuckAiChatSubmitted(
                        query,
                        widget.getSelectedModelId(),
                        widget.getResolvedReasoningEffort(),
                        widget.getSelectedTool(),
                        imagesJson,
                        filesJson,
                    )
                    widget.clearSelectedTool()
                    widget.onPromptSubmitted()
                    inputScreenSessionUsageMetric.onPromptSubmitted()
                    inputScreenDiscoveryFunnel.onPromptSubmitted()
                } else if (queryUrlPredictor.isUrl(query)) {
                    // Not in a Duck.ai chat (e.g. on the NTP with the Duck.ai toggle selected): a
                    // URL is an address, so navigate to it exactly like Search mode rather than
                    // opening the Duck.ai contextual sheet.
                    hideNativeInput(isNavigation = true)
                    callbacks.onSearchSubmitted(query)
                } else {
                    widget.saveLastUsedTogglePosition(isChat = true)
                    widget.storePendingPrompt(query)
                    animator.cancelAnimation()
                    rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList)?.gone()
                    rootView.findViewById<View?>(R.id.focusedView)?.gone()
                    isExiting = true
                    omnibarController.restore()
                    omnibarController.show()
                    removeWidget()
                    // Only tear down the NTP layer if we were over the browser. Under fullscreen
                    // mode the host opens the chat in a new tab (or reuses the NTP tab); under
                    // legacy mode the URL is intercepted by SpecialUrlDetector and opens an
                    // overlay fragment without webview navigation. Either way the underlying
                    // view must stay visible while we hand off.
                    if (omnibarController.isBrowserMode()) {
                        hideNtp()
                    }
                    isExiting = false
                    inputScreenSessionUsageMetric.onPromptSubmitted()
                    inputScreenDiscoveryFunnel.onPromptSubmitted()
                    callbacks.onDuckAiQuerySubmitted(query)
                }
            },
        )
        widget.onChangeModelSubmitted = { modelId -> callbacks.onChangeModelSubmitted(modelId) }
        widget.onBack = {
            // Clear focus before hiding the IME. In SEARCH_ONLY mode the input field still holds
            // focus when Back is pressed, and a focused, attached EditText remains the IME target —
            // the window re-requests the keyboard after the hide. Dropping focus first removes the
            // target so the hide sticks. In SEARCH_AND_DUCK_AI the field has already lost focus, so
            // this is a no-op there.
            widget.clearInputFocus()
            hideNativeInput()
        }
        val previousOnChatSelected = widget.onChatSelected
        widget.onChatSelected = { animate ->
            callbacks.onClearAutocomplete()
            previousOnChatSelected?.invoke(animate)
        }
        widget.onClearTextTapped = {
            if (!widget.isChatTabSelected()) {
                callbacks.onClearAutocomplete()
            }
        }
    }

    private fun showNtp() {
        rootView.findViewById<View?>(R.id.browserLayout)?.gone()
        rootView.findViewById<View?>(R.id.includeNewBrowserTab)?.show()
        rootView.findViewById<View?>(R.id.newTabContainerLayout)?.show()
    }

    private fun hideNtp() {
        rootView.findViewById<View?>(R.id.includeNewBrowserTab)?.gone()
        rootView.findViewById<View?>(R.id.newTabContainerLayout)?.gone()
        rootView.findViewById<View?>(R.id.browserLayout)?.show()
        rootView.findViewById<View?>(R.id.webViewContainer)?.show()
    }

    private fun removeWidget(): Boolean {
        var removed = false
        rootView.findViewById<View?>(R.id.inputModeTopRoot)?.let {
            rootView.removeView(it)
            removed = true
        }
        rootView.findViewById<View?>(R.id.inputModeBottomRoot)?.let {
            rootView.removeView(it)
            removed = true
        }
        floatingSubmitContainer?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            floatingSubmitContainer = null
        }
        if (removed) widgetRoot = null
        duckAiToolbarHidden = false
        // Drop Fragment-scoped callback closures so they don't outlive the widget.
        lastCallbacks = null
        return removed
    }

    private fun createWidgetView(layoutInflater: LayoutInflater, isBottom: Boolean): View {
        val layoutRes =
            if (isBottom) {
                R.layout.input_mode_widget_card_view_bottom
            } else {
                R.layout.input_mode_widget_card_view
            }
        return layoutInflater.inflate(layoutRes, rootView, false)
    }

    private fun bindWidget(
        widgetView: View,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        currentTabUrl: Flow<String?>,
        callbacks: NativeInputCallbacks,
        isBottom: Boolean,
    ) {
        widgetFrom(widgetView)?.apply {
            onStopTapped = callbacks.onStopTapped
            onFireButtonTapped = callbacks.onFireButtonPressed
            onCustomizeResponsesClicked = callbacks.onCustomizeResponsesClicked
            bindTabCount(lifecycleOwner, tabs.map { it.size })
            hideMainButtons()
            onAttachmentChooserStateChanged = { showing -> isPickingImage = showing }
            bindAttachmentCallbacks(
                onCameraCaptureRequested = callbacks.onCameraCaptureRequested,
                onFilePickerRequested = callbacks.onFilePickerRequested,
            )
            onPaidTierChanged = { isPaid ->
                val tier = if (isPaid) DuckAiTier.Paid else DuckAiTier.Free
                omnibarController.updateTierTitle(tier) {
                    fireChatHeaderUpgradeTapped(tier)
                    launchPurchase()
                }
            }
            if (!isBottom) {
                setFloatingSubmitContainer(createFloatingSubmitContainer())
            }
            // Per-tab chatId (null on new chats) published into NativeInputState for
            // consumers (reasoning picker, submission) to resolve per-chat state.
            val chatIdFlow = currentTabUrl.map { extractDuckAiChatId(it) }
            // Picker tied to whether the current tab is a Duck.ai page that already has a chatId (existing chat) or new chat.
            bindModelPickerEnabledSource(chatIdFlow.map { it == null })
            bindChatIdSource(chatIdFlow)
            bindInteractionLockSource(interactionLockSource)
            bindDuckAiFireButtonHighlightSource(duckAiFireButtonHighlightSource)
        }
        bindSearchCallbacks(widgetView, callbacks)
        bindAutocompleteVisibility(widgetView)
        bindChatSuggestions(widgetView, lifecycleOwner, callbacks)
        bindSearchTabAutocompleteClearing(widgetView, callbacks.onClearAutocomplete)
        bindVoiceButtons(widgetView, callbacks)
    }

    private fun bindVoiceButtons(
        widgetView: View,
        callbacks: NativeInputCallbacks,
    ) {
        val widget = widgetFrom(widgetView) ?: return
        updateVoiceButtons(widget)
        val previousOnSearchSelected = widget.onSearchSelected
        widget.onSearchSelected = { animate ->
            updateVoiceButtons(widget)
            previousOnSearchSelected?.invoke(animate)
        }
        val previousOnChatSelected = widget.onChatSelected
        widget.onChatSelected = { animate ->
            updateVoiceButtons(widget)
            previousOnChatSelected?.invoke(animate)
        }
        widget.onVoiceSearchClick = {
            callbacks.onVoiceSearchPressed(widget.isChatTabSelected())
        }
        widget.onVoiceChatClick = {
            hideNativeInput(animate = false)
            duckChat.openVoiceDuckChat()
        }
    }

    private fun updateVoiceButtons(widget: NativeInputWidget) {
        val state = computeVoiceButtonAvailability(
            isOnActiveDuckChat = omnibarController.isDuckAiMode(),
            isVoiceSearchDeviceAvailable = voiceSearchAvailability.isVoiceSearchAvailable,
            isVoiceSearchDuckAiEnabled = duckAiFeatureState.showVoiceSearchToggle.value,
            isVoiceChatEntryEnabled = duckAiFeatureState.showVoiceChatEntry.value,
            isDuckAiTabSelected = widget.isChatTabSelected(),
        )
        widget.setVoiceSearchAvailable(state.voiceSearchAvailable)
        widget.setVoiceChatAvailable(state.voiceChatAvailable)
    }

    private fun bindSearchTabAutocompleteClearing(
        widgetView: View,
        onClearAutocomplete: () -> Unit,
    ) {
        val widget = widgetFrom(widgetView) ?: return
        val previousOnSearchSelected = widget.onSearchSelected
        widget.onSearchSelected = handler@{ animate ->
            if (widget.text.isBlank()) {
                onClearAutocomplete()
                if (omnibarController.isDuckAiMode()) {
                    return@handler
                }
            }
            previousOnSearchSelected?.invoke(animate)
        }
    }

    private fun applyInitialTabSelection(widgetView: View, isNewTab: Boolean, initialInputMode: InputMode?) {
        val widget = widgetFrom(widgetView) ?: return
        when (initialInputMode) {
            InputMode.DUCK_AI -> widget.selectChatTab()
            InputMode.SEARCH -> widget.selectSearchTab()
            null -> if (omnibarController.isDuckAiMode()) {
                widget.selectChatTab()
            } else if (isNewTab) {
                widget.applyDefaultTogglePosition()
            }
        }
    }

    private fun bindAutocompleteVisibility(widgetView: View) {
        if (!omnibarController.isDuckAiMode()) return
        val widget = widgetFrom(widgetView) ?: return
        val autoCompleteList =
            rootView.findViewById<RecyclerView?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)
        val previousOnChatSelected = widget.onChatSelected
        widget.onChatSelected = { animate ->
            previousOnChatSelected?.invoke(animate)
            autoCompleteList.gone()
            focusedView?.gone()
        }
    }

    private fun attachWidget(widgetView: View, isBottom: Boolean, tabId: String) {
        rootView.addView(widgetView, layoutCoordinator.buildWidgetLayoutParams(isBottom))
        widgetRoot = widgetView

        widgetFrom(widgetView)?.apply {
            setWidgetRootView(widgetView)
            configure(tabId = tabId, isDuckAiMode = omnibarController.isDuckAiMode(), isBottom = isBottom)
        }

        applyWindowChrome(widgetView, isBottom)

        if (!startEnterAnimation(widgetView, isBottom)) {
            animator.applyLayoutTransitions(widgetView, isBottom)
            onEnterComplete(widgetView)
        }
    }

    override fun setInteractionLock(lock: InteractionLock) {
        interactionLockSource.value = lock
    }

    override fun setDuckAiFireButtonHighlighted(highlighted: Boolean) {
        duckAiFireButtonHighlightSource.value = highlighted
    }

    private fun suppressShadow(view: View) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRect(0, 0, v.width, v.height)
                outline.alpha = 0f
            }
        }
    }

    private fun applyWindowChrome(widgetView: View, isBottom: Boolean) {
        widgetView.translationZ = WIDGET_ELEVATION_DP.toPx()
        if (isBottom) {
            rootView.findViewById<View?>(R.id.navigationBar)?.gone()
            rootView.findViewById<View?>(R.id.bottomBrowserOutlineStroke)?.gone()
            if (omnibarController.isBrowserMode()) {
                widgetView.setBackgroundColor(
                    widgetView.context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorBackground),
                )
            } else if (omnibarController.isDuckAiMode()) {
                widgetView.setBackgroundColor(
                    widgetView.context.getColorFromAttr(
                        com.duckduckgo.mobile.android.R.attr.daxColorDuckAiBackground,
                    ),
                )
                suppressShadow(widgetView)
            }
            rootView.findViewById<View?>(R.id.browserLayout)?.let {
                it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, 0)
            }
        }
        layoutCoordinator.configureAutocompleteLayout(widgetView, isBottom)
        layoutCoordinator.configureContentOffset(widgetView, isBottom)
        widgetView.post { layoutCoordinator.applyForcedBottomTranslation(widgetView, isBottom) }
    }

    private fun startEnterAnimation(widgetView: View, isBottom: Boolean): Boolean {
        if (omnibarController.isDuckAiMode()) return false
        val widgetCard = widgetView.findViewById<View?>(R.id.inputModeWidgetCard) ?: return false
        val omnibarCard = omnibarController.getCardView() ?: return false
        // Apply focused-state layout so the widget is measured at its final size; otherwise
        // padding/bottom-row/toggle-row visibility land after the 200ms enter as a second step.
        widgetFrom(widgetView)?.beginEnterAnimationPreview(isBottom)
        val margins = animator.init(widgetCard, omnibarCard, omnibarCard.width, omnibarCard.height, isBottom)
            ?: return false

        layoutCoordinator.setWidgetAnimating(true)
        animator.animateEnter(
            widgetCard = widgetCard,
            omnibarCard = omnibarCard,
            widgetView = widgetView,
            margins = margins,
            onUpdate = { layoutCoordinator.onWidgetAnimationFrame(widgetCard) },
            onCancel = {
                layoutCoordinator.setWidgetAnimating(false)
                widgetFrom(widgetView)?.let { widget ->
                    widget.endEnterAnimationPreview()
                    if (widget.isWidgetBottom()) {
                        widget.clearInputFocus()
                    }
                }
            },
            onComplete = {
                layoutCoordinator.setWidgetAnimating(false)
                onEnterComplete(widgetView)
            },
        )
        return true
    }

    private fun onEnterComplete(widgetView: View) {
        layoutCoordinator.enableContentLayoutTransition()
        if (omnibarController.isDuckAiMode()) return
        // Skip the IME-raising work if the widget has been detached before the animation
        // finished (e.g. fragment-manager transient detach during a tab switch). Otherwise
        // focusInput would raise the keyboard on whatever tab is now in front.
        if (!widgetView.isAttachedToWindow) return
        omnibarController.hide()
        widgetFrom(widgetView)?.apply {
            focusInput(rootView.context as? Activity)
            endEnterAnimationPreview()
        }
    }

    private fun bindChatSuggestions(
        widgetView: View,
        lifecycleOwner: LifecycleOwner,
        callbacks: NativeInputCallbacks,
    ) {
        if (omnibarController.isDuckAiMode()) return
        val widget = widgetFrom(widgetView) ?: return
        val autoCompleteList =
            rootView.findViewById<RecyclerView?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)
        var adapter: RecyclerView.Adapter<*>? = null
        widget.bindChatSuggestions(
            lifecycleOwner = lifecycleOwner,
            onChatSuggestionSelected = { query ->
                hideNativeInput(animate = false, isNavigation = true)
                callbacks.onChatSuggestionSelected(query)
            },
            onChatUrlSuggestionClicked = { suggestion ->
                hideNativeInput(isNavigation = true)
                callbacks.onChatUrlSuggestionClicked(suggestion)
            },
            onSearchForQuerySubmitted = { query ->
                hideNativeInput(isNavigation = true)
                callbacks.onSearchSubmitted(query)
            },
            onChatHistoryShortcutClicked = {
                hideNativeInput(isNavigation = true)
                callbacks.onChatHistoryShortcutClicked()
            },
            onShowSuggestions = { chatAdapter ->
                if (autoCompleteList.adapter === chatAdapter) {
                    // Force a fresh layout pass so the adapter behaviour
                    // doesn't leave the user scrolled to a stale position when
                    // URL items appear at position 0.
                    autoCompleteList.swapAdapter(chatAdapter, true)
                } else {
                    adapter = adapter ?: autoCompleteList.adapter
                    autoCompleteList.adapter = chatAdapter
                    autoCompleteList.itemAnimator = null
                }
                autoCompleteList.show()
                focusedView?.gone()
            },
            onClearSuggestions = { hideList ->
                adapter?.let { adapter ->
                    if (autoCompleteList.adapter != adapter) {
                        autoCompleteList.adapter = adapter
                    }
                }
                if (hideList) {
                    autoCompleteList.gone()
                    focusedView?.gone()
                }
            },
        )
    }

    private fun createFloatingSubmitContainer(): ViewGroup {
        val activity = rootView.context as? Activity ?: return FrameLayout(rootView.context)
        val contentView = activity.findViewById<FrameLayout>(android.R.id.content)
        return FrameLayout(rootView.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = 6f.toPx(rootView.context).toInt()
                bottomMargin = 4f.toPx(rootView.context).toInt()
            }
            elevation = WIDGET_ELEVATION_DP.toPx()
        }.also {
            contentView.addView(it)
            floatingSubmitContainer = it
        }
    }

    private fun launchPurchase() {
        globalActivityStarter.start(rootView.context, SubscriptionPurchase(featurePage = DUCK_AI_FEATURE_PAGE))
    }

    /**
     * Fired when the user taps the "Upgrade" pill in the Duck.ai omnibar header. The pill is only shown
     * for [DuckAiTier.Free] (subscription inactive), so this is the chat-header upgrade entry point.
     */
    internal fun fireChatHeaderUpgradeTapped(tier: DuckAiTier) {
        val userTier = if (tier is DuckAiTier.Paid) "plus" else "free"
        pixel.fire(AppPixelName.AI_CHAT_UNIFIED_INPUT_CHAT_HEADER_UPGRADE_TAPPED, mapOf("user_tier" to userTier))
    }

    /** True if [rawUrl] points at an in-progress Duck.ai chat (Duck.ai URL with a non-blank `chatID`). */
    internal fun isExistingDuckAiChat(rawUrl: String?): Boolean = extractDuckAiChatId(rawUrl) != null

    /** Returns the `chatID` query param if [rawUrl] is a Duck.ai chat URL, else `null`. */
    internal fun extractDuckAiChatId(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val uri = runCatching { rawUrl.toUri() }.getOrNull() ?: return null
        return uri.toChatIdOrNull(duckChat)
    }

    companion object {
        private const val WIDGET_ELEVATION_DP = 8f
        private const val FADE_OUT_DURATION_MS = 150L
        private const val DUCK_AI_FEATURE_PAGE = "duckai"
    }
}

internal data class VoiceButtonAvailability(
    val voiceSearchAvailable: Boolean,
    val voiceChatAvailable: Boolean,
)

/**
 * Pure decision logic for which voice entry points the unified input should expose.
 *
 * Rules:
 * - On an active Duck.ai chat page, voice chat is suppressed (you're already in the chat). Voice
 *   search is offered only if both the device supports it and the Duck.ai voice-search flag is on.
 * - Otherwise (NTP / search omnibar with the Search↔Duck.ai toggle):
 *   - Search tab: voice search if device-available.
 *   - Duck.ai tab: voice search if device-available AND [isVoiceSearchDuckAiEnabled]; voice chat
 *     if [isVoiceChatEntryEnabled]. The two are independent now that they occupy separate slots
 *     (in-field microphone vs. bottom-row chip).
 */
internal fun computeVoiceButtonAvailability(
    isOnActiveDuckChat: Boolean,
    isVoiceSearchDeviceAvailable: Boolean,
    isVoiceSearchDuckAiEnabled: Boolean,
    isVoiceChatEntryEnabled: Boolean,
    isDuckAiTabSelected: Boolean,
): VoiceButtonAvailability {
    if (isOnActiveDuckChat) {
        return VoiceButtonAvailability(
            voiceSearchAvailable = isVoiceSearchDeviceAvailable && isVoiceSearchDuckAiEnabled,
            voiceChatAvailable = false,
        )
    }
    return VoiceButtonAvailability(
        voiceSearchAvailable = isVoiceSearchDeviceAvailable && (!isDuckAiTabSelected || isVoiceSearchDuckAiEnabled),
        voiceChatAvailable = isDuckAiTabSelected && isVoiceChatEntryEnabled,
    )
}
