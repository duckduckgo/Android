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
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.SupportedTool
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputHost

@SuppressLint("ViewConstructor")
class OptionsView(context: Context, private val host: NativeInputHost) : LinearLayout(context) {

    private enum class Option { CREATE_IMAGE, WEB_SEARCH }

    private data class MenuItem(
        val iconRes: Int,
        val titleRes: Int,
        val subtitleRes: Int,
        val option: Option,
    )

    private val menuItems = listOf(
        MenuItem(
            iconRes = R.drawable.ic_images_24,
            titleRes = R.string.duckChatOptionsMenuCreateImage,
            subtitleRes = R.string.duckChatOptionsMenuCreateImageSubtitle,
            option = Option.CREATE_IMAGE,
        ),
        MenuItem(
            iconRes = com.duckduckgo.mobile.android.R.drawable.ic_globe_24,
            titleRes = R.string.duckChatOptionsMenuWebSearch,
            subtitleRes = R.string.duckChatOptionsMenuWebSearchSubtitle,
            option = Option.WEB_SEARCH,
        ),
    )

    private val tappedIndices = mutableSetOf<Int>()
    private var popupWindow: PopupWindow? = null
    private var visibleMenuItems = menuItems.withIndex().toList()
    private var optionsButton: ImageView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        optionsButton = buildOptionsButton()
        addView(optionsButton)
    }

    fun getSelectedTool(): SupportedTool? {
        val index = tappedIndices.firstOrNull() ?: return null
        return when (menuItems[index].option) {
            Option.CREATE_IMAGE -> SupportedTool.IMAGE_GENERATION
            Option.WEB_SEARCH -> SupportedTool.WEB_SEARCH
        }
    }

    fun updateCapabilitiesFrom(picker: ModelPicker?) {
        val supportsImageGeneration = picker?.isImageGenerationSupported() ?: true
        val supportsWebSearch = picker?.isWebSearchSupported() ?: true

        visibleMenuItems = menuItems.withIndex()
            .filter { (_, item) ->
                when (item.option) {
                    Option.CREATE_IMAGE -> supportsImageGeneration
                    Option.WEB_SEARCH -> supportsWebSearch
                }
            }
            .toList()

        menuItems.forEachIndexed { index, item ->
            val supported = when (item.option) {
                Option.CREATE_IMAGE -> supportsImageGeneration
                Option.WEB_SEARCH -> supportsWebSearch
            }
            if (!supported && index in tappedIndices) {
                tappedIndices.remove(index)
                removeChipForIndex(index)
                if (item.option == Option.CREATE_IMAGE) {
                    host.showModelPicker(true)
                    host.showReasoningPicker(true)
                }
            }
        }

        optionsButton.isVisible = visibleMenuItems.isNotEmpty()
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
        val trailingIcons = mutableMapOf<Int, ImageView>()
        for ((index, item) in visibleMenuItems) {
            val row = LayoutInflater.from(context).inflate(R.layout.view_options_menu_item, container, false)
            val trailingIcon = row.findViewById<ImageView>(R.id.optionsMenuItemTrailingIcon)
            trailingIcons[index] = trailingIcon
            row.findViewById<ImageView>(R.id.optionsMenuItemIcon).setImageResource(item.iconRes)
            row.findViewById<DaxTextView>(R.id.optionsMenuItemTitle).setText(item.titleRes)
            row.findViewById<DaxTextView>(R.id.optionsMenuItemSubtitle).setText(item.subtitleRes)
            trailingIcon.visibility = if (index in tappedIndices) VISIBLE else GONE
            row.setOnClickListener {
                val nowSelected = index !in tappedIndices
                trailingIcons.values.forEach { it.visibility = GONE }
                if (nowSelected) trailingIcon.visibility = VISIBLE
                toggleOption(index)
                row.postDelayed({ popup.dismiss() }, 150)
            }
            container.addView(row)
        }
    }

    private fun toggleOption(index: Int) {
        if (tappedIndices.contains(index)) {
            tappedIndices.remove(index)
            removeChipForIndex(index)
            host.showModelPicker(true)
            host.showReasoningPicker(true)
        } else {
            tappedIndices.firstOrNull()?.let { other ->
                tappedIndices.remove(other)
                removeChipForIndex(other)
            }
            tappedIndices.add(index)
            addView(buildChip(index, menuItems[index]), 1)
            val show = menuItems[index].option != Option.CREATE_IMAGE
            host.showModelPicker(show)
            host.showReasoningPicker(show)
        }
    }

    private fun removeChipForIndex(index: Int) {
        (0 until childCount).map { getChildAt(it) }.firstOrNull { it.tag == index }?.let { removeView(it) }
    }

    private fun buildChip(index: Int, item: MenuItem): View {
        val view = LayoutInflater.from(context).inflate(R.layout.view_options_chip, this, false)
        view.tag = index
        view.findViewById<ImageView>(R.id.optionsChipIcon).setImageResource(item.iconRes)
        view.contentDescription = context.getString(R.string.duckChatOptionsChipDismissContentDescription, context.getString(item.titleRes))
        view.setOnClickListener {
            tappedIndices.remove(index)
            removeView(view)
            host.showModelPicker(true)
            host.showReasoningPicker(true)
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
}
