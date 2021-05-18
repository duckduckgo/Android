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
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.childrenRecursiveSequence
import com.duckduckgo.app.global.view.quietlySetIsChecked
import kotlinx.android.synthetic.main.settings_toggle_option_with_subtitle.view.*

class SettingsToggleOptionWithSubtitle : FrameLayout {

    private val root: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.settings_toggle_option_with_subtitle, this, true)
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.SettingsItem)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsToggleOptionWithSubtitle)
        title = attributes.getString(R.styleable.SettingsToggleOptionWithSubtitle_toggleTitle) ?: ""
        subtitle = attributes.getString(R.styleable.SettingsToggleOptionWithSubtitle_toggleSubTitle) ?: ""
        isChecked = attributes.getBoolean(R.styleable.SettingsToggleOptionWithSubtitle_isChecked, false)
        attributes.recycle()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setOnClickListener {
            root.toggle.performClick()
        }
    }

    var title: String
        get() { return root.title.text.toString() }
        set(value) { root.title.text = value }

    var subtitle: String
        get() { return root.subtitle.text.toString() }
        set(value) { root.subtitle.text = value }

    var isChecked: Boolean
        get() { return root.toggle.isChecked }
        set(value) { root.toggle.isChecked = value }

    override fun setEnabled(enabled: Boolean) {
        root.childrenRecursiveSequence().forEach { it.isEnabled = enabled }
        super.setEnabled(enabled)
    }

    fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener) {
        root.toggle.setOnCheckedChangeListener(listener)
    }

    fun quietlySetIsChecked(newCheckedState: Boolean, changeListener: CompoundButton.OnCheckedChangeListener?) {
        root.toggle.quietlySetIsChecked(newCheckedState, changeListener)
    }
}
