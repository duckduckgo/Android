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

package com.duckduckgo.app.privacydashboard

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.privacydashboard.PrivacyDashboardViewModel.ViewState
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import kotlinx.android.synthetic.main.activity_privacy_dashboard.*
import kotlinx.android.synthetic.main.content_privacy_dashboard.*
import javax.inject.Inject


class PrivacyDashboardActivity : DuckDuckGoActivity() {

    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var repository: PrivacyMonitorRepository

    companion object {

        fun intent(context: Context): Intent {
            return Intent(context, PrivacyDashboardActivity::class.java)
        }
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
                super.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun render(viewState: ViewState) {
        domain.text = viewState.domain
        httpsText.text = viewState.httpsText
        httpsIcon.setImageDrawable(getDrawable(viewState.httpsIcon))
        networksIcon.setImageDrawable(getDrawable(viewState.networksIcon))
        networksText.text = viewState.networksText
        majorNetworksIcon.setImageDrawable(getDrawable(viewState.majorNetworksIcon))
        majorNetworksText.text = viewState.majorNetworksText
    }
}
