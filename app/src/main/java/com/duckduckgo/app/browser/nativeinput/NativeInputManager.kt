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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.QueryUrlPredictor
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.ui.NativeInputWidget
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class NativeInputCallbacks(
    val onSearchTextChanged: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onDuckAiChatSubmitted: (query: String, modelId: String?) -> Unit,
    val onChatSuggestionSelected: (String) -> Unit,
    val onClearAutocomplete: () -> Unit,
    val onStopTapped: () -> Unit,
    val onVoiceSearchPressed: (isChatTab: Boolean) -> Unit = {},
    val onImageButtonPressed: () -> Unit = {},
)

interface NativeInputManager {
    fun init(
        omnibar: Omnibar,
        rootView: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        onDisabled: () -> Unit = {},
    )

    fun isNativeInputEnabled(): Boolean
    fun showNativeInput(
        layoutInflater: LayoutInflater,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        query: String = "",
        callbacks: NativeInputCallbacks,
    )

    fun hideNativeInput(animate: Boolean = true): Boolean
    fun handleDuckAiVoiceResult(query: String)
    fun onKeyboardVisibilityChanged(isVisible: Boolean)
}

@ContributesBinding(FragmentScope::class)
class RealNativeInputManager @Inject constructor(
    private val duckChat: DuckChat,
    private val animator: NativeInputAnimator,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val globalActivityStarter: GlobalActivityStarter,
    private val queryUrlPredictor: QueryUrlPredictor,
    private val duckAiFeatureState: DuckAiFeatureState,
) : NativeInputManager {
    private lateinit var omnibarController: NativeInputOmnibarController
    private lateinit var rootView: ViewGroup
    private lateinit var layoutCoordinator: NativeInputLayoutCoordinator
    private var isNativeInputFieldEnabled: Boolean = false
    private var isExiting: Boolean = false
    private var floatingSubmitContainer: View? = null
    private var widgetRoot: View? = null
    private var topBackButton: View? = null

    private fun widgetFrom(widgetView: View): NativeInputWidget? {
        return widgetView.findViewById<View?>(R.id.inputModeWidget) as? NativeInputWidget
    }

    override fun init(
        omnibar: Omnibar,
        rootView: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        onDisabled: () -> Unit,
    ) {
        this.omnibarController = RealNativeInputOmnibarController(omnibar, rootView)
        this.rootView = rootView
        this.layoutCoordinator = NativeInputLayoutCoordinator(rootView, this.omnibarController)
        duckChat.observeNativeInputFieldUserSettingEnabled()
            .onEach { isEnabled ->
                if (isNativeInputFieldEnabled && !isEnabled) onDisabled()
                isNativeInputFieldEnabled = isEnabled
            }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun isNativeInputEnabled(): Boolean = isNativeInputFieldEnabled

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

    override fun hideNativeInput(animate: Boolean): Boolean {
        if (!isNativeInputFieldEnabled) return false

        val widgetView = rootView.findViewById<View?>(R.id.inputModeTopRoot)
            ?: rootView.findViewById(R.id.inputModeBottomRoot)
            ?: return false

        rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList)?.gone()
        rootView.findViewById<View?>(R.id.focusedView)?.gone()

        if (!animate) {
            animator.cancelAnimation()
            isExiting = false
            omnibarController.restore()
            omnibarController.show()
            removeWidget()
            if (omnibarController.isBrowserMode()) {
                hideNtp()
            }
            return !omnibarController.isDuckAiMode()
        }

        val card = widgetView.findViewById<View?>(R.id.inputModeWidgetCard)
        val omnibarCard = omnibarController.getCardView()

        val isBottom = widgetFrom(widgetView)?.isWidgetBottom() ?: false
        isExiting = true
        if (!omnibarController.isDuckAiMode() && card != null && omnibarCard != null && omnibarCard.width > 0) {
            animator.animateExit(card, widgetView, omnibarCard, isBottom) {
                isExiting = false
                onHide()
            }
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
            widgetCard.animate()
                .alpha(0f)
                .setDuration(FADE_OUT_DURATION_MS)
                .withEndAction {
                    widgetCard.alpha = 1f
                    removeWidget()
                    if (omnibarController.isBrowserMode()) {
                        hideNtp()
                    }
                }
                .start()
        } else {
            removeWidget()
            if (omnibarController.isBrowserMode()) {
                hideNtp()
            }
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
            onKeyboardShown(widgetRoot)
        } else {
            onKeyboardHidden(widget, widgetRoot)
        }
    }

    private fun onKeyboardShown(widgetRoot: View?) {
        if (omnibarController.isDuckAiMode() || omnibarController.isSplitMode()) return
        omnibarController.hide()
        widgetRoot?.translationZ = 0f
    }

    private fun onKeyboardHidden(
        widget: NativeInputWidget,
        widgetRoot: View?,
    ) {
        if (widget.isModelMenuVisible()) return
        if (omnibarController.isDuckAiMode()) {
            updateWidgetFocus(widget)
        } else {
            omnibarController.showTransparentOmnibar()
            widgetRoot?.let {
                it.bringToFront()
                it.translationZ = WIDGET_ELEVATION_DP.toPx()
            }
            rootView.post { hideNativeInput() }
        }
    }

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
        layoutInflater: LayoutInflater,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        query: String,
        callbacks: NativeInputCallbacks,
    ) {
        if (!isNativeInputFieldEnabled) return

        if (omnibarController.isDuckAiMode() && rootView.findViewById<View?>(R.id.inputModeWidget) != null) return

        animator.cancelAnimation()
        isExiting = false
        if (omnibarController.isDuckAiMode()) {
            omnibarController.forceToTop()
        }
        removeWidget()
        if (omnibarController.isDuckAiMode()) {
            omnibarController.show()
            omnibarController.hideBackground()
        }
        val isBottom = omnibarController.isDuckAiMode() || omnibarController.isOmnibarBottom()
        val widgetView = createWidgetView(layoutInflater, isBottom)
        val prefillText = query.ifEmpty { omnibarController.getText() }
        bindWidget(widgetView, lifecycleOwner, tabs, callbacks, isBottom)
        if (!omnibarController.isDuckAiMode() && prefillText.isNotEmpty()) {
            callbacks.onClearAutocomplete()
            widgetFrom(widgetView)?.apply {
                text = prefillText
                selectAllText()
            }
        }
        attachWidget(widgetView, isBottom)
        if (omnibarController.isDuckAiMode()) {
            widgetFrom(widgetView)?.setToggleVisible(false)
        } else {
            showNtp()
        }
    }

    private fun attachTopBackButton(widgetView: View) {
        val widget = widgetFrom(widgetView) ?: return
        val backButton = LayoutInflater.from(rootView.context)
            .inflate(R.layout.view_native_input_top_back_button, rootView, false)
            .apply {
                setOnClickListener { widget.onBackPressed() }
            }
        rootView.addView(backButton)
        topBackButton = backButton
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
                hideNativeInput()
                callbacks.onSearchSubmitted(query)
            },
            onChatSubmitted = { query ->
                if (queryUrlPredictor.isUrl(query)) {
                    hideNativeInput()
                    callbacks.onSearchSubmitted(query)
                } else if (omnibarController.isDuckAiMode()) {
                    widget.text = ""
                    widget.hideKeyboard()
                    callbacks.onDuckAiChatSubmitted(query, widget.getSelectedModelId())
                } else {
                    widget.storePendingPrompt(query)
                    animator.cancelAnimation()
                    rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList)?.gone()
                    rootView.findViewById<View?>(R.id.focusedView)?.gone()
                    isExiting = true
                    omnibarController.restore()
                    omnibarController.show()
                    removeWidget()
                    hideNtp()
                    isExiting = false
                    callbacks.onSearchSubmitted(duckChat.getDuckChatUrl(query, true))
                }
            },
        )
        widget.onBack = {
            widget.hideKeyboard()
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
        topBackButton?.let {
            rootView.removeView(it)
            topBackButton = null
        }
        if (removed) widgetRoot = null
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
        callbacks: NativeInputCallbacks,
        isBottom: Boolean,
    ) {
        widgetFrom(widgetView)?.apply {
            onStopTapped = callbacks.onStopTapped
            bindTabCount(lifecycleOwner, tabs.map { it.size })
            hideMainButtons()
            onImageClick = { callbacks.onImageButtonPressed() }
            onPaidTierChanged = { isPaid ->
                val tier = if (isPaid) DuckAiTier.Paid else DuckAiTier.Free
                omnibarController.updateTierTitle(tier) { launchUpgrade() }
            }
            if (!isBottom) {
                setFloatingSubmitContainer(createFloatingSubmitContainer())
            }
        }
        bindSearchCallbacks(widgetView, callbacks)
        bindAutocompleteVisibility(widgetView)
        bindChatSuggestions(widgetView, lifecycleOwner, callbacks.onChatSuggestionSelected)
        bindSearchTabAutocompleteClearing(widgetView, callbacks.onClearAutocomplete)
        bindVoiceButtons(widgetView, callbacks)
        layoutCoordinator.applyBottomCardShape(widgetView, isBottom)
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
        val isDuckAiTabSelected = widget.isChatTabSelected()
        val voiceSearchAvailable = voiceSearchAvailability.isVoiceSearchAvailable
        val voiceSearchDuckAiAvailable = duckAiFeatureState.showVoiceSearchToggle.value
        val voiceChatEntryAvailable = duckAiFeatureState.showVoiceChatEntry.value
        val shouldShowVoiceSearchForDuckAi = !voiceChatEntryAvailable && voiceSearchDuckAiAvailable
        widget.setVoiceSearchAvailable(voiceSearchAvailable && (!isDuckAiTabSelected || shouldShowVoiceSearchForDuckAi))
        widget.setVoiceChatAvailable(isDuckAiTabSelected && voiceChatEntryAvailable)
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

    private fun attachWidget(widgetView: View, isBottom: Boolean) {
        rootView.addView(widgetView, layoutCoordinator.buildWidgetLayoutParams(isBottom))
        widgetRoot = widgetView

        if (isBottom) {
            attachTopBackButton(widgetView)
        }

        widgetFrom(widgetView)?.apply {
            setWidgetRootView(widgetView)
            configure(isDuckAiMode = omnibarController.isDuckAiMode(), isBottom = isBottom)
        }

        applyWindowChrome(widgetView, isBottom)

        if (!startEnterAnimation(widgetView, isBottom)) {
            animator.applyLayoutTransitions(widgetView, isBottom)
            onEnterComplete(widgetView)
        }
    }

    private fun applyWindowChrome(widgetView: View, isBottom: Boolean) {
        widgetView.translationZ = WIDGET_ELEVATION_DP.toPx()
        if (isBottom) {
            rootView.findViewById<View?>(R.id.navigationBar)?.gone()
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
        val margins = animator.init(widgetCard, omnibarCard, omnibarCard.width, omnibarCard.height, isBottom)
            ?: return false

        animator.animateEnter(widgetCard, omnibarCard, widgetView, margins) { onEnterComplete(widgetView) }
        return true
    }

    private fun onEnterComplete(widgetView: View) {
        if (omnibarController.isDuckAiMode()) return
        omnibarController.hide()
        widgetFrom(widgetView)?.focusInput(rootView.context as? Activity)
    }

    private fun bindChatSuggestions(
        widgetView: View,
        lifecycleOwner: LifecycleOwner,
        onChatSuggestionSelected: (String) -> Unit,
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
                hideNativeInput(animate = false)
                onChatSuggestionSelected(query)
            },
            onShowSuggestions = { chatAdapter ->
                adapter = adapter ?: autoCompleteList.adapter
                autoCompleteList.adapter = chatAdapter
                autoCompleteList.itemAnimator = null
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

    private fun launchUpgrade() {
        globalActivityStarter.start(rootView.context, SubscriptionPurchase(featurePage = DUCK_AI_FEATURE_PAGE))
    }

    companion object {
        private const val WIDGET_ELEVATION_DP = 8f
        private const val FADE_OUT_DURATION_MS = 150L
        private const val DUCK_AI_FEATURE_PAGE = "duckai"
    }
}
