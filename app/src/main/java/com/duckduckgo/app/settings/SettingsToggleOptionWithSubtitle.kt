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

package com.duckduckgo.app.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.FrameLayout
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.SettingsToggleOptionWithSubtitleBinding
import com.duckduckgo.app.global.view.childrenRecursiveSequence
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class SettingsToggleOptionWithSubtitle : FrameLayout {

    private val binding: SettingsToggleOptionWithSubtitleBinding by viewBinding()

    constructor(context: Context) : this(context, null)
    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : this(context, attrs, R.style.SettingsItem)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int
    ) : super(context, attrs, defStyle) {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsToggleOptionWithSubtitle)
        title = attributes.getString(R.styleable.SettingsToggleOptionWithSubtitle_toggleTitle) ?: ""
        subtitle = attributes.getString(R.styleable.SettingsToggleOptionWithSubtitle_toggleSubTitle) ?: ""
        isChecked = attributes.getBoolean(R.styleable.SettingsToggleOptionWithSubtitle_isChecked, false)
        attributes.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setOnClickListener {
            binding.toggle.performClick()
        }
    }

    var title: String
        get() {
            return binding.title.text.toString()
        }
        set(value) {
            binding.title.text = value
        }

    var subtitle: String
        get() {
            return binding.subtitle.text.toString()
        }
        set(value) {
            binding.subtitle.text = value
        }

    var isChecked: Boolean
        get() {
            return binding.toggle.isChecked
        }
        set(value) {
            binding.toggle.isChecked = value
        }

    override fun setEnabled(enabled: Boolean) {
        binding.root.childrenRecursiveSequence().forEach { it.isEnabled = enabled }
        super.setEnabled(enabled)
    }

    fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
        binding.toggle.setOnCheckedChangeListener(listener)
    }

    fun quietlySetIsChecked(
        newCheckedState: Boolean,
        changeListener: CompoundButton.OnCheckedChangeListener?
    ) {
        binding.toggle.quietlySetIsChecked(newCheckedState, changeListener)
    }
}
