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
import com.duckduckgo.app.browser.databinding.ViewExperimentalTabSwitcherButtonBinding
import com.duckduckgo.app.browser.databinding.ViewNewToolbarTabSwitcherButtonBinding
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R as CommonR

class NewTabSwitcherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TabSwitcherButton(context, attrs, defStyleAttr) {

    private val binding: ViewNewToolbarTabSwitcherButtonBinding by viewBinding()

    override var hasUnread: Boolean = false
        set(value) {
            if (field != value) {
                if (value) {
                    binding.tabsImageView.setImageResource(CommonR.drawable.ic_tab_24_highlighted)
                } else {
                    binding.tabsImageView.setImageResource(CommonR.drawable.ic_tab_24)
                }
            }
            field = value
        }

    override var count = 0
        set(value) {
            if (field != value) {
                if (value < 100) {
                    binding.tabCount.text = "$value"
                    binding.tabCount.show()
                    binding.tabCountInfinite.gone()
                } else {
                    binding.tabCount.gone()
                    binding.tabCountInfinite.show()
                }
            }
            field = value
        }
}
