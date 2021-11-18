/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.vpn.internal.R
import com.duckduckgo.vpn.internal.databinding.SettingsOptionWithLoadingStateBinding

class SettingsToggleOptionWithLoadingState : FrameLayout {

    private val binding: SettingsOptionWithLoadingStateBinding by viewBinding()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, com.duckduckgo.mobile.android.R.style.SettingsItem)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsToggleOptionWithLoadingState)
        setTitle(attributes.getString(R.styleable.SettingsToggleOptionWithLoadingState_text) ?: "")
        attributes.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        setIsLoading(false)
    }

    fun setTitle(title: String) {
        binding.title.text = title
    }

    fun setIsLoading(loading: Boolean) {
        binding.loadingState.isVisible = loading
    }
}
