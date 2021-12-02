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

package com.duckduckgo.mobile.android.vpn.tv

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityTvTrackersActivityBinding
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedFragment
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivityViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TvTrackerDetailsActivity : DuckDuckGoActivity() {

    private val binding: ActivityTvTrackersActivityBinding by viewBinding()
    private val viewModel: DeviceShieldTrackerActivityViewModel by bindViewModel()

    private val feedConfig =
        DeviceShieldActivityFeedFragment.ActivityFeedConfig(
            maxRows = 20,
            timeWindow = 5,
            timeWindowUnits = TimeUnit.DAYS,
            showTimeWindowHeadings = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        showDeviceShieldActivity()
        observeViewModel()
    }

    private fun showDeviceShieldActivity() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.activity_list, DeviceShieldActivityFeedFragment.newInstance(feedConfig))
            .commitNow()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel
                .getBlockedTrackersCount()
                .combine(viewModel.getTrackingAppsCount()) { trackers, apps ->
                    DeviceShieldTrackerActivityViewModel.TrackerCountInfo(trackers, apps)
                }
                .combine(viewModel.vpnRunningState) { trackerCountInfo, runningState ->
                    DeviceShieldTrackerActivityViewModel.TrackerActivityViewState(
                        trackerCountInfo, runningState)
                }
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }
    }

    private fun renderViewState(
        state: DeviceShieldTrackerActivityViewModel.TrackerActivityViewState
    ) {
        binding.trackersBlockedCount.count = state.trackerCountInfo.stringTrackerCount()
        binding.trackersBlockedCount.footer =
            resources.getQuantityString(
                R.plurals.atp_ActivityPastWeekTrackerCount, state.trackerCountInfo.trackers.value)

        binding.trackingAppsCount.count = state.trackerCountInfo.stringAppsCount()
        binding.trackingAppsCount.footer =
            resources.getQuantityString(
                R.plurals.atp_ActivityPastWeekAppCount, state.trackerCountInfo.apps.value)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, TvTrackerDetailsActivity::class.java)
        }
    }
}
