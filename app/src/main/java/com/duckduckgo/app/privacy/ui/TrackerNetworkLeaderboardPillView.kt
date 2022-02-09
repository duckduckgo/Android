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
import android.widget.FrameLayout
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewNetworkTrackerPillBinding
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class TrackerNetworkLeaderboardPillView : FrameLayout {

    private val binding: ViewNetworkTrackerPillBinding by viewBinding()

    val renderer = TrackersRenderer()

    constructor(context: Context) : super(context, null)

    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : super(context, attrs, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    fun render(
        networkEntity: NetworkLeaderboardEntry?,
        totalSitesVisited: Int
    ) {
        networkEntity ?: return
        binding.icon.setImageResource(renderer.networkPillIcon(context, networkEntity.networkName) ?: R.drawable.network_pill_generic)
        val percentText = renderer.networkPercentage(networkEntity, totalSitesVisited)
        binding.icon.contentDescription = "${networkEntity.networkName} $percentText"
        binding.percentage.text = percentText
    }
}
