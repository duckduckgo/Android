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

package com.duckduckgo.app.privacymonitor.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.duckduckgo.app.browser.R
import kotlinx.android.synthetic.main.view_network_tracker_pill.view.*

class NetworkTrackerPillView: FrameLayout {

    val renderer = NetworksRenderer()

    constructor(context: Context) : super(context, null) {
        initLayout()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0) {
        initLayout()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initLayout()
    }

    fun initLayout() {
        View.inflate(context, R.layout.view_network_tracker_pill, this)
    }

    fun render(networkName: String?, percent: Float?) {
        icon.setImageResource(renderer.networkPillIcon(context, networkName ?: "") ?: R.drawable.network_pill_generic)
        percentage.text = renderer.percentage(percent)
    }

}