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

package com.duckduckgo.duckchat.impl.ui.nativeinput

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.nativeinput.image.AttachmentHandler

@SuppressLint("ViewConstructor")
class AttachmentButtonView(
    context: Context,
    val attachmentHandler: AttachmentHandler,
) : FrameLayout(context) {

    init {
        val iconSize = context.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.toolbarIcon)
        val icon = ImageView(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            setBackgroundResource(com.duckduckgo.mobile.android.R.drawable.selectable_item_rounded_corner_background)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_attach_16)
        }
        addView(icon)
        setOnClickListener { attachmentHandler.showAttachmentChooser() }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1.0f else 0.4f
    }
}
