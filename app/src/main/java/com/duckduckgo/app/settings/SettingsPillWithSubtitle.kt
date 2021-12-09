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

@file:Suppress("MemberVisibilityCanBePrivate")

package com.duckduckgo.app.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.ui.view.recursiveEnable
import com.duckduckgo.app.browser.databinding.SettingsPillWithSubtitleBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class SettingsPillWithSubtitle : LinearLayout {

    private val binding: SettingsPillWithSubtitleBinding by viewBinding()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.SettingsItem)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsPillWithSubtitle)
        setTitle(attributes.getString(R.styleable.SettingsPillWithSubtitle_pillTitle) ?: "")
        setSubtitle(attributes.getString(R.styleable.SettingsPillWithSubtitle_pillSubtitle) ?: "")
        setPill(attributes.getResourceId(R.styleable.SettingsPillWithSubtitle_pillDrawable, R.drawable.ic_beta_pill))
        attributes.recycle()
    }

    fun setTitle(title: String) {
        binding.title.text = title
    }

    fun setSubtitle(subtitle: String) {
        binding.subtitle.text = subtitle
    }

    fun setPill(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        binding.pill.setImageDrawable(drawable)
    }

    override fun setEnabled(enabled: Boolean) {
        recursiveEnable(enabled)
        super.setEnabled(enabled)
    }

}
