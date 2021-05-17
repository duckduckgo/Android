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
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.mobile.android.vpn.R
import dagger.android.AndroidInjection
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class DeviceShieldTrackerActivity : AppCompatActivity(R.layout.activity_device_shield_activity) {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var pastWeekView: PastWeekTrackerActivityView
    private val pastWeekTrackerActivityViewModel: PastWeekTrackerActivityViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        pastWeekView = findViewById(R.id.past_week)

        lifecycleScope.launch {
            pastWeekTrackerActivityViewModel.getBlockedTrackersCount()
                .combine(pastWeekTrackerActivityViewModel.getTrackingAppsCount()) { trackers, apps ->
                    TrackerCountInfo(trackers, apps)
                }
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    pastWeekView.trackersCount = it.stringTrackerCount()
                    pastWeekView.trackingAppsCount = it.stringAppsCount()
                }
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldTrackerActivity::class.java)
        }
    }

    private data class TrackerCountInfo(val trackers: TrackerCount, val apps: TrackingAppCount) {
        fun stringTrackerCount(): String {
            return String.format(Locale.US, "%,d", trackers.value)
        }
        fun stringAppsCount(): String {
            return String.format(Locale.US, "%,d", apps.value)
        }
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }
}
