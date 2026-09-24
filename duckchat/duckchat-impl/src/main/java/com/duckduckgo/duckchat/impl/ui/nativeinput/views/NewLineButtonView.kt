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
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.duckchat.impl.R
import kotlin.math.roundToInt

/**
 * The floating new-line button shown above the keyboard in the top-bar layout. Deliberately not a
 * NativeInputPlugin (see the tech design): it lives in the externally-owned floating container, not
 * in one of the widget's own plugin containers, and its visibility stays widget-driven.
 */
class NewLineButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private val button: ImageView by lazy { findViewById(R.id.nativeInputNewLineButton) }

    var onNewLineClicked: (() -> Unit)? = null

    init {
        inflate(context, R.layout.view_native_input_new_line_button, this)
        button.setOnClickListener { onNewLineClicked?.invoke() }
        // Breathing room around the floating button, matching the old floating row; clipToPadding off
        // so the circular ripple is not clipped by the padding.
        val horizontal = 10f.toPx(context).roundToInt()
        val vertical = 16f.toPx(context).roundToInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        clipToPadding = false
        clipChildren = false
    }

    fun setNewLineVisible(visible: Boolean) {
        isVisible = visible
    }
}
