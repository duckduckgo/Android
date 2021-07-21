/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings.db

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.duckduckgo.app.browser.R

class SettingsPillWithSubtitle : LinearLayout {

    private var root: View
    private var titleView: TextView
    private var subtitleView: TextView
    private var pillView: ImageView

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.SettingsItem)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {

        root = LayoutInflater.from(context).inflate(R.layout.settings_pill_with_subtitle, this, true)
        titleView = root.findViewById(R.id.title)
        subtitleView = root.findViewById(R.id.subtitle)
        pillView = root.findViewById(R.id.pill)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsOptionWithPill)
        setTitle(attributes.getString(R.styleable.SettingsOptionWithPill_pillTitle) ?: "")
        setSubtitle(attributes.getString(R.styleable.SettingsOptionWithPill_pillSubtitle) ?: "")
        setPill(attributes.getResourceId(R.styleable.SettingsOptionWithPill_pillDrawable, R.drawable.ic_beta_pill))
        attributes.recycle()
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    fun setSubtitle(subtitle: String) {
        subtitleView.text = subtitle
    }

    fun setPill(idRes: Int) {
        val drawable = VectorDrawableCompat.create(resources, idRes, null)
        pillView.setImageDrawable(drawable)
    }

    override fun setEnabled(enabled: Boolean) {
        recursiveEnable(enabled)
        super.setEnabled(enabled)
    }

    fun View.recursiveEnable(enabled: Boolean) {
        (this as? ViewGroup)?.children?.forEach {
            it.isEnabled = enabled
            it.recursiveEnable(enabled)
        }
    }

}
