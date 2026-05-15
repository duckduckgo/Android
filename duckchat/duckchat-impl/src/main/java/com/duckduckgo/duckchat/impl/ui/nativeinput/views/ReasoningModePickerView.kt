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
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.ModelState
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

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ReasoningModePickerViewModel::class.java]
    }

    private val button: ImageView by lazy { findViewById(R.id.reasoningModePickerButton) }
    private var stateJob: Job? = null
    private var popupWindow: PopupWindow? = null

    init {
        inflate(context, R.layout.view_reasoning_mode_picker, this)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        button.setOnClickListener { showMenu() }
        observeState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stateJob?.cancel()
        stateJob = null
        dismissPopup()
    }

    private fun observeState() {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        stateJob?.cancel()
        stateJob = viewModel.state
            .onEach { state ->
                isVisible = state.availableReasoningModes.size > 1
                viewModel.resolvedMode(state)?.let { mode ->
                    button.setImageResource(viewModel.iconResFor(mode))
                }
            }
            .launchIn(scope)
    }

    private fun showMenu() {
        val state = viewModel.state.value
        if (state.availableReasoningModes.size <= 1) return

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

    private fun populate(container: LinearLayout, popup: PopupWindow, state: ModelState) {
        viewModel.rows(state).forEach { row ->
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
                viewModel.selectMode(row.mode)
                popup.dismiss()
            }
            container.addView(item)
        }
    }

    private fun showAtPosition(popup: PopupWindow) {
        val loc = IntArray(2).also { button.getLocationOnScreen(it) }
        popup.showAtLocation(rootView, Gravity.TOP or Gravity.START, loc[0], loc[1])
    }

    private fun dismissPopup() {
        popupWindow?.let {
            it.setOnDismissListener(null)
            if (it.isShowing) it.dismiss()
        }
        popupWindow = null
    }
}
