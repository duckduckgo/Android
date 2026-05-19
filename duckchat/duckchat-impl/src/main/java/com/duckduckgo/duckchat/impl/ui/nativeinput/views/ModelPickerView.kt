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
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.divider.HorizontalDivider
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.impl.DuckChatConstants.DUCK_AI_FEATURE_PAGE
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionUpgrade
import com.google.android.material.chip.Chip
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

interface ModelPicker {
    var onMenuShown: (() -> Unit)?
    var onMenuDismissed: (() -> Unit)?
    var onModelSelected: (() -> Unit)?
    fun getSelectedModelId(): String?
    fun isImageGenerationSupported(): Boolean
    fun isWebSearchSupported(): Boolean
    fun setPickerEnabled(enabled: Boolean)
    fun setHost(host: NativeInputHost)
}

@InjectWith(ViewScope::class)
class ModelPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), ModelPicker {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ModelPickerViewModel::class.java]
    }
    private val chip: Chip by lazy { findViewById(R.id.modelPickerChip) }
    private var stateJob: Job? = null
    private var commandJob: Job? = null
    private var popupWindow: PopupWindow? = null
    private var lastObservedModelId: String? = null
    private lateinit var host: NativeInputHost
    override var onMenuShown: (() -> Unit)? = null
    override var onMenuDismissed: (() -> Unit)? = null
    override var onModelSelected: (() -> Unit)? = null

    init {
        inflate(context, R.layout.view_model_picker, this)
    }

    override fun getSelectedModelId(): String? = viewModel.getSelectedModelId()

    override fun isImageGenerationSupported(): Boolean {
        if (!isAttachedToWindow) return true
        return viewModel.isImageGenerationSupported()
    }

    override fun isWebSearchSupported(): Boolean {
        if (!isAttachedToWindow) return true
        return viewModel.isWebSearchSupported()
    }

    private var pickerEnabled = false

    override fun setPickerEnabled(enabled: Boolean) {
        this.pickerEnabled = enabled
        if (isAttachedToWindow) updateVisibility()
    }

    override fun setHost(host: NativeInputHost) {
        this.host = host
    }

    private fun updateVisibility() {
        isVisible = pickerEnabled && viewModel.state.value.models.isNotEmpty()
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        chip.setOnClickListener { showMenu() }
        chip.setOnCloseIconClickListener { showMenu() }

        viewModel.fetchModels()
        observeState()
    }

    private fun observeState() {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        stateJob?.cancel()
        lastObservedModelId = viewModel.state.value.selectedModelId
        stateJob = viewModel.state
            .onEach { state ->
                state.selectedModelShortName?.let { chip.text = it }
                updateVisibility()
                val newId = state.selectedModelId
                if (newId != null && newId != lastObservedModelId) {
                    lastObservedModelId = newId
                    onModelSelected?.invoke()
                }
            }
            .launchIn(scope)

        commandJob?.cancel()
        commandJob = viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(scope)
    }

    private fun processCommand(command: ModelPickerViewModel.Command) {
        when (command) {
            is ModelPickerViewModel.Command.LaunchPurchase ->
                globalActivityStarter.start(context, SubscriptionPurchase(origin = command.origin, featurePage = DUCK_AI_FEATURE_PAGE))
            is ModelPickerViewModel.Command.LaunchUpgrade ->
                globalActivityStarter.start(context, SubscriptionUpgrade(origin = command.origin))
        }
    }

    private fun currentSurface(): PickerSurface =
        when (host.getInputState().inputContext) {
            InputContext.DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL -> PickerSurface.DUCK_AI_TAB
            InputContext.BROWSER -> PickerSurface.ADDRESS_BAR
        }

    private fun showMenu() {
        val state = viewModel.state.value
        if (state.models.isEmpty()) return

        viewModel.menuShowing = true
        onMenuShown?.invoke()
        showPopupWindow(state)
    }

    private fun showPopupWindow(state: ModelState) {
        val container = buildMenuContainer()
        val popup = createPopupWindow(container)

        container.populateMenu(state, popup)
        popupWindow = popup

        showAtPosition(popup)
    }

    private fun buildMenuContainer(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.popup_menu_bg)
        }
        return container
    }

    private fun createPopupWindow(container: LinearLayout): PopupWindow {
        return PopupWindow(
            ScrollView(context).apply {
                addView(container)
                isVerticalScrollBarEnabled = false
            },
            resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.popupMenuWidth),
            LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            elevation = resources.getDimension(R.dimen.modelPickerMenuElevation)
            isOutsideTouchable = true
            setOnDismissListener { onPopupDismissed() }
        }
    }

    private fun showAtPosition(popup: PopupWindow) {
        val loc = IntArray(2).also { chip.getLocationOnScreen(it) }
        val x = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        val y = resources.displayMetrics.heightPixels - loc[1] + resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_1)
        popup.showAtLocation(rootView, Gravity.BOTTOM or Gravity.END, x, y)
    }

    private fun onPopupDismissed() {
        viewModel.menuShowing = false
        popupWindow = null
        onMenuDismissed?.invoke()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stateJob?.cancel()
        stateJob = null
        commandJob?.cancel()
        commandJob = null
        dismissPopup()
    }

    private fun dismissPopup() {
        popupWindow?.let {
            it.setOnDismissListener(null)
            if (it.isShowing) it.dismiss()
        }
        popupWindow = null
        viewModel.menuShowing = false
    }

    private fun LinearLayout.populateMenu(state: ModelState, popup: PopupWindow) {
        viewModel.buildSections(state).forEachIndexed { index, section ->
            if (index > 0) addDivider()
            section.headerRes?.let { addSectionHeader(context.getString(it)) }
            for (model in section.models) {
                addModelItem(model, selected = model.id == state.selectedModelId, popup)
            }
        }
    }

    private fun LinearLayout.addModelItem(model: AIChatModel, selected: Boolean, popup: PopupWindow) {
        val item = PopupMenuItemView(context).apply {
            setPrimaryText(model.displayName)
            setLeadingIcon(viewModel.getIconResForModel(model))
            if (selected) setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_check_24)
            configureTrailingIcon()
            setOnClickListener {
                viewModel.onModelTapped(model, currentSurface())
                popup.dismiss()
            }
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(item)
    }

    private fun PopupMenuItemView.setLeadingIcon(@DrawableRes iconRes: Int?) {
        val label = findViewById<DaxTextView>(com.duckduckgo.mobile.android.R.id.label) ?: return
        label.maxLines = 1
        label.isSingleLine = true
        label.ellipsize = TextUtils.TruncateAt.END
        label.setPaddingRelative(label.paddingStart, label.paddingTop, 0, label.paddingBottom)
        val drawable = iconRes?.let { AppCompatResources.getDrawable(context, it) }
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
        label.compoundDrawablePadding = if (drawable != null) {
            resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_2)
        } else {
            0
        }
    }

    private fun PopupMenuItemView.configureTrailingIcon() {
        val trailingIcon = findViewById<ImageView>(com.duckduckgo.mobile.android.R.id.trailingIcon) ?: return
        trailingIcon.updateLayoutParams<MarginLayoutParams> {
            marginEnd = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_3)
        }
    }

    private fun LinearLayout.addSectionHeader(title: String) {
        val header = LayoutInflater.from(context).inflate(R.layout.view_model_picker_section_header, this, false) as DaxTextView
        header.text = title
        addView(header)
    }

    private fun LinearLayout.addDivider() {
        addView(HorizontalDivider(context))
    }
}
