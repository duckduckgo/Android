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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import kotlinx.android.synthetic.main.activity_privacy_dashboard.*
import kotlinx.android.synthetic.main.content_privacy_practices.*
import javax.inject.Inject


class PrivacyPracticesActivity : DuckDuckGoActivity() {

    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var repository: PrivacyMonitorRepository

    companion object {

        fun intent(context: Context): Intent {
            return Intent(context, PrivacyPracticesActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_practices)
        configureToolbar()

        viewModel.viewState.observe(this, Observer<PrivacyPracticesViewModel.ViewState> {
            it?.let { render(it) }
        })

        repository.privacyMonitor.observe(this, Observer<PrivacyMonitor> {
            viewModel.onPrivacyMonitorChanged(it)
        })
    }

    private val viewModel: PrivacyPracticesViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(PrivacyPracticesViewModel::class.java)
    }

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun render(viewState: PrivacyPracticesViewModel.ViewState) {
        practicesBanner.setImageResource(viewState.practices.banner())
        domain.text = viewState.domain
        heading.text = viewState.practices.text(applicationContext)
    }
}
