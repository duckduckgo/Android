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

package com.duckduckgo.app.browser

import android.view.View
import android.view.WindowInsets
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

/**
 * Zeroes IME insets before they reach WebView so an adjustResize host layout and WebView M139+
 * IME visual-viewport resizing do not both shrink the page (double padding).
 *
 * @see <a href="https://developer.android.com/develop/ui/views/layout/webapps/understand-window-insets">
 * Android WebView window insets guidance</a>
 */
internal object WebViewImeInsets {
    fun strip(insets: WindowInsets, view: View): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, view)
        val stripped = strip(compat)
        return if (stripped === compat) insets else stripped.toWindowInsets() ?: insets
    }

    fun strip(compat: WindowInsetsCompat): WindowInsetsCompat {
        if (compat.getInsets(WindowInsetsCompat.Type.ime()) == Insets.NONE) return compat

        return WindowInsetsCompat.Builder(compat)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
            .build()
    }
}
