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

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(ViewScope::class)
@SuppressLint("ViewConstructor")
class OptionsView(context: Context, private val host: NativeInputHost) : LinearLayout(context) {

    @Inject lateinit var viewModelFactory: ViewViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[OptionsViewModel::class.java]
    }

    private data class MenuItem(
        val iconRes: Int,
        val titleRes: Int,
        val subtitleRes: Int,
        val tool: Tool,
    )

    private val menuItems = listOf(
        MenuItem(
            iconRes = R.drawable.ic_images_24,
            titleRes = R.string.duckChatOptionsMenuCreateImage,
            subtitleRes = R.string.duckChatOptionsMenuCreateImageSubtitle,
            tool = Tool.IMAGE_GENERATION,
        ),
        MenuItem(
            iconRes = com.duckduckgo.mobile.android.R.drawable.ic_globe_24,
            titleRes = R.string.duckChatOptionsMenuWebSearch,
            subtitleRes = R.string.duckChatOptionsMenuWebSearchSubtitle,
            tool = Tool.WEB_SEARCH,
        ),
    )

    private var popupWindow: PopupWindow? = null
    private var optionsButton: ImageView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        optionsButton = buildOptionsButton()
        addView(optionsButton)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
    }

    fun getSelectedTool(): Tool? {
        if (!isAttachedToWindow) return null
        return viewModel.selectedTool.value
    }

    fun clearSelection() {
        if (!isAttachedToWindow) return
        viewModel.clearTool()
        removeChip()
        applyPickerVisibility()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isAttachedToWindow) return
        if (visibility == VISIBLE && !viewModel.shouldShowPickers) {
            post {
                host.showModelPicker(false)
                host.showReasoningPicker(false)
            }
        }
    }

    fun updateCapabilitiesFrom(picker: ModelPicker?) {
        val visibleTools = buildSet {
            if (picker?.isImageGenerationSupported() ?: true) add(Tool.IMAGE_GENERATION)
            if (picker?.isWebSearchSupported() ?: true) add(Tool.WEB_SEARCH)
        }

        if (isAttachedToWindow) {
            val selectionCleared = viewModel.updateVisibleTools(visibleTools)
            if (selectionCleared) {
                removeChip()
                applyPickerVisibility()
            }
        }

        optionsButton.isVisible = visibleTools.isNotEmpty()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissPopup()
    }

    private fun buildOptionsButton(): ImageView {
        val iconSize = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIcon)
        return ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.selectable_item_rounded_corner_background)
            contentDescription = context.getString(R.string.duckChatOptionsButtonContentDescription)
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_options_24)
            setOnClickListener { showMenu() }
        }
    }

    private fun showMenu() {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.popup_menu_bg)
        }
        val popup = PopupWindow(
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
            setOnDismissListener { popupWindow = null }
        }
        populate(container, popup)
        popupWindow = popup
        showAtPosition(popup)
    }

    private fun populate(container: LinearLayout, popup: PopupWindow) {
        val selectedTool = viewModel.selectedTool.value
        val trailingIcons = mutableMapOf<Tool, ImageView>()
        for (item in menuItems.filter { it.tool in viewModel.visibleTools.value }) {
            val row = LayoutInflater.from(context).inflate(R.layout.view_options_menu_item, container, false)
            val trailingIcon = row.findViewById<ImageView>(R.id.optionsMenuItemTrailingIcon)

            trailingIcons[item.tool] = trailingIcon
            row.findViewById<ImageView>(R.id.optionsMenuItemIcon).setImageResource(item.iconRes)
            row.findViewById<DaxTextView>(R.id.optionsMenuItemTitle).setText(item.titleRes)
            row.findViewById<DaxTextView>(R.id.optionsMenuItemSubtitle).setText(item.subtitleRes)
            trailingIcon.visibility = if (item.tool == selectedTool) VISIBLE else GONE

            row.setOnClickListener {
                val nowSelected = item.tool != viewModel.selectedTool.value
                trailingIcons.values.forEach { it.visibility = GONE }
                if (nowSelected) trailingIcon.visibility = VISIBLE
                onOptionTapped(item)
                row.postDelayed({ popup.dismiss() }, MENU_DISMISS_DELAY_MS)
            }
            container.addView(row)
        }
    }

    private fun onOptionTapped(item: MenuItem) {
        val hadChip = viewModel.selectedTool.value != null
        viewModel.toggleTool(item.tool)
        if (hadChip) removeChip()
        if (viewModel.selectedTool.value != null) addView(buildChip(item), 1)
        applyPickerVisibility()
    }

    private fun applyPickerVisibility() {
        val show = viewModel.shouldShowPickers
        host.showModelPicker(show)
        host.showReasoningPicker(show)
    }

    private fun removeChip() {
        if (childCount > 1) removeViewAt(1)
    }

    private fun buildChip(item: MenuItem): View {
        val view = LayoutInflater.from(context).inflate(R.layout.view_options_chip, this, false)
        view.findViewById<ImageView>(R.id.optionsChipIcon).setImageResource(item.iconRes)
        view.contentDescription = context.getString(R.string.duckChatOptionsChipDismissContentDescription, context.getString(item.titleRes))
        view.setOnClickListener {
            viewModel.clearTool()
            removeView(view)
            applyPickerVisibility()
        }
        return view
    }

    private fun showAtPosition(popup: PopupWindow) {
        val button = getChildAt(0) ?: this
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

    companion object {
        private const val MENU_DISMISS_DELAY_MS = 150L
    }
}
