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
import androidx.core.view.doOnAttach
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
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
import com.duckduckgo.browser.ui.tabs.TabSwitcherButton
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.InputMode
import com.duckduckgo.duckchat.api.NativeInputEventListener
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InteractionLock
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.NativeInputWidget
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
    val onChatSuggestionDelete: (chatUrl: String) -> Unit = {},
    val onClearAutocomplete: () -> Unit,
    val onStopTapped: () -> Unit,
    val onFireButtonPressed: () -> Unit = {},
    val onTabSwitcherPressed: () -> Unit = {},
    val onBrowserMenuPressed: () -> Unit = {},
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

    /**
     * Whether the native input should actually be used for the current context, as opposed to the
     * legacy omnibar. True when the field is enabled AND either we're in Duck.ai or the config is not
     * search-only. In search-only mode the browser omnibar falls back to the legacy text input.
     */
    fun isNativeInputActive(): Boolean

    /** True when the native input widget is currently attached (top or bottom omnibar). */
    fun isNativeInputShown(): Boolean

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

    /**
     * Re-fetches the omnibar chat-history suggestions from the source of truth, e.g. after a
     * single-chat delete has completed and the chat is actually gone from the store. No-op when
     * the native input isn't shown or the chat tab isn't selected.
     */
    fun refreshChatSuggestions()

    /** The user confirmed deleting a recent chat from the chat-autocomplete fire dialog. */
    fun onChatDeleteConfirmed()

    /** The user cancelled deleting a recent chat from the chat-autocomplete fire dialog. */
    fun onChatDeleteCancelled()

    /** Lock the input field (making it non-interactive + dimmed).*/
    fun setInteractionLock(lock: InteractionLock)

    /** Show pulse animation around the Duck.ai fire button. */
    fun setDuckAiFireButtonHighlighted(highlighted: Boolean)

    /** Hide/show the subscription-tier indicator in the Duck.ai header (hidden during the onboarding lock). */
    fun setDuckAiTierVisible(visible: Boolean)
}

