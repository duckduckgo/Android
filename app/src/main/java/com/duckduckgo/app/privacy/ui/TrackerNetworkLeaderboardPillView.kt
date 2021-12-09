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

package com.duckduckgo.app.privacy.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import kotlinx.android.synthetic.main.view_network_tracker_pill.view.*

class TrackerNetworkLeaderboardPillView : FrameLayout {

    val renderer = TrackersRenderer()

    constructor(context: Context) : super(context, null) {
        initLayout()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0) {
        initLayout()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initLayout()
    }

    private fun initLayout() {
        View.inflate(context, R.layout.view_network_tracker_pill, this)
    }

    fun render(networkEntity: NetworkLeaderboardEntry?, totalSitesVisited: Int) {
        networkEntity ?: return
        icon.setImageResource(renderer.networkPillIcon(context, networkEntity.networkName) ?: R.drawable.network_pill_generic)
        val percentText = renderer.networkPercentage(networkEntity, totalSitesVisited)
        icon.contentDescription = "${networkEntity.networkName} $percentText"
        percentage.text = percentText
    }

}
