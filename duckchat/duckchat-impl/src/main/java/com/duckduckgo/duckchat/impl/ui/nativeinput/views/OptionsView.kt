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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.duckchat.impl.R

@SuppressLint("ViewConstructor")
class OptionsView(context: Context) : FrameLayout(context) {

    private data class MenuItem(
        val iconRes: Int,
        val titleRes: Int,
        val subtitleRes: Int,
        val showTick: Boolean = false,
    )

    private val menuItems = listOf(
        MenuItem(
            iconRes = R.drawable.ic_images_24,
            titleRes = R.string.duckChatOptionsMenuCreateImage,
            subtitleRes = R.string.duckChatOptionsMenuCreateImageSubtitle,
            showTick = true,
        ),
        MenuItem(
            iconRes = com.duckduckgo.mobile.android.R.drawable.ic_globe_24,
            titleRes = R.string.duckChatOptionsMenuWebSearch,
            subtitleRes = R.string.duckChatOptionsMenuWebSearchSubtitle,
            showTick = true,
        ),
        MenuItem(
            iconRes = R.drawable.ic_glasses_24,
            titleRes = R.string.duckChatOptionsMenuCustomizeResponses,
            subtitleRes = R.string.duckChatOptionsMenuCustomizeResponsesSubtitle,
        ),
    )

    private val tappedIndices = mutableSetOf<Int>()
    private var popupWindow: PopupWindow? = null

    init {
        addView(buildOptionsButton())
        setOnClickListener { showMenu() }
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
        }
    }

    private fun showMenu() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
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
        val hMargin = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_5)
        menuItems.forEachIndexed { index, item ->
            val row = LayoutInflater.from(context).inflate(R.layout.view_options_menu_item, container, false)
            val trailingIcon = row.findViewById<ImageView>(R.id.optionsMenuItemTrailingIcon)
            row.findViewById<ImageView>(R.id.optionsMenuItemIcon).setImageResource(item.iconRes)
            row.findViewById<DaxTextView>(R.id.optionsMenuItemTitle).setText(item.titleRes)
            row.findViewById<DaxTextView>(R.id.optionsMenuItemSubtitle).setText(item.subtitleRes)
            trailingIcon.visibility = if (item.showTick && index in tappedIndices) VISIBLE else GONE
            row.setOnClickListener {
                if (item.showTick) {
                    if (tappedIndices.add(index)) {
                        trailingIcon.visibility = VISIBLE
                    } else {
                        tappedIndices.remove(index)
                        trailingIcon.visibility = GONE
                    }
                }
                row.postDelayed({ popup.dismiss() }, 150)
            }
            container.addView(row)
            if (index == 1) {
                val divider = LayoutInflater.from(context).inflate(R.layout.view_options_menu_divider, container, false)
                (divider.layoutParams as LinearLayout.LayoutParams).also {
                    it.marginStart = hMargin
                    it.marginEnd = hMargin
                }
                container.addView(divider)
            }
        }
    }

    private fun showAtPosition(popup: PopupWindow) {
        val loc = IntArray(2).also { getLocationOnScreen(it) }
        val x = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        val y = resources.displayMetrics.heightPixels - loc[1] +
            resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_1)
        popup.showAtLocation(rootView, Gravity.BOTTOM or Gravity.START, x, y)
    }

    private fun dismissPopup() {
        popupWindow?.let {
            it.setOnDismissListener(null)
            if (it.isShowing) it.dismiss()
        }
        popupWindow = null
    }
}
