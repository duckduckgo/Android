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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.recyclerviewext.StickyHeadersLinearLayoutManager
import com.duckduckgo.mobile.android.vpn.R
import dagger.android.AndroidInjection
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class DeviceShieldActivityFeedFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var trackerFeedAdapter: TrackerFeedAdapter

    private val activityFeedViewModel: DeviceShieldActivityFeedViewModel by bindViewModel()

    private var config: ActivityFeedConfig = defaultConfig

    private var feedListener: DeviceShieldActivityFeedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.view_device_shield_activity_feed, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        AndroidInjection.inject(this)
        with(view.findViewById<RecyclerView>(R.id.activity_recycler_view)) {
            val stickyHeadersLayoutManager =
                StickyHeadersLinearLayoutManager<TrackerFeedAdapter>(this@DeviceShieldActivityFeedFragment.requireContext())
            layoutManager = stickyHeadersLayoutManager
            adapter = trackerFeedAdapter
        }

        lifecycleScope.launch {
            activityFeedViewModel.getMostRecentTrackers(
                DeviceShieldActivityFeedViewModel.TimeWindow(
                    config.timeWindow.toLong(),
                    config.timeWindowUnits
                ),
                config.showTimeWindowHeadings
            )
                .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect { it ->
                    feedListener?.onTrackerListShowed(it.size)
                    trackerFeedAdapter.updateData(if (config.unboundedRows()) it else it.take(config.maxRows)) { trackerFeedData ->
                        startActivity(
                            AppTPCompanyTrackersActivity.intent(
                                requireContext(),
                                trackerFeedData.trackingApp.packageId,
                                trackerFeedData.trackingApp.appDisplayName,
                                trackerFeedData.bucket
                            )
                        )
                    }
                }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is DeviceShieldActivityFeedListener) {
            feedListener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        feedListener = null
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    companion object {
        private val defaultConfig = ActivityFeedConfig(
            maxRows = Int.MAX_VALUE,
            timeWindow = 5,
            timeWindowUnits = TimeUnit.DAYS,
            showTimeWindowHeadings = true
        )

        fun newInstance(config: ActivityFeedConfig): DeviceShieldActivityFeedFragment {
            return DeviceShieldActivityFeedFragment().apply {
                this.config = config
            }
        }
    }

    data class ActivityFeedConfig(
        val maxRows: Int,
        val timeWindow: Int,
        val timeWindowUnits: TimeUnit,
        val showTimeWindowHeadings: Boolean
    ) {
        fun unboundedRows(): Boolean = maxRows == Int.MAX_VALUE
    }

    interface DeviceShieldActivityFeedListener {
        fun onTrackerListShowed(totalTrackers: Int)
    }
}
