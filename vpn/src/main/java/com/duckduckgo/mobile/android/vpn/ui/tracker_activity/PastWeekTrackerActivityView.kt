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
import kotlinx.android.synthetic.main.view_device_shield_past_week_activity.view.*
import kotlinx.android.synthetic.main.view_device_shield_past_week_activity_content.view.*

class PastWeekTrackerActivityView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val root: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.view_device_shield_past_week_activity, this, true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        root.isVisible = true
    }

    var trackingAppsCount: String
        get() { return root.tracking_apps_count.count }
        set(value) { root.tracking_apps_count.count = value }

    var trackersCount: String
        get() { return root.trackers_blocked_count.count }
        set(value) { root.trackers_blocked_count.count = value }
}
