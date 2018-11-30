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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.renderer.TrackersRenderer
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.tabId
import kotlinx.android.synthetic.main.content_tracker_networks.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class TrackerNetworksActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository
    private val trackersRenderer = TrackersRenderer()
    private val networksAdapter = TrackerNetworksAdapter()

    private val viewModel: TrackerNetworksViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker_networks)
        configureToolbar()
        configureRecycler()

        viewModel.viewState.observe(this, Observer<TrackerNetworksViewModel.ViewState> {
            it?.let { render(it) }
        })

        repository.retrieveSiteData(intent.tabId!!).observe(this, Observer<Site> {
            viewModel.onSiteChanged(it)
        })
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun configureRecycler() {
        networksList.layoutManager = LinearLayoutManager(this)
        networksList.adapter = networksAdapter
    }

    private fun render(viewState: TrackerNetworksViewModel.ViewState) {
        networksBanner.setImageResource(trackersRenderer.networksBanner(viewState.allTrackersBlocked))
        domain.text = viewState.domain
        heading.text = trackersRenderer.trackersText(this, viewState.trackerCount, viewState.allTrackersBlocked)
        networksAdapter.updateData(viewState.trackingEventsByNetwork)
    }

    companion object {
        fun intent(context: Context, tabId: String): Intent {
            val intent = Intent(context, TrackerNetworksActivity::class.java)
            intent.tabId = tabId
            return intent
        }
    }
}