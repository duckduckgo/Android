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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityTrackerNetworksBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.tabs.tabId
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.android.synthetic.main.content_tracker_networks.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class TrackerNetworksActivity : DuckDuckGoActivity() {

    private val binding: ActivityTrackerNetworksBinding by viewBinding()

    private val viewModel: TrackerNetworksViewModel by bindViewModel()

    private lateinit var networksAdapter: TrackerNetworksAdapter

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val trackerNetworks
        get() = binding.contentTrackerNetworks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureToolbar()
        configureRecycler()

        lifecycleScope.launch {
            viewModel.trackers(intent.tabId!!, intent.getBooleanExtra(EXTRA_DOMAINS_LOADED, false))
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { render(it) }
        }

        lifecycleScope.launch {
            viewModel.commands()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { processCommands(it) }
        }
    }

    private fun processCommands(command: TrackerNetworksViewModel.Command) {
        if (command is TrackerNetworksViewModel.Command.OpenLink) {
            openUrl(command.url)
        }
    }

    private fun openUrl(url: String) {
        startActivity(BrowserActivity.intent(this, url))
        finish()
    }

    private fun configureHeader(emptyView: Boolean, allTrackersBlocked: Boolean) {
        val domainsLoaded = intent.getBooleanExtra(EXTRA_DOMAINS_LOADED, true)
        val protectionsDisabled = intent.getBooleanExtra(EXTRA_PROTECTIONS_DISABLED, false)
        networksOverview.text = getString(
            when {
                !domainsLoaded && protectionsDisabled -> R.string.trackersOverviewProtectionsOff
                !domainsLoaded -> R.string.trackersOverview
                !allTrackersBlocked -> R.string.domainsLoadedOverviewProtectionsOff
                emptyView -> R.string.domainsLoadedOverviewEmpty
                else -> R.string.domainsLoadedOverview
            },
        )
        webTrackingProtections.addClickableLink("learn_more_link", getText(R.string.webTrackingProtectionsText)) {
            openUrl(getString(R.string.webTrackingProtectionsUrl))
        }
    }

    private fun configureToolbar() {
        toolbar.title = getString(
            if (intent.getBooleanExtra(EXTRA_DOMAINS_LOADED, true)) {
                R.string.domainsLoadedActivityTitle
            } else {
                R.string.trackersActivityTitle
            },
        )
        setupToolbar(toolbar)
    }

    private fun configureRecycler() {
        networksAdapter = TrackerNetworksAdapter(viewModel)
        with(trackerNetworks) {
            networksList.layoutManager = LinearLayoutManager(this@TrackerNetworksActivity)
            networksList.adapter = networksAdapter
        }
    }

    private fun render(viewState: TrackerNetworksViewModel.ViewState) {
        with(trackerNetworks) {
            if (viewState is TrackerNetworksViewModel.ViewState.TrackersViewState) {
                val networksIcon = intent.getIntExtra(EXTRA_TRACKING_REQUESTS_BANNER_ICON_ID, R.drawable.networks_icon_neutral)
                networksBanner.setImageResource(networksIcon)
                heading.gone()
            } else if (viewState is TrackerNetworksViewModel.ViewState.DomainsViewState) {
                if (viewState.eventsByNetwork.isEmpty()) {
                    networksBanner.setImageResource(R.drawable.ic_counter)
                    heading.show()
                } else {
                    networksBanner.setImageResource(R.drawable.ic_other_domains_banner)
                    heading.gone()
                }
            }
            configureHeader(viewState.eventsByNetwork.isEmpty(), viewState.allTrackersBlocked)
            domain.text = viewState.domain
            networksAdapter.updateData(viewState.eventsByNetwork)
        }
    }

    companion object {
        fun intent(
            context: Context,
            tabId: String,
            domainsLoaded: Boolean = false,
            trackingRequestsBannerIconId: Int? = null,
            protectionsDisabled: Boolean? = null,
        ): Intent {
            return Intent(context, TrackerNetworksActivity::class.java).apply {
                this.tabId = tabId
                putExtra(EXTRA_DOMAINS_LOADED, domainsLoaded)
                putExtra(EXTRA_TRACKING_REQUESTS_BANNER_ICON_ID, trackingRequestsBannerIconId)
                putExtra(EXTRA_PROTECTIONS_DISABLED, protectionsDisabled)
            }
        }

        private const val EXTRA_DOMAINS_LOADED = "extra_domains_loaded"
        private const val EXTRA_TRACKING_REQUESTS_BANNER_ICON_ID = "extra_tracking_requests_banner_icon_id"
        private const val EXTRA_PROTECTIONS_DISABLED = "extra_protections_disabled"
    }
}
