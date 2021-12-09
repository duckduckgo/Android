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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.duckduckgo.mobile.android.vpn.R
import kotlinx.android.synthetic.main.view_device_shield_past_week_activity_content.view.*

class PastWeekTrackerActivityContentView : FrameLayout {

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.PastWeekTrackerActivityContentView)
        text = attributes.getString(R.styleable.PastWeekTrackerActivityContentView_android_text) ?: ""
        count = attributes.getString(R.styleable.PastWeekTrackerActivityContentView_count) ?: ""
        footer = attributes.getString(R.styleable.PastWeekTrackerActivityContentView_footer) ?: ""
        attributes.recycle()
    }

    private val root: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.view_device_shield_past_week_activity_content, this, true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        root.isVisible = true
    }

    var count: String
        get() { return root.content_text.text.toString() }
        set(value) { root.content_text.text = value }

    var text: String
        get() { return root.content_title.text.toString() }
        set(value) { root.content_title.text = value }

    var footer: String
        get() { return root.content_footer.text.toString() }
        set(value) { root.content_footer.text = value }
}
