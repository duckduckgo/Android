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
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.AppUrl.Url
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.home.HomeActivity
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.renderer.banner
import com.duckduckgo.app.privacymonitor.renderer.text
import com.duckduckgo.app.tabs.TabDataRepository
import com.duckduckgo.app.tabs.tabId
import kotlinx.android.synthetic.main.content_privacy_practices.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class PrivacyPracticesActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var repository: TabDataRepository

    private val practicesAdapter = PrivacyPracticesAdapter()

    private val viewModel: PrivacyPracticesViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(PrivacyPracticesViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_practices)
        configureToolbar()
        configureRecycler()

        viewModel.viewState.observe(this, Observer<PrivacyPracticesViewModel.ViewState> {
            it?.let { render(it) }
        })

        repository.retrieve(intent.tabId!!).observe(this, Observer<PrivacyMonitor> {
            viewModel.onPrivacyMonitorChanged(it)
        })
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun configureRecycler() {
        practicesList.layoutManager = LinearLayoutManager(this)
        practicesList.adapter = practicesAdapter
    }

    private fun render(viewState: PrivacyPracticesViewModel.ViewState) {
        practicesBanner.setImageResource(viewState.practices.banner())
        domain.text = viewState.domain
        heading.text = viewState.practices.text(applicationContext)
        practicesAdapter.updateData(viewState.goodTerms, viewState.badTerms)
    }

    fun onTosdrLinkClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(HomeActivity.intent(this, Url.TOSDR))
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
