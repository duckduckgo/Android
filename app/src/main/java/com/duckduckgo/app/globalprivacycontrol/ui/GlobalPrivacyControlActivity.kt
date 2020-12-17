/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.globalprivacycontrol.ui

import android.content.Context
import android.content.Intent
import android.widget.CompoundButton.OnCheckedChangeListener
import android.os.Bundle
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.view.quietlySetIsChecked
import kotlinx.android.synthetic.main.content_global_privacy_control.*
import kotlinx.android.synthetic.main.include_toolbar.*

class GlobalPrivacyControlActivity : DuckDuckGoActivity() {

    private val viewModel: GlobalPrivacyControlViewModel by bindViewModel()

    private val globalPrivacyControlToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onUserToggleGlobalPrivacyControl(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_privacy_control)
        setupToolbar(toolbar)
        configureUiEventHandlers()
        setupUi()
        observeViewModel()
    }

    private fun setupUi() {
        val description = getString(R.string.globalPrivacyControlDescription)
        globalPrivacyControlDescription.text = description.html(this)
    }

    private fun configureUiEventHandlers() {
        globalPrivacyControlToggle.setOnCheckedChangeListener(globalPrivacyControlToggleListener)
        globalPrivacyControlLink.setOnClickListener { viewModel.onLearnMoreSelected() }
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            Observer { viewState ->
                viewState?.let {
                    globalPrivacyControlToggle.quietlySetIsChecked(it.globalPrivacyControlEnabled, globalPrivacyControlToggleListener)
                }
            }
        )

        viewModel.command.observe(
            this,
            Observer { command ->
                command?.let {
                    when (it) {
                        is GlobalPrivacyControlViewModel.Command.OpenLearnMore -> openLearnMoreSite(it.url)
                    }
                }
            }
        )
    }

    private fun openLearnMoreSite(url: String) {
        startActivity(BrowserActivity.intent(this, url))
        finish()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, GlobalPrivacyControlActivity::class.java)
        }
    }
}
