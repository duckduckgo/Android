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
import android.view.View
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.databinding.ActivityPrivacyPracticesBinding
import com.duckduckgo.app.global.AppUrl.Url
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.renderer.banner
import com.duckduckgo.app.privacy.renderer.text
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.tabId
import javax.inject.Inject

class PrivacyPracticesActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: TabRepository

    private lateinit var binding: ActivityPrivacyPracticesBinding

    private val practicesAdapter = PrivacyPracticesAdapter()

    private val viewModel: PrivacyPracticesViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val privacyPractices
        get() = binding.contentPrivacyPractices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacyPracticesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(toolbar)
        configureRecycler()

        viewModel.viewState.observe(
            this,
            Observer<PrivacyPracticesViewModel.ViewState> {
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

    private fun configureRecycler() {
        privacyPractices.practicesList.layoutManager = LinearLayoutManager(this)
        privacyPractices.practicesList.adapter = practicesAdapter
    }

    private fun render(viewState: PrivacyPracticesViewModel.ViewState) {
        with(privacyPractices) {
            practicesBanner.setImageResource(viewState.practices.banner())
            domain.text = viewState.domain
            heading.text = viewState.practices.text(applicationContext)
            practicesAdapter.updateData(viewState.goodTerms, viewState.badTerms)
        }
    }

    fun onTosdrLinkClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(BrowserActivity.intent(this, Url.TOSDR))
        finish()
    }

    companion object {

        fun intent(context: Context, tabId: String): Intent {
            val intent = Intent(context, PrivacyPracticesActivity::class.java)
            intent.tabId = tabId
            return intent
        }

    }

}
