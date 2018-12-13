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

package com.duckduckgo.app.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.duckduckgo.app.browser.R
import org.jetbrains.anko.childrenRecursiveSequence


class SettingsOptionWithSubtitle : ConstraintLayout {

    private var root: View
    private var titleView: TextView
    private var subtitleView: TextView

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.SettingsItem)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {

        root = LayoutInflater.from(context).inflate(R.layout.settings_option_with_subtitle, this, true)
        titleView = root.findViewById(R.id.title)
        subtitleView = root.findViewById(R.id.subtitle)

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.SettingsOptionWithSubtitle)
        setTitle(attributes.getString(R.styleable.SettingsOptionWithSubtitle_title) ?: "")
        setSubtitle(attributes.getString(R.styleable.SettingsOptionWithSubtitle_subtitle) ?: "")
        attributes.recycle()
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    fun setSubtitle(subtitle: String) {
        subtitleView.text = subtitle
    }

    override fun setEnabled(enabled: Boolean) {
        root.childrenRecursiveSequence().forEach { it.isEnabled = enabled }
        super.setEnabled(enabled)
    }


}