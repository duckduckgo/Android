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

package com.duckduckgo.duckchat.impl.inputscreen.ui.tabattachments

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.duckchat.impl.R

class TabAttachmentPopup(
    context: Context,
    private val useTopBar: Boolean,
    lifecycleOwner: LifecycleOwner,
    faviconManager: FaviconManager,
    private val onTabSelected: (TabAttachmentItem) -> Unit,
) {
    private val adapter = TabAttachmentAdapter(lifecycleOwner, faviconManager) { item ->
        onTabSelected(item)
    }

    private val recyclerView: RecyclerView

    private val popupWindow: PopupWindow

    init {
        @SuppressLint("InflateParams")
        val view = LayoutInflater.from(context).inflate(R.layout.popup_tab_attachment, null)
        recyclerView = view.findViewById(R.id.tabAttachmentRecyclerView)
        recyclerView.adapter = adapter

        popupWindow = PopupWindow(
            view,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            isFocusable = false
            isOutsideTouchable = false
            elevation = 8f
        }
    }

    fun update(items: List<TabAttachmentItem>, anchor: View) {
        adapter.submitList(items.take(MAX_VISIBLE_ITEMS)) {
            showPopup(anchor)
        }
    }

    private fun showPopup(anchor: View) {
        if (useTopBar) {
            if (popupWindow.isShowing) {
                popupWindow.update()
            } else {
                popupWindow.showAsDropDown(anchor)
            }
        } else {
            showAboveAnchor(anchor)
        }
    }

    private fun showAboveAnchor(anchor: View) {
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(anchor.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val popupHeight = recyclerView.measuredHeight
        if (popupHeight == 0) return

        val anchorLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        val anchorTop = anchorLocation[1]

        if (popupWindow.isShowing) {
            popupWindow.update(0, anchorTop - popupHeight, ViewGroup.LayoutParams.MATCH_PARENT, popupHeight)
        } else {
            popupWindow.showAtLocation(
                anchor,
                Gravity.NO_GRAVITY,
                0,
                anchorTop - popupHeight,
            )
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    companion object {
        private const val MAX_VISIBLE_ITEMS = 5
    }
}
