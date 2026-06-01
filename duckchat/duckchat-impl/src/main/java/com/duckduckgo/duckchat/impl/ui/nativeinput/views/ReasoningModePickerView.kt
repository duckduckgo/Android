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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.DuckChatConstants.DUCK_AI_FEATURE_PAGE
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionUpgrade
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class ReasoningModePickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var nativeInputStateProvider: NativeInputStateProvider

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ReasoningModePickerViewModel::class.java]
    }

    private val button: ImageView by lazy { findViewById(R.id.reasoningModePickerButton) }
    private var stateJob: Job? = null
    private var inputContextJob: Job? = null
    private var commandJob: Job? = null
    private var popupWindow: PopupWindow? = null

    // Mirrors the input context from the per-tab native input state so currentSurface() can be
    // read synchronously from popup callbacks. Updated by observeInputContext().
    private var lastInputContext: InputContext = InputContext.BROWSER
    private var lastNativeInputState: NativeInputState? = null

    init {
        inflate(context, R.layout.view_reasoning_mode_picker, this)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        button.setOnClickListener { showMenu() }
        observeState()
        observeInputContext()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stateJob?.cancel()
        stateJob = null
        inputContextJob?.cancel()
        inputContextJob = null
        commandJob?.cancel()
        commandJob = null
        lastNativeInputState = null
        dismissPopup()
    }

    private fun observeInputContext() {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        inputContextJob?.cancel()
        inputContextJob = nativeInputStateProvider.state
            .onEach { state ->
                lastInputContext = state.inputContext
                lastNativeInputState = state
                applyVisibility(viewModel.state.value.visible)
            }
            .launchIn(scope)
    }

    private fun applyVisibility(pickerVisible: Boolean) {
        val nativeState = lastNativeInputState
        val show = pickerVisible && nativeState?.shouldShowPluginControls() == true
        isVisible = show
        (parent as? View)?.isVisible = show
        // Popup is positioned by screen coordinates, not parented to the button. Dismiss
        // it explicitly when picker hides or stale rows stay tappable for the new chat.
        if (!show) dismissPopup()
    }

    private fun observeState() {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        stateJob?.cancel()
        stateJob = viewModel.state
            .onEach { state ->
                applyVisibility(state.visible)
                state.displayedMode?.let { mode ->
                    button.setImageResource(viewModel.iconResFor(mode))
                }
            }
            .launchIn(scope)

        commandJob?.cancel()
        commandJob = viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(scope)
    }

    private fun processCommand(command: UpsellCommand) {
        when (command) {
            is UpsellCommand.LaunchPurchase ->
                globalActivityStarter.start(context, SubscriptionPurchase(origin = command.origin, featurePage = DUCK_AI_FEATURE_PAGE))
            is UpsellCommand.LaunchUpgrade ->
                globalActivityStarter.start(context, SubscriptionUpgrade(origin = command.origin))
        }
    }

    private fun currentSurface(): PickerSurface =
        when (lastInputContext) {
            InputContext.DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL -> PickerSurface.REASONING_PICKER_DUCK_AI_TAB
            InputContext.BROWSER -> PickerSurface.REASONING_PICKER_ADDRESS_BAR
        }

    private fun showMenu() {
        val state = viewModel.state.value
        if (!state.visible) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.popup_menu_bg)
        }
        val popup = PopupWindow(
            ScrollView(context).apply {
                addView(container)
                isVerticalScrollBarEnabled = false
            },
            resources.getDimensionPixelSize(R.dimen.reasoningModePickerMenuWidth),
            LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            elevation = resources.getDimension(R.dimen.modelPickerMenuElevation)
            isOutsideTouchable = true
            setOnDismissListener { popupWindow = null }
        }
        populate(container, popup, state)
        popupWindow = popup
        showAtPosition(popup)
    }

    private fun populate(container: LinearLayout, popup: PopupWindow, state: ReasoningModePickerState) {
        state.rows.forEach { row ->
            val item = LayoutInflater.from(context)
                .inflate(R.layout.view_reasoning_mode_picker_item, container, false)
            item.findViewById<ImageView>(R.id.reasoningModeItemLeadingIcon)
                .setImageDrawable(AppCompatResources.getDrawable(context, row.iconRes))
            item.findViewById<DaxTextView>(R.id.reasoningModeItemTitle).setText(row.titleRes)
            item.findViewById<DaxTextView>(R.id.reasoningModeItemSubtitle).setText(row.subtitleRes)
            val trailingIcon = item.findViewById<ImageView>(R.id.reasoningModeItemTrailingIcon)
            trailingIcon.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_check_24)
            trailingIcon.visibility = if (row.selected) VISIBLE else INVISIBLE
            item.setOnClickListener {
                viewModel.onModeTapped(row.mode, currentSurface())
                popup.dismiss()
            }
            container.addView(item)
        }
    }

    private fun showAtPosition(popup: PopupWindow) {
        val loc = IntArray(2).also { button.getLocationOnScreen(it) }
        val menuWidth = resources.getDimensionPixelSize(R.dimen.reasoningModePickerMenuWidth)
        popup.showAtLocation(rootView, Gravity.TOP or Gravity.START, loc[0] + button.width - menuWidth, loc[1])
    }

    private fun dismissPopup() {
        popupWindow?.let {
            it.setOnDismissListener(null)
            if (it.isShowing) it.dismiss()
        }
        popupWindow = null
    }
}
