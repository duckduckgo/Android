/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.view.View
import android.widget.TextView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.renderer.*
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import kotlinx.android.synthetic.main.content_privacy_scorecard.*
import kotlinx.android.synthetic.main.include_privacy_dashboard_header.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class ScorecardActivity : DuckDuckGoActivity() {

    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var repository: PrivacyMonitorRepository
    private val trackersRenderer = TrackersRenderer()
    private val upgradeRenderer = PrivacyUpgradeRenderer()

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, ScorecardActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_scorecard)
        configureToolbar()

        viewModel.viewState.observe(this, Observer<ScorecardViewModel.ViewState> {
            it?.let { render(it) }
        })

        repository.privacyMonitor.observe(this, Observer<PrivacyMonitor> {
            viewModel.onPrivacyMonitorChanged(it)
        })
    }

    private val viewModel: ScorecardViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(ScorecardViewModel::class.java)
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun render(viewState: ScorecardViewModel.ViewState) {
        privacyBanner.setImageResource(viewState.afterGrade.banner(viewState.privacyOn))
        domain.text = viewState.domain
        heading.text = upgradeRenderer.heading(this, viewState.beforeGrade, viewState.afterGrade, viewState.privacyOn).html(this)
        https.text = viewState.httpsStatus.text(this)
        https.setDrawableEnd(viewState.httpsStatus.successFailureIcon())
        practices.text = viewState.practices.text(this)
        practices.setDrawableEnd(viewState.practices.successFailureIcon())
        beforeGrade.setDrawableEnd(viewState.beforeGrade.smallIcon())
        afterGrade.setDrawableEnd(viewState.afterGrade.smallIcon())
        trackers.text = trackersRenderer.trackersText(this, viewState.trackerCount, viewState.allTrackersBlocked)
        trackers.setDrawableEnd(trackersRenderer.successFailureIcon(viewState.trackerCount))
        majorNetworks.text = trackersRenderer.majorNetworksText(this, viewState.majorNetworkCount, viewState.allTrackersBlocked)
        majorNetworks.setDrawableEnd(trackersRenderer.successFailureIcon(viewState.majorNetworkCount))
        showIsMemberOfMajorNetwork(viewState.showIsMemberOfMajorNetwork)
        showEnhancedGrade(viewState.showEnhancedGrade)
    }

    private fun showIsMemberOfMajorNetwork(show: Boolean) {
        siteIsMajorNetworkMember.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEnhancedGrade(show: Boolean) {
        afterGrade.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun TextView.setDrawableEnd(@DrawableRes resource: Int) {
        if (resource == 0) {
            return
        }
        val drawable = getDrawable(resource)
        setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
    }

}