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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.MenuItem
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.renderer.*
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardViewModel.ViewState
import kotlinx.android.synthetic.main.activity_privacy_dashboard.*
import kotlinx.android.synthetic.main.content_privacy_dashboard.*
import kotlinx.android.synthetic.main.include_privacy_dashboard_header.*
import javax.inject.Inject


class PrivacyDashboardActivity : DuckDuckGoActivity() {

    companion object {

        val REQUEST_DASHBOARD = 1000
        val RESULT_RELOAD = 1000
        val RESULT_TOSDR = 1001

        fun intent(context: Context): Intent {
            return Intent(context, PrivacyDashboardActivity::class.java)
        }
    }

    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var repository: PrivacyMonitorRepository
    private val trackersRenderer = TrackersRenderer()
    private val upgradeRenderer = PrivacyUpgradeRenderer()

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

    private val viewModel: PrivacyDashboardViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(PrivacyDashboardViewModel::class.java)
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (viewModel.shouldReloadPage) {
            setResult(RESULT_RELOAD)
        }
        super.onBackPressed()
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
    }

    private fun renderToggle(enabled: Boolean) {
        val backgroundColor = if (enabled) R.color.midGreen else R.color.warmerGrey
        privacyToggleContainer.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        privacyToggle.isChecked = enabled
    }

    fun onScorecardClicked() {
        startActivity(ScorecardActivity.intent(this))
    }

    fun onNetworksClicked(view: View) {
        startActivity(TrackerNetworksActivity.intent(this))
    }

    fun onPracticesClicked(view: View) {
        startActivityForResult(PrivacyPracticesActivity.intent(this), REQUEST_DASHBOARD)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_DASHBOARD && resultCode == RESULT_TOSDR) {
            setResult(RESULT_TOSDR)
            finish()
        }
    }
}
