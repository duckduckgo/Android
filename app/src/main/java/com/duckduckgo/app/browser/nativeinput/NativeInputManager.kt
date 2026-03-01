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
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.net.toUri
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.DuckAI
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.browser.webview.BottomOmnibarBrowserContainerLayoutBehavior
import com.duckduckgo.app.browser.webview.TopOmnibarBrowserContainerLayoutBehavior
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.duckchat.impl.ui.NativeInputModeWidget
import com.google.android.material.card.MaterialCardView
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

interface NativeInputManager {
    fun start(lifecycleOwner: LifecycleOwner)
    fun isNativeInputEnabled(): Boolean
    fun showNativeInput(
        omnibar: Omnibar,
        layoutInflater: LayoutInflater,
        rootView: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        query: String = "",
        onSearchTextChanged: (String) -> Unit,
        onClearAutocomplete: () -> Unit,
        onSearchSubmitted: (String) -> Unit,
        onChatSubmitted: (String) -> Unit,
        onDuckAiChatSubmitted: (String) -> Unit,
        onChatSuggestionSelected: (String) -> Unit,
        onFireButtonTapped: () -> Unit,
        onTabSwitcherTapped: () -> Unit,
        onMenuTapped: () -> Unit,
        onStopClicked: () -> Unit,
    )
    fun hideNativeInput(
        rootView: ViewGroup,
        omnibar: Omnibar,
    ): Boolean
    fun onKeyboardVisibilityChanged(
        isVisible: Boolean,
        rootView: ViewGroup,
        omnibar: Omnibar,
    )
}

