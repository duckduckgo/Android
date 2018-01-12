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

package com.duckduckgo.app.settings

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.about.AboutDuckDuckGoActivity.Companion.RESULT_CODE_LOAD_ABOUT_DDG_WEB_PAGE
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.launchExternalActivity
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.android.synthetic.main.content_settings.*
import javax.inject.Inject

class SettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: SettingsViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(SettingsViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupActionBar()

        configureUiEventHandlers()
        observeViewModel()
        viewModel.start()
    }

    private fun configureUiEventHandlers() {
        about.setOnClickListener { startActivityForResult(AboutDuckDuckGoActivity.intent(this), REQUEST_CODE_ABOUT_DDG) }
        provideFeedback.setOnClickListener { viewModel.userRequestedToSendFeedback() }
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this, Observer<SettingsViewModel.ViewState> { viewState ->
            viewState?.let {
                version.text = it.version
            }
        })

        viewModel.command.observe(this, Observer {
            when(it) {
                is SettingsViewModel.Command.SendEmail -> provideEmailFeedback(it.emailUri)
            }
        })
    }

    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == REQUEST_CODE_ABOUT_DDG && resultCode == RESULT_CODE_LOAD_ABOUT_DDG_WEB_PAGE) {
            setResult(resultCode)
            finish()
        }
    }

    private fun provideEmailFeedback(emailUri: Uri) {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = emailUri
        launchExternalActivity(intent)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }

        private const val REQUEST_CODE_ABOUT_DDG = 100
    }
}
