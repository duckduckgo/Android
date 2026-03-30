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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.PopupMenuItemView
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.ModelState
import com.google.android.material.chip.Chip
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

interface ModelPicker {
    fun isMenuVisible(): Boolean
    fun getSelectedModelId(): String?
    fun setPickerEnabled(enabled: Boolean)
}

@InjectWith(ViewScope::class)
class ModelPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), ModelPicker {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ModelPickerViewModel::class.java]
    }
    private val chip: Chip by lazy { findViewById(R.id.modelPickerChip) }
    private var stateJob: Job? = null
    private var popupWindow: PopupWindow? = null

    init {
        inflate(context, R.layout.view_model_picker, this)
    }

    override fun isMenuVisible(): Boolean = viewModel.menuShowing

    override fun getSelectedModelId(): String? = viewModel.getSelectedModelId()

    private var pickerEnabled = false

    override fun setPickerEnabled(enabled: Boolean) {
        this.pickerEnabled = enabled
        if (isAttachedToWindow) updateVisibility()
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
        stateJob = viewModel.state
            .onEach { state ->
                state.selectedModelShortName?.let { chip.text = it }
                updateVisibility()
            }
            .launchIn(scope)
    }

    private fun showMenu() {
        val state = viewModel.state.value
        if (state.models.isEmpty()) return

        viewModel.menuShowing = true

        val imm = context.getSystemService<InputMethodManager>()
        if (imm?.isAcceptingText == true) {
            imm.hideSoftInputFromWindow(windowToken, 0)
            postDelayed(KEYBOARD_DELAY) { showPopupWindow(state) }
        } else {
            showPopupWindow(state)
        }
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
            true,
        ).apply {
            elevation = resources.getDimension(R.dimen.modelPickerMenuElevation)
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stateJob?.cancel()
        stateJob = null
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
            if (selected) setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_check_24)
            if (model.isAccessible) {
                setOnClickListener {
                    viewModel.selectModel(model)
                    popup.dismiss()
                }
            } else {
                setDisabled()
            }
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(item)
    }

    private fun LinearLayout.addSectionHeader(title: String) {
        val header = LayoutInflater.from(context).inflate(R.layout.view_model_picker_section_header, this, false) as DaxTextView
        header.text = title
        addView(header)
    }

    private fun LinearLayout.addDivider() {
        addView(LayoutInflater.from(context).inflate(R.layout.view_model_picker_divider, this, false))
    }

    companion object {
        private const val KEYBOARD_DELAY = 250L
    }
}
