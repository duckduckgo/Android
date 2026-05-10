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

package com.duckduckgo.app.browser.nativeinput

import android.widget.ImageView

/**
 * Sets the Duck.ai entry icon according to whether the native input field setting is enabled.
 * Native enabled → chevron-down variant with a leading inset; otherwise the standard chat icon
 * with no leading inset.
 */
fun ImageView.applyDuckAiIconStyling(isNativeInputEnabled: Boolean) {
    if (isNativeInputEnabled) {
        setImageResource(com.duckduckgo.duckchat.impl.R.drawable.ic_ai_chat_down_24)
        setPaddingRelative(
            resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_2),
            paddingTop,
            0,
            paddingBottom,
        )
    } else {
        setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_ai_chat_24)
        setPaddingRelative(0, paddingTop, 0, paddingBottom)
    }
}
