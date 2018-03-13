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
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.renderer.*
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.privacy.ui.PrivacyDashboardViewModel.ViewState
import com.duckduckgo.app.tabs.tabId
import kotlinx.android.synthetic.main.content_privacy_dashboard.*
import kotlinx.android.synthetic.main.include_privacy_dashboard_header.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class PrivacyDashboardActivity : DuckDuckGoActivity() {

    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var repository: TabRepository
    private val trackersRenderer = TrackersRenderer()
    private val upgradeRenderer = PrivacyUpgradeRenderer()

    private val viewModel: PrivacyDashboardViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(PrivacyDashboardViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_dashboard)
        configureToolbar()

        viewModel.viewState.observe(this, Observer<ViewState> {
            it?.let { render(it) }
        })

        repository.retrieveSiteData(intent.tabId!!).observe(this, Observer<Site> {
            viewModel.onSiteChanged(it)
        })

        privacyGrade.setOnClickListener {
            onScorecardClicked()
        }

        privacyToggle.setOnCheckedChangeListener { _, enabled ->
            viewModel.onPrivacyToggled(enabled)
        }
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun render(viewState: ViewState) {
        if (isFinishing) {
            return
        }
        privacyBanner.setImageResource(viewState.afterGrade.banner(viewState.toggleEnabled))
        domain.text = viewState.domain
        heading.text = upgradeRenderer.heading(this, viewState.beforeGrade, viewState.afterGrade, viewState.toggleEnabled).html(this)
        httpsIcon.setImageResource(viewState.httpsStatus.icon())
        httpsText.text = viewState.httpsStatus.text(this)
        networksIcon.setImageResource(trackersRenderer.networksIcon(viewState.allTrackersBlocked))
        networksText.text = trackersRenderer.networksText(this, viewState.networkCount, viewState.allTrackersBlocked)
        practicesIcon.setImageResource(viewState.practices.icon())
        practicesText.text = viewState.practices.text(this)
        renderToggle(viewState.toggleEnabled)
        networkTrackerSummaryNotReady.visibility = if (viewState.showNetworkTrackerSummary) View.GONE else View.VISIBLE
        networkTrackerSummaryHeader.visibility = if (viewState.showNetworkTrackerSummary) View.VISIBLE else View.GONE
        networkTrackerSummaryPill1.visibility = if (viewState.showNetworkTrackerSummary) View.VISIBLE else View.GONE
        networkTrackerSummaryPill2.visibility = if (viewState.showNetworkTrackerSummary) View.VISIBLE else View.GONE
        networkTrackerSummaryPill3.visibility = if (viewState.showNetworkTrackerSummary) View.VISIBLE else View.GONE
        networkTrackerSummaryPill1.render(viewState.networkTrackerSummaryName1, viewState.networkTrackerSummaryPercent1)
        networkTrackerSummaryPill2.render(viewState.networkTrackerSummaryName2, viewState.networkTrackerSummaryPercent2)
        networkTrackerSummaryPill3.render(viewState.networkTrackerSummaryName3, viewState.networkTrackerSummaryPercent3)
        updateActivityResult(viewState.shouldReloadPage)
    }

    private fun renderToggle(enabled: Boolean) {
        val backgroundColor = if (enabled) R.color.midGreen else R.color.warmerGray
        privacyToggleContainer.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        privacyToggle.isChecked = enabled
    }

    fun onScorecardClicked() {
        startActivity(ScorecardActivity.intent(this, intent.tabId!!))
    }

    fun onNetworksClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(TrackerNetworksActivity.intent(this, intent.tabId!!))
    }

    fun onPracticesClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(PrivacyPracticesActivity.intent(this, intent.tabId!!))
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
