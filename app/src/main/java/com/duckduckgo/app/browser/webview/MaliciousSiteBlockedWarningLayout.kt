/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.webview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.app.browser.databinding.ViewMaliciousSiteBlockedWarningBinding
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding

class MaliciousSiteBlockedWarningLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    sealed class Action {
        data object VisitSite : Action()
        data object LeaveSite : Action()
    }

    private val binding: ViewMaliciousSiteBlockedWarningBinding by viewBinding()

    fun bind() {
        resetViewState()

        with(binding) {
            setListeners()
        }
    }

    private fun resetViewState() {
        with(binding) {
            advancedCTA.show()
            advancedGroup.gone()
        }
    }

    private fun setListeners() {
        with(binding) {
            advancedCTA.setOnClickListener {
                advancedCTA.gone()
                advancedGroup.show()
                errorLayout.post {
                    errorLayout.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }
}
