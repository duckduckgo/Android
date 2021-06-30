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

package com.duckduckgo.mobile.android.vpn.ui.report

import android.app.ActivityOptions
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.ui.notification.applyBoldSpanTo
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboarding
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class DeviceShieldFragment : Fragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var deviceShieldOnboarding: DeviceShieldOnboarding

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    private lateinit var deviceShieldCtaLayout: View
    private lateinit var deviceShieldCtaHeaderTextView: TextView
    private lateinit var deviceShieldCtaImageView: ImageView

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    private val viewModel: PrivacyReportViewModel by bindViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        AndroidSupportInjection.inject(this)
        val view = inflater.inflate(R.layout.fragment_device_shield_cta, container, false)

        configureViewReferences(view)
        observeViewModel()
        return view
    }

    private fun configureViewReferences(view: View) {
        deviceShieldCtaLayout = view.findViewById(R.id.deviceShieldCtaLayout)
        deviceShieldCtaHeaderTextView = view.findViewById(R.id.deviceShieldCtaHeader)
        deviceShieldCtaImageView = view.findViewById(R.id.deviceShieldCtaImage)
        deviceShieldCtaLayout.setOnClickListener {
            viewModel.onDeviceShieldEnabled()
            val options = ActivityOptions.makeSceneTransitionAnimation(requireActivity()).toBundle()
            startActivity(DeviceShieldTrackerActivity.intent(requireActivity()), options)
        }
    }

    override fun onResume() {
        super.onResume()
        // This logic is to ensure the we send the analytics exactly when the fragment is visible to the user
        // There are couple nuances/issues
        // 1. multiple tabs will have this fragment attached and resumed but only one will be shown to the user
        // 2. in BrowserFragment seems to call fragment_device_shield_container.show()/hide() several times causing
        //  this fragment to be resumed and visible for a split-second when Dax is shown (aka empty tab)
        Handler(Looper.getMainLooper()).postDelayed(200) {
            if (view?.isShown == true) {
                deviceShieldPixels.didShowNewTabSummary()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.viewStateFlow
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState ->
                renderViewState(viewState)
            }.launchIn(lifecycleScope)
    }

    private fun renderViewState(
        state: PrivacyReportViewModel.PrivacyReportView.ViewState
    ) {
        if (state.isRunning) {
            if (state.trackersBlocked.trackers > 0) {
                renderTrackersBlockedWhenEnabled(state.trackersBlocked)
            } else {
                deviceShieldCtaHeaderTextView.setText(R.string.deviceShieldNewTabEnabled)
                deviceShieldCtaImageView.setImageResource(R.drawable.ic_apptb_default)
            }
        } else {
            deviceShieldCtaHeaderTextView.setText(R.string.deviceShieldNewTabDisabled)
            deviceShieldCtaImageView.setImageResource(R.drawable.ic_apptb_warning)
        }
    }

    private fun renderTrackersBlockedWhenEnabled(trackerBlocked: PrivacyReportViewModel.PrivacyReportView.TrackersBlocked) {
        val latestAppString = resources.getString(R.string.deviceShieldDailyLastCompanyBlockedNotificationInApp, trackerBlocked.latestApp)
        val prefix = resources.getString(R.string.deviceShieldWeeklyCompanyTrackersBlockedNotificationPrefix)
        val totalTrackers = resources.getQuantityString(R.plurals.deviceShieldTrackers, trackerBlocked.trackers, trackerBlocked.trackers)
        val optionalOtherAppsString = if (trackerBlocked.otherAppsSize == 0) "" else resources.getQuantityString(
            R.plurals.deviceShieldDailyLastCompanyBlockedNotificationOptionalOtherApps, trackerBlocked.otherAppsSize, trackerBlocked.otherAppsSize
        )
        val pastWeekSuffix = resources.getString(R.string.deviceShieldNewTabSuffix)

        val textToStyle = "$prefix $totalTrackers $latestAppString$optionalOtherAppsString$pastWeekSuffix"

        deviceShieldCtaHeaderTextView.text = textToStyle.applyBoldSpanTo(
            listOf(
                totalTrackers,
                latestAppString,
                optionalOtherAppsString
            )
        )
        deviceShieldCtaImageView.setImageResource(R.drawable.ic_apptb_default)
    }
}
