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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.ui.NativeInputWidget
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class NativeInputCallbacks(
    val onSearchTextChanged: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onDuckAiChatSubmitted: (String) -> Unit,
    val onChatSuggestionSelected: (String) -> Unit,
    val onClearAutocomplete: () -> Unit,
    val onStopTapped: () -> Unit,
)

interface NativeInputManager {
    fun init(omnibar: Omnibar, rootView: ViewGroup, lifecycleOwner: LifecycleOwner, onDisabled: () -> Unit = {})
    fun isNativeInputEnabled(): Boolean
    fun showNativeInput(
        layoutInflater: LayoutInflater,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        query: String = "",
        callbacks: NativeInputCallbacks,
    )
    fun hideNativeInput(): Boolean
    fun onKeyboardVisibilityChanged(isVisible: Boolean)
}

@ContributesBinding(FragmentScope::class)
class RealNativeInputManager @Inject constructor(
    private val duckChat: DuckChat,
) : NativeInputManager {
    private lateinit var omnibarController: NativeInputOmnibarController
    private lateinit var rootView: ViewGroup
    private lateinit var layoutCoordinator: NativeInputLayoutCoordinator
    private lateinit var animator: NativeInputAnimator
    private var isNativeInputFieldEnabled: Boolean = false
    private var isExiting: Boolean = false

    private fun widgetFrom(widgetView: View): NativeInputWidget? {
        return widgetView.findViewById<View?>(R.id.inputModeWidget) as? NativeInputWidget
    }

    override fun init(omnibar: Omnibar, rootView: ViewGroup, lifecycleOwner: LifecycleOwner, onDisabled: () -> Unit) {
        this.omnibarController = RealNativeInputOmnibarController(omnibar, rootView)
        this.animator = RealNativeInputAnimator()
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

    override fun hideNativeInput(): Boolean {
        if (!isNativeInputFieldEnabled) return false

        val widgetView = rootView.findViewById<View?>(R.id.inputModeTopRoot)
            ?: rootView.findViewById(R.id.inputModeBottomRoot)
            ?: return false

        rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList)?.gone()
        rootView.findViewById<View?>(R.id.focusedView)?.gone()

        val card = widgetView.findViewById<View?>(R.id.inputModeWidgetCard)
        val omnibarCard = omnibarController.getCardView()

        isExiting = true
        if (!omnibarController.isDuckAiMode() && card != null && omnibarCard != null && omnibarCard.width > 0) {
            animator.animateExit(card, widgetView, omnibarCard, layoutCoordinator.isWidgetBottom()) {
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
        val widgetRoot = widget.asView().parent?.parent as? View

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
        if (layoutCoordinator.isWidgetBottom() && widgetRoot != null) {
            expandBottomCardToFull(widgetRoot)
        } else {
            setWidgetCardEndMargin(0)
        }
    }

    private fun expandBottomCardToFull(widgetRoot: View) {
        val card = widgetRoot.findViewById<View?>(R.id.inputModeWidgetCard) ?: return
        val parentWidth = (card.parent as? View)?.width ?: return

        layoutCoordinator.applyBottomCardCorners(widgetRoot)

        animator.animateCardWidth(
            card = card,
            widgetView = widgetRoot,
            target = CardWidthTarget(width = parentWidth, marginStart = 0, marginEnd = 0, bottomMargin = 0),
        )
    }

    private fun onKeyboardHidden(widget: NativeInputWidget, widgetRoot: View?) {
        updateWidgetFocus(widget)
        if (!omnibarController.isDuckAiMode() && !omnibarController.isSplitMode()) {
            showTabsAndMenuButtons(widgetRoot)
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

    private fun showTabsAndMenuButtons(widgetRoot: View?) {
        omnibarController.showTransparentOmnibar()
        widgetRoot?.let {
            it.bringToFront()
            it.translationZ = 8f.toPx()
        }
        if (widgetRoot != null) {
            layoutCoordinator.applyRoundedCardShape(widgetRoot)
        }
        rootView.post {
            if (widgetRoot != null && widgetRoot.translationZ != 0f) {
                setWidgetCardEndMargin(omnibarController.getButtonsWidth())
            }
        }
    }

    private fun isDescendantOf(ancestor: View, view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun setWidgetCardEndMargin(margin: Int) {
        val card = rootView.findViewById<View?>(R.id.inputModeWidgetCard) ?: return
        val widgetView = rootView.findViewById(R.id.inputModeTopRoot)
            ?: rootView.findViewById<View?>(R.id.inputModeBottomRoot)
            ?: return
        val params = card.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val targetMarginEnd = params.marginStart + margin
        if (params.marginEnd == targetMarginEnd) return
        val parentWidth = (card.parent as? View)?.width ?: return

        animator.animateCardWidth(
            card = card,
            widgetView = widgetView,
            target = CardWidthTarget(
                width = parentWidth - params.marginStart - targetMarginEnd,
                marginStart = params.marginStart,
                marginEnd = targetMarginEnd,
                bottomMargin = params.bottomMargin,
            ),
        )
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

        isExiting = false
        animator.cancelAnimation()
        if (omnibarController.isDuckAiMode()) {
            omnibarController.forceToTop()
        }
        removeWidget()
        if (omnibarController.isDuckAiMode()) {
            omnibarController.show()
            omnibarController.hideBackground()
        }
        val widgetView = createWidgetView(layoutInflater)
        val prefillText = query.ifEmpty { omnibarController.getText() }
        bindWidget(widgetView, lifecycleOwner, tabs, callbacks)
        if (!omnibarController.isDuckAiMode() && prefillText.isNotEmpty()) {
            callbacks.onClearAutocomplete()
            widgetFrom(widgetView)?.apply {
                text = prefillText
                selectAllText()
            }
        }
        attachWidget(widgetView)
        if (!omnibarController.isDuckAiMode()) {
            showNtp()
        }
    }

    private fun bindSearchCallbacks(
        widgetView: View,
        callbacks: NativeInputCallbacks,
    ) {
        val widget = widgetFrom(widgetView) ?: return
        widget.bindInputEvents(
            onSearchTextChanged = callbacks.onSearchTextChanged,
            onSearchSubmitted = { query ->
                hideNativeInput()
                callbacks.onSearchSubmitted(query)
            },
            onChatSubmitted = { query ->
                if (omnibarController.isDuckAiMode()) {
                    widget.text = ""
                    widget.hideKeyboard()
                    callbacks.onDuckAiChatSubmitted(query)
                } else {
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
        val previousOnChatSelected = widget.onChatSelected
        widget.onChatSelected = {
            callbacks.onClearAutocomplete()
            previousOnChatSelected?.invoke()
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
        return removed
    }

    private fun createWidgetView(layoutInflater: LayoutInflater): View {
        val layoutRes =
            if (layoutCoordinator.isWidgetBottom()) {
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
    ) {
        widgetFrom(widgetView)?.apply {
            onStopTapped = callbacks.onStopTapped
            bindTabCount(lifecycleOwner, tabs.map { it.size })
            hideMainButtons()
        }
        bindSearchCallbacks(widgetView, callbacks)
        bindAutocompleteVisibility(widgetView)
        bindChatSuggestions(widgetView, lifecycleOwner, callbacks.onChatSuggestionSelected)
        bindSearchTabAutocompleteClearing(widgetView, callbacks.onClearAutocomplete)
        applyInitialTabSelection(widgetView)
        layoutCoordinator.applyBottomCardShape(widgetView)
    }

    private fun bindSearchTabAutocompleteClearing(
        widgetView: View,
        onClearAutocomplete: () -> Unit,
    ) {
        val widget = widgetFrom(widgetView) ?: return
        val previousOnSearchSelected = widget.onSearchSelected
        widget.onSearchSelected = {
            if (widget.text.isBlank()) {
                onClearAutocomplete()
            }
            previousOnSearchSelected?.invoke()
        }
    }

    private fun applyInitialTabSelection(widgetView: View) {
        if (!omnibarController.isDuckAiMode()) return
        widgetFrom(widgetView)?.selectChatTab()
    }

    private fun bindAutocompleteVisibility(widgetView: View) {
        if (!omnibarController.isDuckAiMode()) return
        val widget = widgetFrom(widgetView) ?: return
        val autoCompleteList =
            rootView.findViewById<RecyclerView?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)
        val previousOnChatSelected = widget.onChatSelected
        widget.onChatSelected = {
            previousOnChatSelected?.invoke()
            autoCompleteList.gone()
            focusedView?.gone()
        }
    }

    private fun attachWidget(widgetView: View) {
        val omnibarCard = omnibarController.getCardView()
        val widgetCard = widgetView.findViewById<View?>(R.id.inputModeWidgetCard)
        val margins = if (!omnibarController.isDuckAiMode() && omnibarCard != null && widgetCard != null) {
            animator.init(widgetCard, omnibarCard, omnibarCard.width, omnibarCard.height, layoutCoordinator.isWidgetBottom())
        } else {
            null
        }

        rootView.addView(widgetView, layoutCoordinator.buildWidgetLayoutParams())
        widgetView.translationZ = WIDGET_ELEVATION_DP.toPx()
        if (layoutCoordinator.isWidgetBottom()) {
            rootView.findViewById<View?>(R.id.navigationBar)?.gone()
            rootView.findViewById<View?>(R.id.browserLayout)?.let {
                it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, 0)
            }
        }
        layoutCoordinator.configureAutocompleteLayout(widgetView)
        layoutCoordinator.configureContentOffset(widgetView)
        widgetView.post { layoutCoordinator.applyForcedBottomTranslation(widgetView) }

        if (widgetCard != null && omnibarCard != null && margins != null) {
            animator.animateEnter(widgetCard, omnibarCard, widgetView, margins) {
                if (!omnibarController.isDuckAiMode()) {
                    omnibarController.hide()
                    widgetFrom(widgetView)?.focusInput(rootView.context as? Activity)
                }
            }
        } else {
            animator.applyLayoutTransitions(widgetView)
            if (!omnibarController.isDuckAiMode()) {
                omnibarController.hide()
                widgetFrom(widgetView)?.focusInput(rootView.context as? Activity)
            }
        }
    }

    private fun bindChatSuggestions(
        widgetView: View,
        lifecycleOwner: LifecycleOwner,
        onChatSuggestionSelected: (String) -> Unit,
    ) {
        if (omnibarController.isDuckAiMode()) return
        if (!duckChat.isChatSuggestionsFeatureAvailable()) return
        val widget = widgetFrom(widgetView) ?: return
        val autoCompleteList =
            rootView.findViewById<RecyclerView?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)
        var adapter: RecyclerView.Adapter<*>? = null
        widget.bindChatSuggestions(
            lifecycleOwner = lifecycleOwner,
            onChatSuggestionSelected = onChatSuggestionSelected,
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

    companion object {
        private const val WIDGET_ELEVATION_DP = 8f
        private const val FADE_OUT_DURATION_MS = 150L
    }
}
