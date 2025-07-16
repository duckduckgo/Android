/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.newtab

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.text.HtmlCompat
import androidx.lifecycle.*
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.FragmentDeviceShieldCtaBinding
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.TrackersBlocked
import com.duckduckgo.mobile.android.vpn.ui.privacyreport.PrivacyReportViewModel.PrivacyReportView.ViewState
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class AppTrackingProtectionStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    override fun getTag(): String {
        return "AppTP"
    }

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val viewModel: PrivacyReportViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PrivacyReportViewModel::class.java]
    }

    private val conflatedJob = ConflatedJob()

    private val binding: FragmentDeviceShieldCtaBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        conflatedJob += viewModel.viewStateFlow
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        deviceShieldPixels.didShowNewTabSummary()

        configureViewReferences()

        if (Build.VERSION.SDK_INT >= 28) {
            binding.root.addBottomShadow()
        }
    }

    override fun onDetachedFromWindow() {
        conflatedJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun configureViewReferences() {
        binding.deviceShieldCtaLayout.setOnClickListener {
            deviceShieldPixels.didPressNewTabSummary()
            context.startActivity(DeviceShieldTrackerActivity.intent(context))
        }
    }

    private fun renderViewState(viewState: ViewState) {
        if (viewState.isFeatureEnabled) {
            binding.root.show()
        } else {
            binding.root.gone()
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
            binding.deviceShieldCtaHeader.setText(R.string.atp_NewTabEnabled)
            binding.deviceShieldCtaImage.setImageResource(R.drawable.ic_apptp_default)
        }
    }

    private fun renderStateDisabled() {
        binding.deviceShieldCtaHeader.setText(R.string.atp_NewTabDisabled)
        binding.deviceShieldCtaImage.setImageResource(R.drawable.ic_apptp_warning)
    }

    private fun renderStateRevoked() {
        binding.deviceShieldCtaHeader.setText(R.string.atp_NewTabRevoked)
        binding.deviceShieldCtaImage.setImageResource(R.drawable.ic_apptp_warning)
    }

    private fun renderTrackersBlockedWhenEnabled(trackerBlocked: TrackersBlocked) {
        val trackersBlocked = trackerBlocked.trackers
        val lastTrackingApp = trackerBlocked.latestApp
        val otherApps = trackerBlocked.otherAppsSize

        val textToStyle = if (trackersBlocked == 1) {
            when (otherApps) {
                0 -> resources.getString(
                    R.string.atp_DailyLastCompanyBlockedHomeTabOneTimeZeroOtherApps,
                    trackersBlocked,
                    lastTrackingApp,
                )

                1 -> resources.getString(
                    R.string.atp_DailyLastCompanyBlockedHomeTabOneTimeOneOtherApp,
                    trackersBlocked,
                    lastTrackingApp,
                )

                else -> resources.getString(
                    R.string.atp_DailyLastCompanyBlockedHomeTabOneTimeMoreOtherApps,
                    trackersBlocked,
                    lastTrackingApp,
                    otherApps,
                )
            }
        } else {
            when (otherApps) {
                0 -> resources.getString(
                    R.string.atp_DailyLastCompanyBlockedHomeTabOtherTimesZeroOtherApps,
                    trackersBlocked,
                    lastTrackingApp,
                )

                1 -> resources.getString(
                    R.string.atp_DailyLastCompanyBlockedHomeTabOtherTimesOneOtherApp,
                    trackersBlocked,
                    lastTrackingApp,
                )

                else -> resources.getString(
                    R.string.atp_DailyLastCompanyBlockedHomeTabOtherTimesMoreOtherApps,
                    trackersBlocked,
                    lastTrackingApp,
                    otherApps,
                )
            }
        }

        binding.deviceShieldCtaHeader.text = HtmlCompat.fromHtml(textToStyle, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.deviceShieldCtaImage.setImageResource(R.drawable.ic_apptp_default)
    }
}

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageSectionPlugin::class,
    priority = NewTabPageSectionPlugin.PRIORITY_APP_TP,
)
class AppTrackingProtectionNewTabPageSectionPlugin @Inject constructor(
    private val vpnStore: VpnStore,
    private val vpnFeatureRemover: VpnFeatureRemover,
    private val setting: NewTabAppTrackingProtectionSectionSetting,
) : NewTabPageSectionPlugin {
    override val name = NewTabPageSection.APP_TRACKING_PROTECTION.name

    override fun getView(context: Context): View {
        return AppTrackingProtectionStateView(context)
    }

    override suspend fun isUserEnabled(): Boolean {
        if (vpnFeatureRemover.isFeatureRemoved()) {
            return false
        }

        return if (vpnStore.didShowOnboarding()) {
            setting.self().isEnabled()
        } else {
            false
        }
    }
}
