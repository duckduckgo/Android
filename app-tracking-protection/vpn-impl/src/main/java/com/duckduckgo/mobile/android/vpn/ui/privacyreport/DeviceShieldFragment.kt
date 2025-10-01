/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.privacyreport

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
import androidx.core.text.HtmlCompat
import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.TrackersBlocked
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.ViewState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class DeviceShieldFragment : DuckDuckGoFragment() {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    private lateinit var deviceShieldCtaLayout: View
    private lateinit var deviceShieldCtaHeaderTextView: TextView
    private lateinit var deviceShieldCtaImageView: ImageView

    private inline fun <reified V : ViewModel> bindViewModel() = lazy { ViewModelProvider(this, viewModelFactory).get(V::class.java) }

    private val viewModel: PrivacyReportViewModel by bindViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
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
            deviceShieldPixels.didPressNewTabSummary()
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

    private fun renderViewState(viewState: ViewState) {
        if (viewState.isFeatureEnabled) {
            this.view?.show()
        } else {
            this.view?.gone()
        }
        when {
            viewState.vpnState.state == ENABLED -> renderStateEnabled(viewState)
            viewState.vpnState.stopReason == REVOKED -> renderStateRevoked()
            else -> renderStateDisabled()
        }
    }

    private fun renderStateEnabled(viewState: ViewState) {
        if (viewState.trackersBlocked.trackers > 0) {
            renderTrackersBlockedWhenEnabled(viewState.trackersBlocked)
        } else {
            deviceShieldCtaHeaderTextView.setText(R.string.atp_NewTabEnabled)
            deviceShieldCtaImageView.setImageResource(R.drawable.ic_apptp_default)
        }
    }

    private fun renderStateDisabled() {
        deviceShieldCtaHeaderTextView.setText(R.string.atp_NewTabDisabled)
        deviceShieldCtaImageView.setImageResource(R.drawable.ic_apptp_warning)
    }

    private fun renderStateRevoked() {
        deviceShieldCtaHeaderTextView.setText(R.string.atp_NewTabRevoked)
        deviceShieldCtaImageView.setImageResource(R.drawable.ic_apptp_warning)
    }

    private fun renderTrackersBlockedWhenEnabled(trackerBlocked: TrackersBlocked) {
        val trackersBlocked = trackerBlocked.trackers
        val lastTrackingApp = trackerBlocked.latestApp
        val otherApps = trackerBlocked.otherAppsSize

        val textToStyle =
            if (trackersBlocked == 1) {
                when (otherApps) {
                    0 ->
                        resources.getString(
                            R.string.atp_DailyLastCompanyBlockedHomeTabOneTimeZeroOtherApps,
                            trackersBlocked,
                            lastTrackingApp,
                        )
                    1 ->
                        resources.getString(
                            R.string.atp_DailyLastCompanyBlockedHomeTabOneTimeOneOtherApp,
                            trackersBlocked,
                            lastTrackingApp,
                        )
                    else ->
                        resources.getString(
                            R.string.atp_DailyLastCompanyBlockedHomeTabOneTimeMoreOtherApps,
                            trackersBlocked,
                            lastTrackingApp,
                            otherApps,
                        )
                }
            } else {
                when (otherApps) {
                    0 ->
                        resources.getString(
                            R.string.atp_DailyLastCompanyBlockedHomeTabOtherTimesZeroOtherApps,
                            trackersBlocked,
                            lastTrackingApp,
                        )
                    1 ->
                        resources.getString(
                            R.string.atp_DailyLastCompanyBlockedHomeTabOtherTimesOneOtherApp,
                            trackersBlocked,
                            lastTrackingApp,
                        )
                    else ->
                        resources.getString(
                            R.string.atp_DailyLastCompanyBlockedHomeTabOtherTimesMoreOtherApps,
                            trackersBlocked,
                            lastTrackingApp,
                            otherApps,
                        )
                }
            }

        deviceShieldCtaHeaderTextView.text = HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)
        deviceShieldCtaImageView.setImageResource(R.drawable.ic_apptp_default)
    }
}
