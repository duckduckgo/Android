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
import androidx.lifecycle.*
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.recyclerviewext.StickyHeadersLinearLayoutManager
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity
import com.duckduckgo.mobile.android.vpn.apps.ui.TrackingProtectionExclusionListActivity.Companion.AppsFilter
import com.duckduckgo.mobile.android.vpn.databinding.ViewDeviceShieldActivityFeedBinding
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.TimeWindow
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldActivityFeedViewModel.TrackerFeedViewState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackerFeedItem.TrackerLoadingSkeleton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class DeviceShieldActivityFeedFragment : DuckDuckGoFragment() {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var trackerFeedAdapter: TrackerFeedAdapter

    private val activityFeedViewModel: DeviceShieldActivityFeedViewModel by bindViewModel()
    private lateinit var binding: ViewDeviceShieldActivityFeedBinding

    private var config: ActivityFeedConfig = defaultConfig

    private var feedListener: DeviceShieldActivityFeedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ViewDeviceShieldActivityFeedBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        with(binding.activityRecyclerView) {
            val stickyHeadersLayoutManager =
                StickyHeadersLinearLayoutManager<TrackerFeedAdapter>(this@DeviceShieldActivityFeedFragment.requireContext())
            layoutManager = stickyHeadersLayoutManager
            adapter = trackerFeedAdapter
        }

        lifecycleScope.launch {
            activityFeedViewModel.getMostRecentTrackers(
                TimeWindow(
                    config.timeWindow.toLong(),
                    config.timeWindowUnits
                ),
                config.showTimeWindowHeadings
            ).flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect { viewState ->
                    renderViewState(viewState)
                }
        }
    }

    private suspend fun renderViewState(viewState: TrackerFeedViewState) {
        renderTrackerList(viewState)
        renderAppsData(viewState)

        if (viewState.trackers.isNotEmpty()) {
            if (viewState.trackers.first() != TrackerLoadingSkeleton) {
                feedListener?.onTrackerListShowed(viewState.trackers.size)
            }
        }
    }

    private suspend fun renderTrackerList(viewState: TrackerFeedViewState) {
        trackerFeedAdapter.updateData(
            if (config.unboundedRows()) viewState.trackers else viewState.trackers.take(config.maxRows)
        ) { trackerFeedData ->
            if (trackerFeedData.isAppInstalled()) {
                startActivity(
                    AppTPCompanyTrackersActivity.intent(
                        requireContext(),
                        trackerFeedData.trackingApp.packageId,
                        trackerFeedData.trackingApp.appDisplayName,
                        trackerFeedData.bucket
                    )
                )
            } else {
                Snackbar.make(
                    requireView(),
                    getString(R.string.atp_CompanyDetailsNotAvailableForUninstalledApps),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderAppsData(viewState: TrackerFeedViewState) {
        if (viewState.vpnState.state != VpnRunningState.ENABLED) {
            binding.appsStateContainer.gone()
            return
        }

        if (config.unboundedRows() || viewState.trackers.size >= config.maxRows) {
            binding.appsStateContainer.gone()
            return
        }

        if (viewState.appsProtectionData == null) {
            binding.appsStateContainer.gone()
            return
        }

        if (viewState.appsProtectionData.protectedAppsData.appsCount > 0) {
            binding.protectedAppsState.bind(viewState.appsProtectionData.protectedAppsData) { appsFilter ->
                launchExclusionlist(viewState.vpnState, appsFilter)
            }
            binding.protectedAppsState.show()
        } else {
            binding.protectedAppsState.gone()
        }
        if (viewState.appsProtectionData.unprotectedAppsData.appsCount > 0) {
            binding.unProtectedAppsState.bind(viewState.appsProtectionData.unprotectedAppsData) { appsFilter ->
                launchExclusionlist(viewState.vpnState, appsFilter)
            }
            binding.unProtectedAppsState.show()
        } else {
            binding.unProtectedAppsState.gone()
        }
        binding.appsStateContainer.show()
    }

    private fun launchExclusionlist(
        vpnState: VpnState,
        filter: AppsFilter
    ) {
        startActivity(
            TrackingProtectionExclusionListActivity.intent(
                requireContext(),
                vpnState.state == VpnRunningState.ENABLED,
                filter
            )
        )
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
