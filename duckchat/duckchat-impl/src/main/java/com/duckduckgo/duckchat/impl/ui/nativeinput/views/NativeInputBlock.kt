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
import android.graphics.Rect
import android.text.InputFilter
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.IdRes
import androidx.core.view.isVisible

/**
 * The native input composer lock, used to lock the native input in various states. Does not lock the toggle row.
 */
internal class NativeInputBlock(
    private val widget: ViewGroup,
    private val inputField: EditText,
    @IdRes private val dimmedRowIds: List<Int>,
    @IdRes private val toggleRowId: Int,
    private val dimAlpha: Float,
) {
    var isBlocked: Boolean = false
        private set

    private val typingFilter = InputFilter { _, _, _, _, _, _ ->
        widget.post { (widget.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(inputField) }
        ""
    }

    fun set(blocked: Boolean) {
        isBlocked = blocked
        val others = inputField.filters.filterNot { it === typingFilter }
        inputField.filters = (if (blocked) others + typingFilter else others).toTypedArray()
    }

    /** Allow the write operation even if the input is blocked */
    fun <T> runUnblocked(write: () -> T): T {
        val filters = inputField.filters
        inputField.filters = filters.filterNot { it === typingFilter }.toTypedArray()
        try {
            return write()
        } finally {
            inputField.filters = filters
        }
    }

    fun setRowsDimmed(dimmed: Boolean) {
        dimmedRowIds.forEach { id -> widget.findViewById<View?>(id)?.alpha = if (dimmed) dimAlpha else 1f }
    }

    /** True when the touch hits the toggle row, which stays usable under this lock. */
    fun allowsTouch(ev: MotionEvent): Boolean {
        val row = widget.findViewById<View?>(toggleRowId)?.takeIf { it.isVisible } ?: return false
        val bounds = Rect(0, 0, row.width, row.height)
        widget.offsetDescendantRectToMyCoords(row, bounds)
        return bounds.contains(ev.x.toInt(), ev.y.toInt())
    }
}
