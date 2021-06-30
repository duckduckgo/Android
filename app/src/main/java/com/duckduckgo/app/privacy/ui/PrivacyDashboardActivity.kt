/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.duckduckgo.app.brokensite.BrokenSiteActivity
import com.duckduckgo.app.brokensite.BrokenSiteData
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.hide
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.view.show
import com.duckduckgo.app.privacy.renderer.*
import com.duckduckgo.app.privacy.ui.PrivacyDashboardViewModel.Command
import com.duckduckgo.app.privacy.ui.PrivacyDashboardViewModel.Command.LaunchManageWhitelist
import com.duckduckgo.app.privacy.ui.PrivacyDashboardViewModel.Command.LaunchReportBrokenSite
import com.duckduckgo.app.privacy.ui.PrivacyDashboardViewModel.ViewState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.tabId
import kotlinx.android.synthetic.main.content_privacy_dashboard.*
import kotlinx.android.synthetic.main.include_privacy_dashboard_header.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class PrivacyDashboardActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository

    @Inject
    lateinit var pixel: Pixel

    private val trackersRenderer = TrackersRenderer()
    private val upgradeRenderer = PrivacyUpgradeRenderer()

    private val viewModel: PrivacyDashboardViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_dashboard)
        setupToolbar(toolbar)
        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.viewState.observe(
            this,
            {
                it?.let { render(it) }
            }
        )
        viewModel.command.observe(
            this,
            {
                it?.let { processCommand(it) }
            }
        )
        repository.retrieveSiteData(intent.tabId!!).observe(
            this,
            {
                viewModel.onSiteChanged(it)
            }
        )
    }

    private fun setupClickListeners() {
        privacyGrade.setOnClickListener {
            onScorecardClicked()
        }

        whitelistButton.setOnClickListener {
            viewModel.onManageWhitelistSelected()
        }

        brokenSiteButton.setOnClickListener {
            viewModel.onReportBrokenSiteSelected()
        }

        privacyToggle.setOnCheckedChangeListener { _, enabled ->
            viewModel.onPrivacyToggled(enabled)
        }
    }

    private fun render(viewState: ViewState) {
        if (isFinishing) {
            return
        }
        val toggle = viewState.toggleEnabled ?: true
        val isSiteIntTempAllowedList = viewState.isSiteOnTempAllowedList
        val isPrivacyOn = toggle && !isSiteIntTempAllowedList
        privacyBanner.setImageResource(viewState.afterGrade.banner(toggle))
        domain.text = viewState.domain
        heading.text = upgradeRenderer.heading(this, viewState.beforeGrade, viewState.afterGrade, isPrivacyOn).html(this)
        httpsIcon.setImageResource(viewState.httpsStatus.icon())
        httpsText.text = viewState.httpsStatus.text(this)
        networksIcon.setImageResource(trackersRenderer.networksIcon(viewState.allTrackersBlocked))
        networksText.text = trackersRenderer.trackersText(this, viewState.trackerCount, viewState.allTrackersBlocked)
        practicesIcon.setImageResource(viewState.practices.icon())
        practicesText.text = viewState.practices.text(this)
        renderToggle(toggle, isSiteIntTempAllowedList)
        renderTrackerNetworkLeaderboard(viewState)
        updateActivityResult(viewState.shouldReloadPage)
    }

    private fun renderTrackerNetworkLeaderboard(viewState: ViewState) {

        if (!viewState.shouldShowTrackerNetworkLeaderboard) {
            hideTrackerNetworkLeaderboard()
            return
        }

        trackerNetworkPill1.render(viewState.trackerNetworkEntries.elementAtOrNull(0), viewState.sitesVisited)
        trackerNetworkPill2.render(viewState.trackerNetworkEntries.elementAtOrNull(1), viewState.sitesVisited)
        trackerNetworkPill3.render(viewState.trackerNetworkEntries.elementAtOrNull(2), viewState.sitesVisited)
        showTrackerNetworkLeaderboard()
    }

    private fun showTrackerNetworkLeaderboard() {
        trackerNetworkLeaderboardHeader.show()
        trackerNetworkPill1.show()
        trackerNetworkPill2.show()
        trackerNetworkPill3.show()
        trackerNetworkLeaderboardNotReady.hide()
    }

    private fun hideTrackerNetworkLeaderboard() {
        trackerNetworkLeaderboardHeader.hide()
        trackerNetworkPill1.hide()
        trackerNetworkPill2.hide()
        trackerNetworkPill3.hide()
        trackerNetworkLeaderboardNotReady.show()
    }

    private fun renderToggle(enabled: Boolean, isSiteIntTempAllowedList: Boolean) {
        val backgroundColor = if (enabled && !isSiteIntTempAllowedList) R.color.midGreen else R.color.warmerGray
        privacyToggleContainer.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        privacyToggle.isChecked = enabled
        if (isSiteIntTempAllowedList) {
            privacyToggle.gone()
            privacyToggleText.text = getString(R.string.privacyProtectionToggleDisabled)
        } else {
            privacyToggle.show()
            privacyToggleText.text = getString(R.string.privacyProtectionToggle)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is LaunchManageWhitelist -> launchWhitelistActivity()
            is LaunchReportBrokenSite -> launchReportBrokenSite(command.data)
        }
    }

    private fun onScorecardClicked() {
        pixel.fire(PRIVACY_DASHBOARD_SCORECARD)
        startActivity(ScorecardActivity.intent(this, intent.tabId!!))
    }

    private fun launchReportBrokenSite(data: BrokenSiteData) {
        startActivity(BrokenSiteActivity.intent(this, data))
    }

    private fun launchWhitelistActivity() {
        startActivity(WhitelistActivity.intent(this))
    }

    fun onEncryptionClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        pixel.fire(PRIVACY_DASHBOARD_ENCRYPTION)
    }

    fun onNetworksClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        pixel.fire(PRIVACY_DASHBOARD_NETWORKS)
        startActivity(TrackerNetworksActivity.intent(this, intent.tabId!!))
    }

    fun onPracticesClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        pixel.fire(PRIVACY_DASHBOARD_PRIVACY_PRACTICES)
        startActivity(PrivacyPracticesActivity.intent(this, intent.tabId!!))
    }

    fun onLeaderboardClick(@Suppress("UNUSED_PARAMETER") view: View) {
        pixel.fire(PRIVACY_DASHBOARD_GLOBAL_STATS)
    }

    private fun updateActivityResult(shouldReload: Boolean) {
        if (shouldReload) {
            setResult(RELOAD_RESULT_CODE)
        } else {
            setResult(Activity.RESULT_OK)
        }
    }

    companion object {

        const val RELOAD_RESULT_CODE = 100

        fun intent(context: Context, tabId: String): Intent {
            val intent = Intent(context, PrivacyDashboardActivity::class.java)
            intent.tabId = tabId
            return intent
        }

    }
}