@ContributesBinding(FragmentScope::class)
class RealNativeInputManager @Inject constructor(
    private val duckChat: DuckChat,
    private val animator: NativeInputAnimator,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val globalActivityStarter: GlobalActivityStarter,
    private val queryUrlPredictor: QueryUrlPredictor,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val duckChatInputModeState: DuckChatInputModeState,
    private val pixel: Pixel,
    private val nativeInputStateBugKillSwitch: NativeInputStateBugKillSwitch,
    private val nativeInputEventListener: NativeInputEventListener,
) : NativeInputManager {
    private lateinit var omnibarController: NativeInputOmnibarController
    private lateinit var rootView: ViewGroup
    private lateinit var layoutCoordinator: NativeInputLayoutCoordinator
    private var isNativeInputFieldEnabled: Boolean = false
    private var isNativeChatInputEnabled: Boolean = false
    private var isNavBarFeatureEnabled: Boolean = false
    private var inputModeCapability: NativeInputState.InputMode = NativeInputState.InputMode.SEARCH_ONLY
    private var isExiting: Boolean = false
    private var isPickingImage: Boolean = false
    private var duckAiToolbarHidden: Boolean = false
    private var floatingSubmitContainer: View? = null
    private var widgetRoot: View? = null
    private var navBarRoot: View? = null
    private var navBarShown: Boolean? = null

    // The nav bar is shown once when the browser input opens empty, then latched off the first time the
    // user types. Latched stays off for the session — clearing text or toggling the keyboard won't bring it back.
    private var navBarInteractionLatched: Boolean = false
    private var navBarHeightPx: Int = 0
    private var navBarIsBottom: Boolean = false
    private var navBarTabCountLiveData: LiveData<Int>? = null
    private var navBarTabCountObserver: Observer<Int>? = null
    private var lastCallbacks: NativeInputCallbacks? = null

    /** True while an enter morph from [attachWidget] still owns [NativeInputLayoutCoordinator]'s animating flag. */
    private var pendingEnterOwnsAnimating = false

    // The NTP top stroke is driven by hasFavorites, so we save its visibility on attach and restore it
    // on detach rather than re-showing unconditionally (which would show it with no favorites present).
    private var savedTopNtpStrokeVisibility: Int? = null

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
                if (isNativeInputFieldEnabled && !isEnabled) {
                    // Instant teardown before clearing the flag. Settings toggles often fire while the
                    // tab is paused, so an animated exit's waitForLayout may never run; and once the
                    // flag is false, hideNativeInput used to no-op and leave UTI chrome / a missing
                    // address field on return to NTP.
                    hideNativeInput(animate = false)
                    onDisabled()
                }
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
        duckChatInputModeState.inputModeCapability
            .onEach {
                inputModeCapability = it
                // A live switch to Search-only means the browser no longer uses native input. Tear down an
                // open widget so the legacy omnibar returns instead of lingering as a toggle-less widget
                // until the user closes and refocuses (hideNativeInput no-ops when nothing is shown).
                // Otherwise re-evaluate the nav bar for the new mode.
                if (!isNativeInputActive()) {
                    hideNativeInput(animate = false)
                } else {
                    refreshNavBarVisibility()
                }
            }
            .launchIn(lifecycleOwner.lifecycleScope)
        duckChat.observeNativeInputNavBarEnabled()
            .onEach {
                isNavBarFeatureEnabled = it
                refreshNavBarVisibility()
            }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun isNativeInputEnabled(): Boolean = isNativeInputFieldEnabled

    override fun isNativeInputActive(): Boolean {
        if (!isNativeInputFieldEnabled) return false
        // Duck.ai always uses the native input; the browser omnibar only does so outside search-only.
        val inDuckAi = ::omnibarController.isInitialized && omnibarController.isDuckAiMode()
        return inDuckAi || inputModeCapability != NativeInputState.InputMode.SEARCH_ONLY
    }

    override fun isNativeInputShown(): Boolean {
        if (!::rootView.isInitialized) return false
        return widgetRoot != null ||
            rootView.findViewById<View?>(R.id.inputModeTopRoot) != null ||
            rootView.findViewById<View?>(R.id.inputModeBottomRoot) != null
    }

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

    override fun refreshChatSuggestions() {
        if (!::rootView.isInitialized) return
        val widget = widgetFrom(rootView) ?: return
        widget.refreshChatSuggestions()
    }

    override fun onChatDeleteConfirmed() {
        if (!::rootView.isInitialized) return
        val widget = widgetFrom(rootView) ?: return
        widget.onChatDeleteConfirmed()
    }

    override fun onChatDeleteCancelled() {
        if (!::rootView.isInitialized) return
        val widget = widgetFrom(rootView) ?: return
        widget.onChatDeleteCancelled()
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
        if (!::rootView.isInitialized) return false

        val widgetView = rootView.findViewById<View?>(R.id.inputModeTopRoot)
            ?: rootView.findViewById(R.id.inputModeBottomRoot)
            ?: return false

        // Do not require isNativeInputFieldEnabled: teardown must still run after the setting flips
        // off (and after a paused animated hide left the widget attached).

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

        // Bottom omnibar: slide the nav bar out with the close (visual-only — exit owns reflow /
        // isWidgetAnimating, so no begin/endNavBarSlide). Top omnibar: leave the bar in place for
        // the exit morph so the card morphs back to the omnibar while the buttons stay put
        // (removed with the widget in removeWidget) — sliding it would steal that animation.
        if (navBarShown == true && navBarIsBottom) {
            slideNavBarOutWithClose(animate = animate)
        }

        if (!animate) {
            animator.cancelAnimation()
            isExiting = false
            // Match animated exit: suspend LayoutTransition before resetting offsets so CHANGING
            // can't leave a leftover NTP gap (e.g. live switch to Search-only while UTI is open on
            // bottom omnibar with a nav-bar top inset).
            layoutCoordinator.suspendContentReflow()
            layoutCoordinator.updateNavBarInset(0)
            layoutCoordinator.resetContentOffsetToBase()
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
            // Bottom: nav bar may already be sliding out on its own timeline. Top: bar stays put —
            // this exit only morphs the input card back to the omnibar. Suspend the content-reflow
            // transition so the per-frame content offset driven by onWidgetAnimationFrame is instant;
            // otherwise it animates on the transition's own clock and the reset-to-base races, leaving
            // stale top padding.
            layoutCoordinator.suspendContentReflow()
            animator.animateExit(
                widgetCard = card,
                widgetView = widgetView,
                omnibarCard = omnibarCard,
                isBottom = isBottom,
                onUpdate = { layoutCoordinator.onWidgetAnimationFrame(card) },
                onCancel = {
                    layoutCoordinator.setWidgetAnimating(false)
                    layoutCoordinator.resumeContentReflow()
                },
                onComplete = {
                    // Reset content under the still-suspended reflow and keep isWidgetAnimating true
                    // until removeWidget's detach: clearing the animating flag here lets layout
                    // listeners re-apply the exit-end inset during the fade and race the detach reset,
                    // which is what left intermittent leftover space under the omnibar.
                    layoutCoordinator.resetContentOffsetToBase()
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
        // Nav bar visibility is decoupled from the keyboard: it's decided at open (empty → shown) and only
        // hidden on the first keystroke. Toggling the keyboard neither shows nor hides it.
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
            if (!isExternalKeyboardConnected()) {
                updateWidgetFocus(widget)
            }
        }
    }

    private fun isExternalKeyboardConnected(): Boolean =
        rootView.resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS

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
        // The nav bar is a first-focus affordance for the empty browser input. It belongs to browser input
        // only (Duck.ai / contextual never show it), is gated behind the nativeInputNavBar flag and the
        // Search & Duck.ai mode, and is only created when the field opens empty — focusing a site (URL
        // prefilled) never gets one. Fresh session, so the interaction latch resets.
        navBarInteractionLatched = false
        val createNavBar = shouldCreateNavBar(isNavBarFeatureEnabled, omnibarController.isDuckAiMode(), inputModeCapability) &&
            prefillText.isEmpty()
        val navBarView = if (createNavBar) createNavBarView(layoutInflater) else null
        bindWidget(widgetView, lifecycleOwner, tabs, currentTabUrl, callbacks, isBottom)
        if (navBarView != null) bindNavBar(navBarView, widgetView, lifecycleOwner, tabs, callbacks)
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
        attachWidget(widgetView, navBarView, isBottom, tabId)
        // Bottom omnibar: slide the nav bar in with open. Top omnibar: snap the bar so the enter
        // morph can run from the omnibar while the buttons appear without animating — a concurrent
        // top slide fights that morph (and was only needed for bottom chrome).
        applyNavBarVisibility(
            show = navBarShouldBeVisible(),
            animate = isBottom,
            // Enter morph (when started) owns isWidgetAnimating until it completes; clearing it when
            // an open slide finishes first would re-enable layout listeners mid-enter.
            clearAnimatingOnComplete = !pendingEnterOwnsAnimating,
            // Never ride the widget during open — bottom never does; top must stay planted for morph.
            moveWidgetWithBar = false,
        )
        syncBackArrowToNavBar()
        lifecycleOwner.lifecycleScope.launch {
            if (isDuckAiSettingsUrl(currentTabUrl.firstOrNull())) widgetView.gone()
        }
        val isNewTab = query.isEmpty() && omnibarController.getText().isEmpty()
        applyInitialTabSelection(widgetView, isNewTab, initialInputMode)
        if (omnibarController.isDuckAiMode()) {
            widgetFrom(widgetView)?.setToggleVisible(false)
        } else {
            showNtp()
        }
        val landscape = rootView.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        nativeInputEventListener.onNativeInputShown(landscape = landscape)
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
            // Fires on every keystroke regardless of the selected tab (search text routes to
            // onSearchTextChanged, chat text to onChatTextChanged), so this is the tab-agnostic signal
            // that latches the nav bar off on the user's first input.
            onInputTextEmptyChanged = { isEmpty -> if (!isEmpty) onNavBarInputInteraction() },
            onSearchSubmitted = { query ->
                nativeInputEventListener.onSearchSubmitted(query)
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
                    nativeInputEventListener.onChatPromptSubmitted()
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
                    nativeInputEventListener.onChatPromptSubmitted()
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
        // Clearing the field must not dismiss the focused view: onClearAutocomplete re-triggers
        // autocomplete with hasFocus=false and hides the focused view, so on a browser tab it would
        // wipe the favourites the empty+focused state should show. The clear's own text change already
        // re-renders the correct empty state (favourites), so no extra handling is needed here.
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
        animator.cancelAnimation()
        var removed = false
        rootView.findViewById<View?>(R.id.inputModeTopRoot)?.let {
            rootView.removeView(it)
            removed = true
        }
        rootView.findViewById<View?>(R.id.inputModeBottomRoot)?.let {
            rootView.removeView(it)
            removed = true
        }
        rootView.findViewById<View?>(R.id.inputModeWidgetNavLayout)?.let {
            rootView.removeView(it)
            navBarRoot = null
        }
        navBarShown = null
        pendingEnterOwnsAnimating = false
        navBarTabCountObserver?.let { existing -> navBarTabCountLiveData?.removeObserver(existing) }
        navBarTabCountObserver = null
        navBarTabCountLiveData = null
        floatingSubmitContainer?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            floatingSubmitContainer = null
        }
        if (removed) widgetRoot = null
        savedTopNtpStrokeVisibility?.let { vis ->
            rootView.findViewById<View?>(R.id.topNtpOutlineStroke)?.visibility = vis
            savedTopNtpStrokeVisibility = null
        }
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

    private fun createNavBarView(layoutInflater: LayoutInflater): View {
        return layoutInflater.inflate(R.layout.input_mode_widget_nav_bar, rootView, false)
    }

    private fun bindNavBar(
        navBarView: View,
        widgetView: View,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        callbacks: NativeInputCallbacks,
    ) {
        bindInputModeNavBar(
            navBarView = navBarView,
            // Route through the widget's Back handler so it drops focus (IME hide sticks), closes, and
            // fires the same back-button pixel — telemetry stays consistent whether the user taps this
            // nav bar back (empty field) or the toggle-row back (with text).
            onBack = { widgetFrom(widgetView)?.onBackPressed() },
            onFire = callbacks.onFireButtonPressed,
            onTabs = callbacks.onTabSwitcherPressed,
            onBrowserMenu = callbacks.onBrowserMenuPressed,
        )
        bindNavBarTabCount(navBarView, lifecycleOwner, tabs)
    }

    /**
     * Mirrors [NativeInputWidget.bindTabCount]: the fragment's viewLifecycleOwner is reused across
     * show/hide cycles, so the previous observer is removed before re-observing — otherwise each cycle
     * stacks an observer that keeps updating a detached nav bar button.
     */
    private fun bindNavBarTabCount(
        navBarView: View,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
    ) {
        val tabsButton = navBarView.findViewById<TabSwitcherButton?>(R.id.inputModeWidgetNavTabs) ?: return
        navBarTabCountObserver?.let { existing -> navBarTabCountLiveData?.removeObserver(existing) }
        val tabCount = tabs.map { it.size }
        val observer = Observer<Int> { count -> tabsButton.count = count }
        navBarTabCountLiveData = tabCount
        navBarTabCountObserver = observer
        tabCount.observe(lifecycleOwner, observer)
        tabsButton.count = tabCount.value ?: 0
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
            onPaidTierChanged = { isPaid, isSubscriptionEligible ->
                val tier = when {
                    isPaid -> DuckAiTier.Paid
                    isSubscriptionEligible -> DuckAiTier.Free
                    else -> DuckAiTier.FreeNoUpgrade
                }
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

    /** Current on-screen visibility the nav bar should have: still browser + flag + Search & Duck.ai (so a
     *  live mode/flag change hides it) and not yet latched off by the user's first keystroke. */
    private fun navBarShouldBeVisible(): Boolean =
        shouldCreateNavBar(isNavBarFeatureEnabled, omnibarController.isDuckAiMode(), inputModeCapability) &&
            shouldShowNavBar(isBrowserContext = navBarRoot != null, interactionLatched = navBarInteractionLatched)

    /** Re-applies the nav bar visibility for the current input state; no-op when no bar is attached. */
    private fun refreshNavBarVisibility() {
        if (navBarRoot == null) return
        applyNavBarVisibility(show = navBarShouldBeVisible(), animate = true)
        syncBackArrowToNavBar()
    }

    /** The toggle-row back arrow is the inverse of the nav bar: it fills in as the back affordance only
     *  while the nav bar is hidden, so exactly one back arrow shows at a time. */
    private fun syncBackArrowToNavBar() {
        widgetFrom(rootView)?.setNavBarVisible(navBarShown == true)
    }

    /** The user typed: latch the nav bar off for the rest of this input session and slide it out. */
    private fun onNavBarInputInteraction() {
        if (navBarInteractionLatched || navBarRoot == null) return
        navBarInteractionLatched = true
        refreshNavBarVisibility()
    }

    /**
     * Shows/hides the persistent nav bar. No-op when there is no bar (non-browser context) or the bar
     * is already in the target state. On change it slides the bar and reflows content to clear or
     * reclaim the bar strip. In top mode, mid-session toggles also translate the widget with the bar
     * unless [moveWidgetWithBar] is false (enter/exit morph — card must stay put relative to omnibar).
     *
     * @param clearAnimatingOnComplete when true (default), clears [NativeInputLayoutCoordinator]'s
     * animating flag after the slide — correct for mid-session toggles. Pass false when the slide
     * runs alongside enter/exit, which already owns that flag.
     * @param moveWidgetWithBar top mid-session only. Bottom mode never moves the widget with the bar.
     */
    private fun applyNavBarVisibility(
        show: Boolean,
        animate: Boolean,
        clearAnimatingOnComplete: Boolean = true,
        moveWidgetWithBar: Boolean = !navBarIsBottom,
    ) {
        val navBar = navBarRoot ?: return
        val widget = widgetRoot ?: return
        if (!shouldAnimateNavBar(navBarShown, show)) return
        navBarShown = show
        val navBarInset = if (show) navBarHeightPx else 0
        if (!animate) {
            animator.animateNavBarVisibility(
                navBarView = navBar,
                widgetView = widget,
                isBottom = navBarIsBottom,
                heightPx = navBarHeightPx,
                show = show,
                animate = false,
                moveWidgetWithBar = moveWidgetWithBar,
                onComplete = { layoutCoordinator.updateNavBarInset(navBarInset) },
            )
            return
        }
        // When the open slide shares the session with enter, skip begin/endNavBarSlide: enter already
        // owns isWidgetAnimating, and endNavBarSlide's resume would clear the LayoutTransition that
        // onEnterComplete just assigned. Mid-session toggles (default) take full ownership.
        if (clearAnimatingOnComplete) {
            layoutCoordinator.beginNavBarSlide()
        }
        animator.animateNavBarVisibility(
            navBarView = navBar,
            widgetView = widget,
            isBottom = navBarIsBottom,
            heightPx = navBarHeightPx,
            show = show,
            animate = true,
            moveWidgetWithBar = moveWidgetWithBar,
            onFrame = { onScreenPx -> layoutCoordinator.updateNavBarInset(onScreenPx) },
            onComplete = {
                layoutCoordinator.updateNavBarInset(navBarInset)
                if (clearAnimatingOnComplete) {
                    layoutCoordinator.endNavBarSlide()
                    layoutCoordinator.setWidgetAnimating(false)
                }
            },
        )
    }

    /**
     * Hides the nav bar as UTI closes. Unlike [applyNavBarVisibility], this does not take ownership of
     * content reflow — the exit morph already suspends it and resets padding on complete. Calling
     * [NativeInputLayoutCoordinator.endNavBarSlide] here would resume reflow mid-exit.
     *
     * Never moves the widget with the bar: exit morph needs the card planted so it can morph back to
     * the omnibar while the buttons slide away alone. Still drives [NativeInputLayoutCoordinator.updateNavBarInset]
     * each frame so NTP/browser content tracks the sliding bar instead of keeping the full inset until
     * exit's reset-to-base.
     */
    private fun slideNavBarOutWithClose(animate: Boolean) {
        val navBar = navBarRoot ?: return
        val widget = widgetRoot ?: return
        if (!shouldAnimateNavBar(navBarShown, targetShown = false)) return
        navBarShown = false
        animator.animateNavBarVisibility(
            navBarView = navBar,
            widgetView = widget,
            isBottom = navBarIsBottom,
            heightPx = navBarHeightPx,
            show = false,
            animate = animate,
            moveWidgetWithBar = false,
            onFrame = { onScreenPx -> layoutCoordinator.updateNavBarInset(onScreenPx) },
            onComplete = { layoutCoordinator.updateNavBarInset(0) },
        )
    }

    private fun attachWidget(widgetView: View, navBarView: View?, isBottom: Boolean, tabId: String) {
        // Inflated from a ?attr/actionBarSize height, so layoutParams carries the resolved nav bar height.
        val navBarHeightPx = navBarView?.layoutParams?.height?.takeIf { it > 0 } ?: 0
        this.navBarHeightPx = navBarHeightPx
        this.navBarIsBottom = isBottom
        if (navBarView != null) {
            rootView.addView(navBarView, layoutCoordinator.buildNavBarLayoutParams(navBarHeightPx))
            // Start off-screen so the first apply can slide the bar in (or snap it) instead of
            // flashing it at rest for a frame. -height matches animateNavBarVisibility's hiddenY.
            navBarView.translationY = -navBarHeightPx.toFloat()
            // Bottom omnibar only: the autocomplete list overlaps the top strip and would draw over the
            // bar (e.g. the Duck.ai tab's suggestions), so float it above via translationZ. Suppress the
            // shadow the raised Z would otherwise cast, we only want the draw order, not the elevation.
            // Top omnibar has no such overlap, leave it flat.
            if (isBottom) {
                navBarView.translationZ = WIDGET_ELEVATION_DP.toPx()
                suppressShadow(navBarView)
            }
        }
        // Top mode offsets the widget below the nav bar so it isn't overlapped; bottom mode is unaffected.
        // Do not translate the widget off-screen with the bar: enter morph needs it planted so the card
        // can grow from the omnibar while only the bar (buttons) slides in.
        rootView.addView(widgetView, layoutCoordinator.buildWidgetLayoutParams(isBottom, topInsetPx = navBarHeightPx))
        widgetRoot = widgetView
        navBarRoot = navBarView

        widgetFrom(widgetView)?.apply {
            setWidgetRootView(widgetView)
            configure(tabId = tabId, isDuckAiMode = omnibarController.isDuckAiMode(), isBottom = isBottom)
        }

        applyWindowChrome(widgetView, isBottom)

        val enterStarted = startEnterAnimation(widgetView, isBottom)
        if (!enterStarted) {
            animator.applyLayoutTransitions(widgetView, isBottom)
            onEnterComplete(widgetView)
        }
        // Stash so showNativeInput can avoid clearing isWidgetAnimating when the open slide
        // finishes before the enter morph.
        pendingEnterOwnsAnimating = enterStarted
    }

    override fun setInteractionLock(lock: InteractionLock) {
        interactionLockSource.value = lock
    }

    override fun setDuckAiFireButtonHighlighted(highlighted: Boolean) {
        duckAiFireButtonHighlightSource.value = highlighted
    }

    override fun setDuckAiTierVisible(visible: Boolean) {
        if (::omnibarController.isInitialized) omnibarController.setTierVisible(visible)
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
            // The top outline strokes separate a top omnibar from content; with the input's nav bar at
            // the top they just draw a hairline under the bar. Hide them, restored on close.
            rootView.findViewById<View?>(R.id.topBrowserOutlineStroke)?.gone()
            rootView.findViewById<View?>(R.id.topNtpOutlineStroke)?.let {
                if (savedTopNtpStrokeVisibility == null) savedTopNtpStrokeVisibility = it.visibility
                it.gone()
            }
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
            // Once the morph has the omnibar's screen position, hide it immediately when a nav bar
            // is replacing the chrome — otherwise the omnibar sits on top of the bar for the whole
            // enter and pops away at the end (the flicker).
            onStart = {
                if (navBarRoot != null) {
                    omnibarController.hide()
                }
            },
            onCancel = {
                pendingEnterOwnsAnimating = false
                layoutCoordinator.setWidgetAnimating(false)
                widgetFrom(widgetView)?.let { widget ->
                    widget.endEnterAnimationPreview()
                    if (widget.isWidgetBottom()) {
                        widget.clearInputFocus()
                    }
                }
            },
            onComplete = {
                pendingEnterOwnsAnimating = false
                layoutCoordinator.setWidgetAnimating(false)
                onEnterComplete(widgetView)
            },
        )
        return true
    }

    private fun onEnterComplete(widgetView: View) {
        layoutCoordinator.enableContentLayoutTransition()
        if (omnibarController.isDuckAiMode()) return
        if (widgetView.isAttachedToWindow) {
            completeEnter(widgetView)
            return
        }
        // The enter finished while the widget was transiently detached — e.g. the ViewPager re-attaches
        // fragments when a new tab is created. Raising the IME now could target whatever tab is in front,
        // so defer the focus until this widget re-attaches. Hide the omnibar immediately so the tab can't
        // settle with both the omnibar and the widget visible, and finish focus on re-attach (guarded so a
        // since-replaced widget is a no-op).
        omnibarController.hide()
        widgetView.doOnAttach {
            if (widgetRoot === widgetView && !omnibarController.isDuckAiMode()) {
                completeEnter(widgetView)
            }
        }
    }

    private fun completeEnter(widgetView: View) {
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
            onChatSuggestionDelete = { chatUrl ->
                callbacks.onChatSuggestionDelete(chatUrl)
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
        globalActivityStarter.start(
            rootView.context,
            SubscriptionPurchase(origin = PURCHASE_ORIGIN, featurePage = DUCK_AI_FEATURE_PAGE),
        )
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

    private fun isDuckAiSettingsUrl(url: String?): Boolean {
        val uri = url?.toUri() ?: return false
        return duckChat.isDuckChatUrl(uri) && uri.getQueryParameter("settings") == "open"
    }

    companion object {
        private const val WIDGET_ELEVATION_DP = 8f
        private const val FADE_OUT_DURATION_MS = 150L
        private const val DUCK_AI_FEATURE_PAGE = "duckai"
        private const val PURCHASE_ORIGIN = "funnel_duckai_android__freelabel"
    }
}

/**
 * Wires the persistent input-mode nav bar's controls to their actions. Pure view wiring (findViewById +
 * setOnClickListener) kept out of the manager so it can be unit-tested without the full attach path.
 */
internal fun bindInputModeNavBar(
    navBarView: View,
    onBack: () -> Unit,
    onFire: () -> Unit,
    onTabs: () -> Unit,
    onBrowserMenu: () -> Unit,
) {
    navBarView.findViewById<View?>(R.id.inputModeWidgetNavBack)?.setOnClickListener { onBack() }
    navBarView.findViewById<View?>(R.id.inputModeWidgetNavFire)?.setOnClickListener { onFire() }
    navBarView.findViewById<View?>(R.id.inputModeWidgetNavTabs)?.setOnClickListener { onTabs() }
    navBarView.findViewById<View?>(R.id.inputModeWidgetNavMenu)?.setOnClickListener { onBrowserMenu() }
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

/**
 * Whether to create the nav bar for this input session. It's a browser-input affordance gated behind the
 * [nativeInputNavBar][com.duckduckgo.duckchat.impl.feature.DuckChatFeature.nativeInputNavBar] flag and the
 * Search & Duck.ai input mode: search-only users, Duck.ai/contextual input, or a disabled flag never get it.
 * The caller additionally only creates it when the field opens empty (a first-focus affordance), so focusing
 * a site with a prefilled URL never gets one. Once created, on-screen visibility is decided by [shouldShowNavBar].
 */
internal fun shouldCreateNavBar(featureEnabled: Boolean, isDuckAiMode: Boolean, inputMode: NativeInputState.InputMode): Boolean =
    featureEnabled && !isDuckAiMode && inputMode == NativeInputState.InputMode.SEARCH_AND_DUCK_AI

/**
 * Whether the input-mode nav bar should currently be on screen. It's shown for a browser-input session that
 * opened empty and stays shown until the user's first keystroke latches it off — the keyboard state does not
 * affect it, and once latched clearing the text won't bring it back. Duck.ai and contextual input never show it.
 */
internal fun shouldShowNavBar(isBrowserContext: Boolean, interactionLatched: Boolean): Boolean =
    isBrowserContext && !interactionLatched

/**
 * Whether a visibility change is needed. [currentShown] is null before the first apply, which always
 * applies; otherwise apply only when the target differs — this makes repeated same-state callbacks
 * (e.g. the prefill-driven emptiness callback) no-ops.
 */
internal fun shouldAnimateNavBar(currentShown: Boolean?, targetShown: Boolean): Boolean =
    currentShown != targetShown
