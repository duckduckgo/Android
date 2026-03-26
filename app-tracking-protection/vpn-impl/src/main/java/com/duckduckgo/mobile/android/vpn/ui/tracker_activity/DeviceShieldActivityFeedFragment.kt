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
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.recyclerviewext.StickyHeadersLinearLayoutManager
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity.Companion.AppsFilter
import com.duckduckgo.mobile.android.vpn.databinding.ViewDeviceShieldActivityFeedBinding
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedViewModel.Command
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedViewModel.Command.ShowProtectedAppsList
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedViewModel.Command.ShowUnprotectedAppsList
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedViewModel.Command.TrackerListDisplayed
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedViewModel.TrackerFeedViewState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class DeviceShieldActivityFeedFragment : DuckDuckGoFragment() {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var trackerFeedAdapter: TrackerFeedAdapter

    private val viewModel: DeviceShieldActivityFeedViewModel by bindViewModel()
    private lateinit var binding: ViewDeviceShieldActivityFeedBinding

    private var config: ActivityFeedConfig = defaultConfig

    private var feedListener: DeviceShieldActivityFeedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ViewDeviceShieldActivityFeedBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        with(binding.activityRecyclerView) {
            layoutManager = StickyHeadersLinearLayoutManager<TrackerFeedAdapter>(this@DeviceShieldActivityFeedFragment.requireContext())
            adapter = trackerFeedAdapter
        }

        lifecycleScope.launch {
            viewModel.getMostRecentTrackers(
                TimeWindow(
                    config.timeWindow.toLong(),
                    config.timeWindowUnits,
                ),
                config,
            ).flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect { viewState ->
                    renderViewState(viewState)
                }
        }

        lifecycleScope.launch {
            viewModel.commands()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { processCommands(it) }
        }
    }

    private suspend fun renderViewState(viewState: TrackerFeedViewState) {
        renderTrackerList(viewState)

        viewModel.trackerListDisplayed(viewState)
    }

    private suspend fun renderTrackerList(viewState: TrackerFeedViewState) {
        trackerFeedAdapter.updateData(
            if (config.unboundedRows()) viewState.trackers else viewState.trackers.take(config.maxRows),
        ) { trackerFeedItem ->
            when (trackerFeedItem) {
                is TrackerFeedItem.TrackerFeedData -> {
                    if (trackerFeedItem.isAppInstalled()) {
                        startActivity(
                            AppTPCompanyTrackersActivity.intent(
                                requireContext(),
                                trackerFeedItem.trackingApp.packageId,
                                trackerFeedItem.trackingApp.appDisplayName,
                                trackerFeedItem.bucket,
                            ),
                        )
                    } else {
                        Snackbar.make(
                            requireView(),
                            getString(R.string.atp_CompanyDetailsNotAvailableForUninstalledApps),
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    }
                }
                is TrackerFeedItem.TrackerTrackerAppsProtection -> {
                    viewModel.showAppsList(viewState.vpnState, trackerFeedItem)
                }
                else -> {} // no-op
            }
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is ShowProtectedAppsList -> showProtectedAppsList(command)
            is ShowUnprotectedAppsList -> showUnprotectedAppsList(command)
            is TrackerListDisplayed -> onTrackerListDisplayed(command)
        }
    }

    private fun showProtectedAppsList(command: ShowProtectedAppsList) {
        startActivity(
            TrackingProtectionExclusionListActivity.intent(
                requireContext(),
                AppsFilter.PROTECTED_ONLY,
            ),
        )
    }

    private fun showUnprotectedAppsList(command: ShowUnprotectedAppsList) {
        startActivity(
            TrackingProtectionExclusionListActivity.intent(
                requireContext(),
                AppsFilter.UNPROTECTED_ONLY,
            ),
        )
    }

    private fun onTrackerListDisplayed(command: TrackerListDisplayed) {
        feedListener?.onTrackerListShowed(command.trackersListSize)
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

    private fun TrackerFeedItem.TrackerFeedData.isAppInstalled(): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(trackingApp.packageId, 0)
            true
        } catch (e: NameNotFoundException) {
            false
        }
    }

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    companion object {
        private val defaultConfig = ActivityFeedConfig(
            maxRows = Int.MAX_VALUE,
            timeWindow = 7,
            timeWindowUnits = TimeUnit.DAYS,
            showTimeWindowHeadings = true,
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
        val showTimeWindowHeadings: Boolean,
    ) {
        fun unboundedRows(): Boolean = maxRows == Int.MAX_VALUE
    }

    interface DeviceShieldActivityFeedListener {
        fun onTrackerListShowed(totalTrackers: Int)
    }
}
