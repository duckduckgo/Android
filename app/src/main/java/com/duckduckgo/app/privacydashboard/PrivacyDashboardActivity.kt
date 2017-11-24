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
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.privacymonitor.SiteMonitor
import kotlinx.android.synthetic.main.activity_privacy_dashboard.*
import kotlinx.android.synthetic.main.content_privacy_dashboard.*
import javax.inject.Inject


class PrivacyDashboardActivity : DuckDuckGoActivity() {

    @Inject lateinit var viewModelFactory: ViewModelFactory

    companion object {

        fun intent(context: Context, monitor: SiteMonitor): Intent {
            val intent = Intent(context, PrivacyDashboardActivity::class.java)
            intent.putExtra(SiteMonitor::class.java.name, monitor)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_dashboard)
        configureToolbar()

        if (savedInstanceState == null) {
            loadIntentData()
        }

        viewModel.liveSiteMonitor.observe(this, Observer<SiteMonitor> {
            it?.let { render(it) }
        })
    }

    private val viewModel: PrivacyDashboardViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(PrivacyDashboardViewModel::class.java)
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadIntentData() {
        val siteMonitor = intent.getSerializableExtra(SiteMonitor::class.java.name) as SiteMonitor?
        if (siteMonitor != null) {
            viewModel.attachSiteMonitor(siteMonitor)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                super.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun render(siteMonitor: SiteMonitor) {
        domain.text = Uri.parse(siteMonitor.url).host
        trackerNetworksText.text = getString(R.string.trackerNetworksBlocked, siteMonitor.trackerNetworkCount.toString())
    }
}
