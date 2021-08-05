/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build.VERSION.SDK_INT
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import com.duckduckgo.app.statistics.Variant

class BrowserPopupMenu(layoutInflater: LayoutInflater, variant: Variant, view: View = inflate(layoutInflater, variant)) :
    PopupWindow(view, WRAP_CONTENT, WRAP_CONTENT, true) {

    // popupwindow gets stuck on the screen on API 22 (tested on 23) without a background
    // color.  Adding it however garbles the elevation so we cannot have elevation here.

    init {
        if (SDK_INT <= 22) {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
        } else {
            elevation = 6.toFloat()
        }
        animationStyle = android.R.style.Animation_Dialog
    }

    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun show(rootView: View, anchorView: View, onDismiss: () -> Unit) {
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val x = margin
        val y = anchorLocation[1] + margin
        showAtLocation(rootView, Gravity.TOP or Gravity.END, x, y)
        setOnDismissListener(onDismiss)
    }

    companion object {

        private const val margin = 16

        fun inflate(layoutInflater: LayoutInflater, variant: Variant): View {
            return layoutInflater.inflate(R.layout.popup_window_browser_menu, null)
        }
    }
}
