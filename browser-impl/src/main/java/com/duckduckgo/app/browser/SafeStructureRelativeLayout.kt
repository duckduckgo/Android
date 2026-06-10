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

import android.content.Context
import android.util.AttributeSet
import android.view.ViewStructure
import android.widget.RelativeLayout
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

class SafeStructureRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {

    override fun dispatchProvideStructure(structure: ViewStructure) {
        try {
            super.dispatchProvideStructure(structure)
        } catch (e: NullPointerException) {
            logcat(ERROR) { "SafeStructureRelativeLayout: Error dispatching structure: ${e.asLog()}" }
        }
    }
}
