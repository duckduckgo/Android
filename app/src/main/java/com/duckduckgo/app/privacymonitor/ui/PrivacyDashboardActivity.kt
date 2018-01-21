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

package com.duckduckgo.app.privacymonitor.ui

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
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.renderer.*
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardViewModel.ViewState
import kotlinx.android.synthetic.main.content_privacy_dashboard.*
import kotlinx.android.synthetic.main.include_privacy_dashboard_header.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class PrivacyDashboardActivity : DuckDuckGoActivity() {

    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var repository: PrivacyMonitorRepository
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

        repository.privacyMonitor.observe(this, Observer<PrivacyMonitor> {
            viewModel.onPrivacyMonitorChanged(it)
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
        val backgroundColor = if (enabled) R.color.midGreen else R.color.warmerGrey
        privacyToggleContainer.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        privacyToggle.isChecked = enabled
    }

    fun onScorecardClicked() {
        startActivity(ScorecardActivity.intent(this))
    }

    fun onNetworksClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(TrackerNetworksActivity.intent(this))
    }


    fun onPracticesClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivityForResult(PrivacyPracticesActivity.intent(this), REQUEST_CODE_PRIVACY_PRACTICES)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_PRIVACY_PRACTICES && resultCode == PrivacyPracticesActivity.TOSDR_RESULT_CODE) {
            setResult(TOSDR_RESULT_CODE)
            finish()
        }
    }

    private fun updateActivityResult(shouldReload: Boolean) {
        if (shouldReload) {
            setResult(RELOAD_RESULT_CODE)
        } else {
            setResult(Activity.RESULT_OK)
        }
    }

    companion object {

        private const val REQUEST_CODE_PRIVACY_PRACTICES = 100

        const val RELOAD_RESULT_CODE = 100
        const val TOSDR_RESULT_CODE = 101

        fun intent(context: Context): Intent {
            return Intent(context, PrivacyDashboardActivity::class.java)
        }

    }

}