@ContributesBinding(AppScope::class, boundType = NativeInputManager::class)
class RealNativeInputManager @Inject constructor(
    private val duckChat: DuckChat,
    private val chatSuggestionsReader: ChatSuggestionsReader,
) : NativeInputManager {
    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private var isNativeInputFieldEnabled: Boolean = false

    private var autoCompleteAdapter: RecyclerView.Adapter<*>? = null

    private var chatSuggestionsUserEnabled: Boolean = true
    private var chatSuggestionsAdapter: ChatSuggestionsAdapter? = null
    private var chatSuggestionsJob: Job? = null

    private fun View.snapshotPadding() = Padding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    private fun widgetFrom(widgetView: View): NativeInputModeWidget? {
        return widgetView.findViewById(R.id.inputModeWidget)
    }

    override fun start(lifecycleOwner: LifecycleOwner) {
        duckChat.observeNativeInputFieldUserSettingEnabled()
            .onEach { isEnabled ->
                isNativeInputFieldEnabled = isEnabled
            }
            .launchIn(lifecycleOwner.lifecycleScope)

        duckChat.observeChatSuggestionsUserSettingEnabled()
            .onEach { enabled ->
                chatSuggestionsUserEnabled = enabled
            }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun isNativeInputEnabled(): Boolean = isNativeInputFieldEnabled

    override fun hideNativeInput(
        rootView: ViewGroup,
        omnibar: Omnibar,
    ): Boolean {
        if (omnibar.viewMode == DuckAI) return false
        val removed = removeExistingWidget(rootView)
        if (!removed) return false
        rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList)?.gone()
        rootView.findViewById<View?>(R.id.focusedView)?.gone()
        restoreOmnibar(omnibar, rootView)
        omnibar.show()
        return true
    }

    override fun onKeyboardVisibilityChanged(
        isVisible: Boolean,
        rootView: ViewGroup,
        omnibar: Omnibar,
    ) {
        fun isDescendantOf(
            ancestor: View,
            view: View,
        ): Boolean {
            var current: View? = view
            while (current != null) {
                if (current === ancestor) return true
                val parent = current.parent
                current = parent as? View
            }
            return false
        }

        if (isVisible || (!isNativeInputFieldEnabled && omnibar.viewMode != DuckAI)) return
        if (omnibar.viewMode == DuckAI) {
            widgetFrom(rootView)?.requestInputFocus()
            return
        }
        val widget = widgetFrom(rootView) ?: return
        val focusedView = rootView.findFocus()
        val focusWithinWidget = focusedView?.let { isDescendantOf(widget, it) } ?: false
        if (widget.hasInputFocus() || !focusWithinWidget) {
            widget.clearInputFocus()
            hideNativeInput(rootView, omnibar)
        } else {
            widget.requestInputFocus()
        }
    }

    override fun showNativeInput(
        omnibar: Omnibar,
        layoutInflater: LayoutInflater,
        rootView: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        query: String,
        onSearchTextChanged: (String) -> Unit,
        onClearAutocomplete: () -> Unit,
        onSearchSubmitted: (String) -> Unit,
        onChatSubmitted: (String) -> Unit,
        onDuckAiChatSubmitted: (String) -> Unit,
        onChatSuggestionSelected: (String) -> Unit,
        onFireButtonTapped: () -> Unit,
        onTabSwitcherTapped: () -> Unit,
        onMenuTapped: () -> Unit,
        onStopClicked: () -> Unit,
    ) {
        if (!isNativeInputFieldEnabled) return
        if (omnibar.viewMode == DuckAI) {
            forceOmnibarTop(omnibar, rootView)
        }
        removeExistingWidget(rootView)
        if (omnibar.viewMode == DuckAI) {
            omnibar.show()
            hideOmnibarBackground(omnibar, rootView)
        }
        val widgetView = createWidgetView(layoutInflater, rootView, omnibar)
        val prefillText = query.ifEmpty { omnibar.getText() }
        bindWidget(
            widgetView = widgetView,
            rootView = rootView,
            omnibar = omnibar,
            lifecycleOwner = lifecycleOwner,
            tabs = tabs,
            onSearchTextChanged = onSearchTextChanged,
            onClearAutocomplete = onClearAutocomplete,
            onSearchSubmitted = onSearchSubmitted,
            onChatSubmitted = onChatSubmitted,
            onDuckAiChatSubmitted = onDuckAiChatSubmitted,
            onChatSuggestionSelected = onChatSuggestionSelected,
            onFireButtonTapped = onFireButtonTapped,
            onTabSwitcherTapped = onTabSwitcherTapped,
            onMenuTapped = onMenuTapped,
            onStopClicked = onStopClicked,
        )
        if (omnibar.viewMode != DuckAI && prefillText.isNotEmpty()) {
            widgetFrom(widgetView)?.text = prefillText
        }
        attachWidget(widgetView, rootView, omnibar)
        if (omnibar.viewMode != DuckAI) {
            omnibar.hide()
        }
    }

    private fun bindMainButtons(
        widgetView: View,
        onFireButtonTapped: () -> Unit,
        onTabSwitcherTapped: () -> Unit,
        onMenuTapped: () -> Unit,
    ) {
        widgetFrom(widgetView)?.bindMainButtons(
            onFireButtonTapped = onFireButtonTapped,
            onTabSwitcherTapped = onTabSwitcherTapped,
            onMenuTapped = onMenuTapped,
        )
    }

    private fun bindTabCount(
        widgetView: View,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
    ) {
        val widget = widgetFrom(widgetView) ?: return
        val tabCount = tabs.map { it.size }
        widget.bindTabCount(lifecycleOwner, tabCount)
    }

    private fun bindSearchCallbacks(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
        onSearchTextChanged: (String) -> Unit,
        onClearAutocomplete: () -> Unit,
        onSearchSubmitted: (String) -> Unit,
        onChatSubmitted: (String) -> Unit,
        onDuckAiChatSubmitted: (String) -> Unit,
    ) {
        val widget = widgetFrom(widgetView) ?: return
        widget.bindSearchCallbacks(
            onSearchTextChanged = onSearchTextChanged,
            onSearchSubmitted = { query ->
                if (omnibar.viewMode == DuckAI) {
                    removeExistingWidget(rootView)
                    restoreOmnibar(omnibar, rootView)
                    omnibar.show()
                } else {
                    hideNativeInput(rootView, omnibar)
                }
                onSearchSubmitted(query)
            },
            onChatSubmitted = { query ->
                if (omnibar.viewMode == DuckAI) {
                    (widget.context as? Activity)?.hideKeyboard(widget.inputField)
                    onDuckAiChatSubmitted(query)
                } else {
                    hideNativeInput(rootView, omnibar)
                    onChatSubmitted(query)
                }
            },
        )
        val previousOnChatSelected = widget.onChatSelected
        widget.onChatSelected = {
            onClearAutocomplete()
            previousOnChatSelected?.invoke()
        }
        widget.onClearTextTapped = {
            if (!widget.isChatTabSelected()) {
                onClearAutocomplete()
            }
        }
    }

    private fun removeExistingWidget(rootView: ViewGroup): Boolean {
        var removed = false
        rootView.findViewById<View?>(R.id.inputModeTopRoot)?.let {
            rootView.removeView(it)
            removed = true
        }
        rootView.findViewById<View?>(R.id.inputModeBottomRoot)?.let {
            rootView.removeView(it)
            removed = true
        }
        restoreAutoCompleteAdapter(rootView)
        chatSuggestionsAdapter = null
        autoCompleteAdapter = null
        return removed
    }

    private fun hideOmnibarBackground(omnibar: Omnibar, rootView: ViewGroup) {
        val omnibarView = omnibar.omnibarView as? View ?: return

        val toolbarContainer = omnibarView.findViewById<View?>(R.id.toolbarContainer)
        val cardShadow = omnibarView.findViewById<com.google.android.material.card.MaterialCardView?>(R.id.omniBarContainerShadow)
        val innerCard = omnibarView.findViewById<com.google.android.material.card.MaterialCardView?>(R.id.omniBarContainer)
        val header = omnibarView.findViewById<android.widget.LinearLayout?>(R.id.duckAIHeader)
        val aiIcon = omnibarView.findViewById<View?>(R.id.aiIcon)
        val aiTitle = omnibarView.findViewById<TextView?>(R.id.aiTitle)
        val leadingIconContainer = omnibarView.findViewById<View?>(R.id.omnibarIconContainer)
        val shieldIcon = omnibarView.findViewById<View?>(R.id.shieldIcon)
        val omnibarTextInput = omnibarView.findViewById<View?>(R.id.omnibarTextInput)
        val pageLoadingIndicator = omnibarView.findViewById<View?>(R.id.pageLoadingIndicator)

        fun apply() {
            if (rootView.findViewById<View?>(R.id.inputModeWidget) == null) return
            toolbarContainer?.setBackgroundColor(Color.TRANSPARENT)
            cardShadow?.setCardBackgroundColor(Color.TRANSPARENT)
            cardShadow?.cardElevation = 0f
            innerCard?.setCardBackgroundColor(Color.TRANSPARENT)
            leadingIconContainer?.gone()
            shieldIcon?.gone()
            omnibarTextInput?.gone()
            pageLoadingIndicator?.gone()
            header?.show()
            header?.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            header?.setBackgroundColor(Color.TRANSPARENT)
            aiIcon?.gone()
            aiTitle?.show()
            aiTitle?.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6)
        }

        apply()
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
        omnibarView.addOnLayoutChangeListener(listener)
        omnibarView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    omnibarView.removeOnLayoutChangeListener(listener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    private fun restoreOmnibar(omnibar: Omnibar, rootView: ViewGroup) {
        val omnibarView = omnibar.omnibarView as? View
        if (omnibarView != null) {
            val ctx = omnibarView.context
            omnibarView.findViewById<View?>(R.id.toolbarContainer)
                ?.setBackgroundColor(ctx.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorToolbar))
            omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainerShadow)?.apply {
                setCardBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
                cardElevation = 1f.toPx()
            }
            omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainer)
                ?.setCardBackgroundColor(ctx.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorWindow))
        }

        if (omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM) {
            val params = omnibarView?.layoutParams as? CoordinatorLayout.LayoutParams
            if (params?.gravity == Gravity.TOP) {
                omnibarView.updateLayoutParams<CoordinatorLayout.LayoutParams> { gravity = Gravity.BOTTOM }
                val parent = omnibarView.parent as? ViewGroup
                parent?.removeView(omnibarView)
                parent?.addView(omnibarView)
                omnibarView.elevation = 0f
                rootView.findViewById<View?>(R.id.browserLayout)?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    behavior = BottomOmnibarBrowserContainerLayoutBehavior()
                }
            }
        }

        if (omnibar.omnibarType == OmnibarType.SPLIT) {
            rootView.findViewById<View?>(R.id.navigationBar)?.show()
        }

        omnibar.isScrollingEnabled = true
    }

    private fun forceOmnibarTop(omnibar: Omnibar, rootView: ViewGroup) {
        val omnibarView = omnibar.omnibarView as? View ?: return
        val parent = omnibarView.parent as? ViewGroup ?: return

        if (omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM) {
            omnibarView.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                gravity = Gravity.TOP
            }
            parent.removeView(omnibarView)
            parent.addView(omnibarView, 0)
            omnibarView.elevation = 1f.toPx()

            val topBehavior = TopOmnibarBrowserContainerLayoutBehavior(rootView.context, null)
            rootView.findViewById<View?>(R.id.browserLayout)?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                behavior = topBehavior
            }
        }
        omnibar.isScrollingEnabled = false
        omnibar.setExpanded(true)
    }

    private fun createWidgetView(
        layoutInflater: LayoutInflater,
        rootView: ViewGroup,
        omnibar: Omnibar,
    ): View {
        val layoutRes =
            if (isWidgetBottom(omnibar)) {
                R.layout.input_mode_widget_card_view_bottom
            } else {
                R.layout.input_mode_widget_card_view
            }
        return layoutInflater.inflate(layoutRes, rootView, false)
    }

    private fun bindWidget(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
        lifecycleOwner: LifecycleOwner,
        tabs: LiveData<List<TabEntity>>,
        onSearchTextChanged: (String) -> Unit,
        onClearAutocomplete: () -> Unit,
        onSearchSubmitted: (String) -> Unit,
        onChatSubmitted: (String) -> Unit,
        onDuckAiChatSubmitted: (String) -> Unit,
        onChatSuggestionSelected: (String) -> Unit,
        onFireButtonTapped: () -> Unit,
        onTabSwitcherTapped: () -> Unit,
        onMenuTapped: () -> Unit,
        onStopClicked: () -> Unit,
    ) {
        bindMainButtons(widgetView, onFireButtonTapped, onTabSwitcherTapped, onMenuTapped)
        widgetFrom(widgetView)?.onStopClicked = onStopClicked
        bindTabCount(widgetView, lifecycleOwner, tabs)
        bindSearchCallbacks(
            widgetView,
            rootView,
            omnibar,
            onSearchTextChanged,
            onClearAutocomplete,
            onSearchSubmitted,
            onChatSubmitted,
            onDuckAiChatSubmitted,
        )
        bindAutocompleteVisibility(widgetView, rootView, omnibar)
        bindChatSuggestions(
            widgetView = widgetView,
            rootView = rootView,
            omnibar = omnibar,
            lifecycleOwner = lifecycleOwner,
            onChatSuggestionSelected = onChatSuggestionSelected,
        )
        bindSearchTabAutocompleteClearing(widgetView, onClearAutocomplete)
        applyInitialTabSelection(widgetView, omnibar)
        applyMainButtonsVisibility(widgetView, omnibar)
        applyBottomCardShape(widgetView, omnibar)
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

    private fun applyInitialTabSelection(
        widgetView: View,
        omnibar: Omnibar,
    ) {
        if (omnibar.viewMode != DuckAI) return
        widgetFrom(widgetView)?.selectChatTab()
    }

    private fun bindAutocompleteVisibility(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
    ) {
        if (omnibar.viewMode != DuckAI) return
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

    private fun applyMainButtonsVisibility(
        widgetView: View,
        omnibar: Omnibar,
    ) {
        widgetFrom(widgetView)?.setMainButtonsAllowed(omnibar.viewMode != DuckAI)
    }

    private fun attachWidget(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
    ) {
        rootView.addView(widgetView, buildWidgetLayoutParams(omnibar))
        if (isWidgetBottom(omnibar)) {
            rootView.findViewById<View?>(R.id.navigationBar)?.gone()
            rootView.findViewById<View?>(R.id.browserLayout)?.let {
                it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, 0)
            }
        }
        configureAutocompleteLayout(widgetView, rootView, omnibar)
        configureContentOffset(widgetView, rootView, omnibar)
        widgetView.post { applyForcedBottomTranslation(widgetView, rootView, omnibar) }
        if (omnibar.viewMode != DuckAI) {
            widgetFrom(widgetView)?.focusInput(rootView.context as? Activity)
        }
    }

    private fun applyBottomCardShape(
        widgetView: View,
        omnibar: Omnibar,
    ) {
        if (!isWidgetBottom(omnibar)) return
        val card = widgetView.findViewById<MaterialCardView?>(R.id.inputModeWidgetCard) ?: return
        val radius = card.resources.getDimension(R.dimen.extraLargeShapeCornerRadius)
        card.shapeAppearanceModel =
            card.shapeAppearanceModel
                .toBuilder()
                .setTopLeftCornerSize(radius)
                .setTopRightCornerSize(radius)
                .setBottomLeftCornerSize(0f)
                .setBottomRightCornerSize(0f)
                .build()
    }

    private fun bindChatSuggestions(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
        lifecycleOwner: LifecycleOwner,
        onChatSuggestionSelected: (String) -> Unit,
    ) {
        if (omnibar.viewMode == DuckAI) return
        if (!duckChat.isChatSuggestionsFeatureAvailable()) return
        val widget = widgetFrom(widgetView) ?: return
        val autoCompleteList =
            rootView.findViewById<RecyclerView?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)
        val adapter = ChatSuggestionsAdapter { suggestion ->
            onChatSuggestionSelected(buildChatUrl(suggestion))
        }.also { chatSuggestionsAdapter = it }

        fun showChatSuggestions(query: String) {
            if (!chatSuggestionsUserEnabled) {
                hideChatSuggestions(autoCompleteList, focusedView, hideList = true)
                return
            }
            autoCompleteAdapter = autoCompleteAdapter ?: autoCompleteList.adapter
            autoCompleteList.adapter = adapter
            autoCompleteList.itemAnimator = null
            fetchChatSuggestions(
                lifecycleOwner = lifecycleOwner,
                query = query,
                autoCompleteList = autoCompleteList,
                focusedView = focusedView,
                adapter = adapter,
            )
        }

        fun clearChatSuggestions(hideList: Boolean) {
            hideChatSuggestions(autoCompleteList, focusedView, hideList = hideList)
            if (!hideList) {
                restoreAutoCompleteAdapter(rootView)
            }
        }

        val previousOnSearchSelected = widget.onSearchSelected
        widget.onSearchSelected = {
            clearChatSuggestions(hideList = false)
            previousOnSearchSelected?.invoke()
        }

        val previousOnChatSelected = widget.onChatSelected
        widget.onChatSelected = {
            previousOnChatSelected?.invoke()
            omnibar.hide()
            showChatSuggestions(widget.text)
        }

        widget.onChatTextChanged = { text ->
            if (autoCompleteList.adapter == adapter) {
                fetchChatSuggestions(
                    lifecycleOwner = lifecycleOwner,
                    query = text,
                    autoCompleteList = autoCompleteList,
                    focusedView = focusedView,
                    adapter = adapter,
                )
            }
        }
    }

    private fun fetchChatSuggestions(
        lifecycleOwner: LifecycleOwner,
        query: String,
        autoCompleteList: RecyclerView,
        focusedView: View?,
        adapter: ChatSuggestionsAdapter,
    ) {
        chatSuggestionsJob?.cancel()
        chatSuggestionsJob =
            lifecycleOwner.lifecycleScope.launch {
                val suggestions = runCatching { chatSuggestionsReader.fetchSuggestions(query) }.getOrDefault(emptyList())
                adapter.submitList(suggestions)
                if (suggestions.isNotEmpty()) {
                    autoCompleteList.show()
                    focusedView?.gone()
                } else {
                    autoCompleteList.gone()
                }
            }
    }

    private fun hideChatSuggestions(
        autoCompleteList: RecyclerView,
        focusedView: View?,
        hideList: Boolean,
    ) {
        chatSuggestionsJob?.cancel()
        chatSuggestionsAdapter?.submitList(emptyList())
        if (hideList) {
            autoCompleteList.gone()
            focusedView?.gone()
        }
        chatSuggestionsReader.tearDown()
    }

    private fun restoreAutoCompleteAdapter(rootView: ViewGroup) {
        val autoCompleteList = rootView.findViewById<RecyclerView?>(R.id.autoCompleteSuggestionsList) ?: return
        autoCompleteAdapter?.let { adapter ->
            if (autoCompleteList.adapter != adapter) {
                autoCompleteList.adapter = adapter
            }
        }
    }

    private fun buildChatUrl(suggestion: ChatSuggestion): String {
        return duckChat.getDuckChatUrl("", false)
            .toUri()
            .buildUpon()
            .appendQueryParameter(CHAT_ID_PARAM, suggestion.chatId)
            .build()
            .toString()
    }

    private fun configureAutocompleteLayout(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
    ) {
        val autoCompleteList = rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)
        val baseElevation = maxOf(autoCompleteList.elevation, focusedView?.elevation ?: 0f)
        val targetElevation = baseElevation + widgetView.resources.displayMetrics.density
        widgetView.elevation = maxOf(widgetView.elevation, targetElevation)
        widgetView.bringToFront()

        val targets =
            buildList {
                add(autoCompleteList to autoCompleteList.snapshotPadding())
                focusedView?.let { add(it to it.snapshotPadding()) }
            }
        fun applyPadding(deltaTop: Int, deltaBottom: Int) {
            targets.forEach { (view, padding) ->
                view.setPadding(
                    padding.left,
                    padding.top + deltaTop,
                    padding.right,
                    padding.bottom + deltaBottom,
                )
            }
        }

        fun applyForWidgetPosition() {
            val isBottom = isWidgetBottom(omnibar)
            val topOffset = if (isBottom) 0 else maxOf(0, widgetView.bottom - autoCompleteList.top)
            val bottomOffset = if (isBottom) maxOf(0, autoCompleteList.bottom - widgetView.top) else 0
            applyPadding(deltaTop = topOffset, deltaBottom = bottomOffset)
        }

        widgetView.post { applyForWidgetPosition() }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyForWidgetPosition()
            }
        widgetView.addOnLayoutChangeListener(layoutListener)
        autoCompleteList.addOnLayoutChangeListener(layoutListener)
        focusedView?.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    applyPadding(deltaTop = 0, deltaBottom = 0)
                    v.removeOnLayoutChangeListener(layoutListener)
                    autoCompleteList.removeOnLayoutChangeListener(layoutListener)
                    focusedView?.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    private fun configureContentOffset(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
    ) {
        data class Target(val view: View, val basePadding: Padding)
        val newTabContent =
            rootView.findViewById<View?>(R.id.newTabPage)
                ?: rootView.findViewById<View?>(R.id.includeNewBrowserTab)
        val targets =
            listOfNotNull(
                rootView.findViewById<View?>(R.id.browserLayout),
                newTabContent,
            ).map { Target(it, it.snapshotPadding()) }
        if (targets.isEmpty()) return
        val anchor = widgetView.findViewById(R.id.inputModeWidgetCard) ?: widgetView

        val overlap = widgetView.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_5)

        fun applyPadding(view: View, padding: Padding, deltaTop: Int, deltaBottom: Int) {
            view.setPadding(
                padding.left,
                padding.top + deltaTop,
                padding.right,
                padding.bottom + deltaBottom,
            )
        }

        fun applyOffset() {
            if (!widgetView.isShown) {
                targets.forEach { target ->
                    applyPadding(target.view, target.basePadding, deltaTop = 0, deltaBottom = 0)
                }
                return
            }
            val isBottom = isWidgetBottom(omnibar)
            val anchorLocation = IntArray(2).also { anchor.getLocationInWindow(it) }
            val anchorBottomInWindow = anchorLocation[1] + anchor.height
            targets.forEach { target ->
                val view = target.view
                val viewLocation = IntArray(2).also { view.getLocationInWindow(it) }
                val deltaTop = if (isBottom) 0 else maxOf(0, anchorBottomInWindow - viewLocation[1])
                val deltaBottom =
                    if (isBottom) {
                        if (omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM) {
                            maxOf(0, overlap)
                        } else {
                            maxOf(0, anchor.height - overlap)
                        }
                    } else {
                        0
                    }
                applyPadding(view, target.basePadding, deltaTop, deltaBottom)
            }
        }

        widgetView.post { applyOffset() }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyOffset()
            }
        widgetView.addOnLayoutChangeListener(layoutListener)
        rootView.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    targets.forEach { target ->
                        applyPadding(target.view, target.basePadding, deltaTop = 0, deltaBottom = 0)
                    }
                    v.removeOnLayoutChangeListener(layoutListener)
                    rootView.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    private fun buildWidgetLayoutParams(
        omnibar: Omnibar,
    ): ViewGroup.LayoutParams {
        return CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (isWidgetBottom(omnibar)) Gravity.BOTTOM else Gravity.TOP
        }
    }

    private fun isWidgetBottom(omnibar: Omnibar): Boolean {
        return omnibar.viewMode == DuckAI || omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM
    }

    private fun applyForcedBottomTranslation(
        widgetView: View,
        rootView: ViewGroup,
        omnibar: Omnibar,
    ) {
        val shouldForce = isWidgetBottom(omnibar) && omnibar.omnibarType != OmnibarType.SINGLE_BOTTOM
        if (!shouldForce) {
            widgetView.translationY = 0f
            return
        }
        fun applyOffset() {
            val gap = maxOf(0, rootView.height - widgetView.bottom)
            if (widgetView.translationY != gap.toFloat()) {
                widgetView.translationY = gap.toFloat()
            }
        }
        applyOffset()
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyOffset()
            }
        rootView.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    rootView.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    companion object {
        private const val CHAT_ID_PARAM = "chatID"
    }
}
