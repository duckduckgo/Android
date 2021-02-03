/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText
import com.duckduckgo.app.global.view.showKeyboard
import timber.log.Timber

/**
 * Variant of EditText which detects when the user has dismissed the soft keyboard
 *
 * Register as a listener using the `onBackKeyListener` property.
 */
class KeyboardAwareEditText : AppCompatEditText {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            // This is triggering multiple keyboard shows, which is unnecessary
            // showKeyboard()
        }
    }

    var onBackKeyListener: OnBackKeyListener? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            return onBackKeyListener?.onBackKey() ?: false
        }

        return super.onKeyPreIme(keyCode, event)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (isFocused) {
            // This is triggering multiple keyboard shows, which is unnecessary
            // showKeyboard()
        }
    }

    interface OnBackKeyListener {

        fun onBackKey(): Boolean
    }
}
