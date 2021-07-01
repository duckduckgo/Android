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

package com.duckduckgo.app.privacy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.databinding.ActivityPrivacyScorecardBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.privacy.renderer.*
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.tabId
import javax.inject.Inject

class ScorecardActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository

    private lateinit var binding: ActivityPrivacyScorecardBinding
    private val trackersRenderer = TrackersRenderer()
    private val upgradeRenderer = PrivacyUpgradeRenderer()

    private val viewModel: ScorecardViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val privacyScorecard
        get() = binding.contentPrivacyScorecard

    private val privacyScorecardHeader
        get() = privacyScorecard.privacyGrade

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyScorecardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(toolbar)

        viewModel.viewState.observe(
            this,
            Observer<ScorecardViewModel.ViewState> {
                it?.let { render(it) }
            }
        )

        repository.retrieveSiteData(intent.tabId!!).observe(
            this,
            Observer<Site> {
                viewModel.onSiteChanged(it)
            }
        )
    }

    private fun render(viewState: ScorecardViewModel.ViewState) {
        with(privacyScorecard) {
            val context = this@ScorecardActivity
            privacyScorecardHeader.privacyBanner.setImageResource(viewState.afterGrade.banner(viewState.privacyOn))
            privacyScorecardHeader.domain.text = viewState.domain
            privacyScorecardHeader.heading.text =
                upgradeRenderer.heading(context, viewState.beforeGrade, viewState.afterGrade, viewState.privacyOn).html(context)
            https.text = viewState.httpsStatus.text(context)
            https.setDrawableEnd(viewState.httpsStatus.successFailureIcon())
            practices.text = viewState.practices.text(context)
            practices.setDrawableEnd(viewState.practices.successFailureIcon())
            beforeGrade.setDrawableEnd(viewState.beforeGrade.smallIcon())
            afterGrade.setDrawableEnd(viewState.afterGrade.smallIcon())
            trackers.text = trackersRenderer.trackersText(context, viewState.trackerCount, viewState.allTrackersBlocked)
            trackers.setDrawableEnd(trackersRenderer.successFailureIcon(viewState.trackerCount))
            majorNetworks.text = trackersRenderer.majorNetworksText(context, viewState.majorNetworkCount, viewState.allTrackersBlocked)
            majorNetworks.setDrawableEnd(trackersRenderer.successFailureIcon(viewState.majorNetworkCount))
            showIsMemberOfMajorNetwork(viewState.showIsMemberOfMajorNetwork)
            showEnhancedGrade(viewState.showEnhancedGrade)
        }
    }

    private fun showIsMemberOfMajorNetwork(show: Boolean) {
        privacyScorecard.siteIsMajorNetworkMember.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEnhancedGrade(show: Boolean) {
        privacyScorecard.afterGrade.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun TextView.setDrawableEnd(@DrawableRes resource: Int) {
        if (resource == 0) {
            return
        }
        val drawable = getDrawable(resource)
        setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
    }

    companion object {

        fun intent(context: Context, tabId: String): Intent {
            val intent = Intent(context, ScorecardActivity::class.java)
            intent.tabId = tabId
            return intent
        }
    }
}
