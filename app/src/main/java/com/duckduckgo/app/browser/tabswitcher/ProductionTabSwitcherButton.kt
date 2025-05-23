/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.tabswitcher

import android.content.Context
import android.util.AttributeSet
import com.duckduckgo.app.browser.databinding.ViewTabSwitcherButtonBinding
import com.duckduckgo.common.ui.viewbinding.viewBinding

class ProductionTabSwitcherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TabSwitcherButton(context, attrs, defStyleAttr) {

    private val binding: ViewTabSwitcherButtonBinding by viewBinding()

    override var count = 0
        set(value) {
            field = value
            val text = if (count < 100) "$count" else "~"
            binding.tabCount.text = text
        }
}
